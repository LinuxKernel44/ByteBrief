package app.bytebrief.source.github;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.TrackedRepo;
import app.bytebrief.domain.BodyState;
import app.bytebrief.net.SourceException;
import app.bytebrief.testutil.Fixtures;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class GithubSourcesTest {
    private MockWebServer server;
    private final Map<String, MockResponse> routes = new HashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private String base;
    private GithubApi api;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requests.add(request);
                MockResponse r = routes.get(request.getPath());
                return r != null ? r : new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        base = server.url("/").toString().replaceAll("/$", "");
        api = new GithubApi(Fixtures.fetcher(), base, null);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private TrendingSource trending() {
        return new TrendingSource(Fixtures.fetcher(), api, Fixtures.factory(), base + "/trending", Fixtures.CLOCK);
    }

    @Test
    public void trendingFetchMapsRealFixtureToItems() throws Exception {
        routes.put("/trending?since=daily", new MockResponse().setBody(Fixtures.resource("fixtures/github_trending_daily.html")));
        List<ContentItem> items = trending().fetch(TrendingSource.Period.DAILY, null);
        assertEquals(12, items.size());
        ContentItem it = items.get(0);
        assertEquals("gh_trending", it.sourceType);
        assertEquals("morluto/rea", it.sourceId);
        assertEquals("morluto/rea", it.title);
        assertEquals("https://github.com/morluto/rea", it.canonicalUrl);
        assertEquals("u:github.com/morluto/rea", it.dedupKey);
        assertEquals(255_645, it.score);
        assertEquals(BodyState.SNIPPET, it.bodyState);
        JSONObject meta = new JSONObject(it.metadataJson);
        assertEquals(64_229, meta.getLong("forks"));
        assertEquals(41_034, meta.getLong("stars_in_period"));
        assertEquals("daily", meta.getString("period"));
    }

    @Test
    public void trendingLanguageFilterIsValidatedAndEncoded() throws Exception {
        routes.put("/trending/c%2B%2B?since=weekly", new MockResponse().setBody(Fixtures.resource("fixtures/github_trending_daily.html")));
        trending().fetch(TrendingSource.Period.WEEKLY, "C++");
        assertEquals("/trending/c%2B%2B?since=weekly", requests.get(0).getPath());
        try {
            trending().fetch(TrendingSource.Period.DAILY, "java/../../admin");
            fail("expected SourceException");
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.INVALID_URL, e.kind);
        }
    }

    @Test
    public void emptyOrChangedMarkupIsAnErrorNotSilentSuccess() {
        routes.put("/trending?since=daily", new MockResponse().setBody("<html>consent wall</html>"));
        try {
            trending().fetch(TrendingSource.Period.DAILY, null);
            fail("expected SourceException");
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.PARSE, e.kind);
        }
    }

    @Test
    public void enrichAddsTopicsLicenseAndReadme() throws Exception {
        routes.put("/trending?since=daily", new MockResponse().setBody(Fixtures.resource("fixtures/github_trending_daily.html")));
        routes.put("/repos/morluto/rea", new MockResponse().setBody("{\"description\":\"d\",\"topics\":[\"reverse-engineering\",\"agents\"],"
                + "\"license\":{\"spdx_id\":\"MIT\"},\"pushed_at\":\"2026-10-14T10:00:00Z\",\"stargazers_count\":5,\"forks_count\":1}"));
        routes.put("/repos/morluto/rea/readme", new MockResponse().setBody("# rea\nReverse engineering helper."));
        TrendingSource src = trending();
        List<ContentItem> items = src.fetch(TrendingSource.Period.DAILY, null);
        int n = src.enrich(items, 1, i -> true);
        assertEquals(1, n);
        ContentItem it = items.get(0);
        assertEquals("reverse-engineering,agents,typescript", it.topics);
        assertEquals(BodyState.FULL, it.bodyState);
        assertTrue(it.bodyText.contains("Reverse engineering helper"));
        JSONObject meta = new JSONObject(it.metadataJson);
        assertEquals("MIT", meta.getString("license"));
        assertEquals("2026-10-14T10:00:00Z", meta.getString("pushed_at"));
        assertFalse(meta.getBoolean("readme_truncated"));
        // Only one repo enriched because of the cap.
        assertEquals(BodyState.SNIPPET, items.get(1).bodyState);
    }

    @Test
    public void enrichStopsOnRateLimitWithoutFailing() throws Exception {
        routes.put("/trending?since=daily", new MockResponse().setBody(Fixtures.resource("fixtures/github_trending_daily.html")));
        routes.put("/repos/morluto/rea", new MockResponse().setResponseCode(403)
                .setHeader("x-ratelimit-remaining", "0").setHeader("x-ratelimit-reset", "1760000000"));
        TrendingSource src = trending();
        List<ContentItem> items = src.fetch(TrendingSource.Period.DAILY, null);
        assertEquals(0, src.enrich(items, 10, i -> true));
        assertEquals(1, requests.stream().filter(r -> r.getPath().startsWith("/repos/")).count());
    }

    @Test
    public void tokenIsSentOnlyWhenConfigured() throws Exception {
        routes.put("/repos/o/r", new MockResponse().setBody("{}"));
        api.fetchRepo("o", "r");
        assertNull(requests.get(0).getHeader("Authorization"));
        new GithubApi(Fixtures.fetcher(), base, () -> "secret").fetchRepo("o", "r");
        assertEquals("Bearer secret", requests.get(1).getHeader("Authorization"));
    }

    @Test
    public void invalidRepositoryNamesNeverReachTheNetwork() {
        for (String bad : new String[]{"..", "a/b", "a b", "", "x?y=1"}) {
            try {
                api.fetchRepo(bad, "r");
                fail("expected rejection of " + bad);
            } catch (SourceException e) {
                assertEquals(SourceException.Kind.INVALID_URL, e.kind);
            }
        }
        assertTrue(requests.isEmpty());
    }

    @Test
    public void releasesSkipDraftsAndOptionallyPrereleases() throws Exception {
        String json = "[{\"id\":1,\"tag_name\":\"v2.0\",\"name\":\"Two\",\"body\":\"## Notes\\nFast.\",\"html_url\":\"https://github.com/o/r/releases/tag/v2.0\","
                + "\"published_at\":\"2026-10-10T08:00:00Z\",\"author\":{\"login\":\"dev\"},\"draft\":false,\"prerelease\":false},"
                + "{\"id\":2,\"tag_name\":\"v3.0-rc1\",\"name\":null,\"body\":null,\"html_url\":\"https://github.com/o/r/releases/tag/v3.0-rc1\","
                + "\"published_at\":\"2026-10-12T08:00:00Z\",\"draft\":false,\"prerelease\":true},"
                + "{\"id\":3,\"tag_name\":\"v4\",\"html_url\":\"https://github.com/o/r/releases/tag/v4\",\"draft\":true,\"prerelease\":false}]";
        routes.put("/repos/o/r/releases?per_page=10", new MockResponse().setBody(json).setHeader("ETag", "\"abc\""));
        TrackedRepo repo = new TrackedRepo();
        repo.owner = "o";
        repo.name = "r";
        ReleaseSource src = new ReleaseSource(api, Fixtures.factory());

        ReleaseSource.Result stable = src.fetch(repo, false);
        assertEquals(1, stable.items.size());
        ContentItem it = stable.items.get(0);
        assertEquals("o/r Two", it.title);
        assertEquals("dev", it.author);
        assertEquals("https://github.com/o/r/releases/tag/v2.0", it.canonicalUrl);
        assertEquals(BodyState.FULL, it.bodyState);
        assertEquals("\"abc\"", stable.etag);
        assertEquals(java.time.Instant.parse("2026-10-10T08:00:00Z").toEpochMilli(), it.publishedAt);

        ReleaseSource.Result all = src.fetch(repo, true);
        assertEquals(2, all.items.size());
        ContentItem pre = all.items.get(1);
        assertEquals("o/r v3.0-rc1", pre.title);
        assertTrue(pre.topics.contains("prerelease"));
        assertEquals(BodyState.NONE, pre.bodyState);
    }

    @Test
    public void releasesUseConditionalRequests() throws Exception {
        routes.put("/repos/o/r/releases?per_page=10", new MockResponse().setResponseCode(304));
        TrackedRepo repo = new TrackedRepo();
        repo.owner = "o";
        repo.name = "r";
        repo.etag = "\"abc\"";
        ReleaseSource.Result r = new ReleaseSource(api, Fixtures.factory()).fetch(repo, true);
        assertTrue(r.notModified);
        assertTrue(r.items.isEmpty());
        assertEquals("\"abc\"", requests.get(0).getHeader("If-None-Match"));
    }

    @Test
    public void missingRepositoryIsHttpError() {
        TrackedRepo repo = new TrackedRepo();
        repo.owner = "ghost";
        repo.name = "gone";
        try {
            new ReleaseSource(api, Fixtures.factory()).fetch(repo, true);
            fail("expected SourceException");
        } catch (SourceException e) {
            assertEquals(404, e.httpCode);
            assertFalse(e.isRetryable());
        }
    }
}
