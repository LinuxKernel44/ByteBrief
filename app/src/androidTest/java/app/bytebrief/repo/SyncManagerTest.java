package app.bytebrief.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.Feed;
import app.bytebrief.data.db.TrackedRepo;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.net.HttpFetcher;
import app.bytebrief.settings.SyncConfig;
import app.bytebrief.source.github.GithubApi;
import app.bytebrief.source.github.ReleaseSource;
import app.bytebrief.source.github.TrendingSource;
import app.bytebrief.source.hn.HackerNewsSource;
import app.bytebrief.source.rss.FeedSource;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** End-to-end synchronization against a local mock server and a real Room database. */
public class SyncManagerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);

    private AppDatabase db;
    private MockWebServer server;
    private final Map<String, MockResponse> routes = new HashMap<>();
    private SyncManager sync;
    private TestConfig config;

    static class TestConfig implements SyncConfig {
        final Set<SourceType> enabled = EnumSet.allOf(SourceType.class);

        @Override public boolean isSourceEnabled(SourceType t) { return enabled.contains(t); }
        @Override public Set<HackerNewsSource.Feed> hackerNewsFeeds() { return EnumSet.of(HackerNewsSource.Feed.TOP); }
        @Override public int hackerNewsLimit() { return 10; }
        @Override public TrendingSource.Period trendingPeriod() { return TrendingSource.Period.DAILY; }
        @Override public String trendingLanguage() { return null; }
        @Override public boolean includePrereleases() { return false; }
        @Override public int retentionDays() { return 36500; }
        @Override public int trendingEnrichBudget() { return 0; }
    }

    private String asset(String name) throws Exception {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                MockResponse m = routes.get(r.getPath());
                return m != null ? m : new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        String base = server.url("/").toString().replaceAll("/$", "");

        HttpFetcher http = new HttpFetcher(HttpFetcher.defaultClient(), CLOCK);
        ItemFactory factory = new ItemFactory(CLOCK);
        GithubApi api = new GithubApi(http, base + "/gh", null);
        config = new TestConfig();
        sync = new SyncManager(db, new Ingestor(db, CLOCK),
                new HackerNewsSource(http, factory, base + "/hn"),
                new TrendingSource(http, api, factory, base + "/trending", CLOCK),
                new FeedSource(http, factory, true),
                new ReleaseSource(api, factory), config, CLOCK);

        routes.put("/hn/topstories.json", new MockResponse().setBody("[1,2]"));
        routes.put("/hn/item/1.json", new MockResponse().setBody("{\"id\":1,\"type\":\"story\",\"by\":\"pg\",\"time\":1760000000,"
                + "\"title\":\"Liquid Glass\",\"url\":\"https://github.com/morluto/rea\",\"score\":10,\"descendants\":2}"));
        routes.put("/hn/item/2.json", new MockResponse().setBody("{\"id\":2,\"type\":\"story\",\"by\":\"x\",\"time\":1760000100,"
                + "\"title\":\"Other\",\"url\":\"https://other.example/p\",\"score\":3}"));
        routes.put("/trending?since=daily", new MockResponse().setBody(asset("fixtures/github_trending_daily.html")));
        routes.put("/feed.xml", new MockResponse().setBody(asset("fixtures/rss2_sample.xml")));
        routes.put("/gh/repos/o/r/releases?per_page=10", new MockResponse().setHeader("ETag", "\"r1\"").setBody(
                "[{\"id\":77,\"tag_name\":\"v1\",\"name\":\"One\",\"body\":\"notes\",\"html_url\":\"https://github.com/o/r/releases/tag/v1\","
                        + "\"published_at\":\"2026-10-14T00:00:00Z\",\"draft\":false,\"prerelease\":false}]"));

        Feed f = new Feed();
        f.url = base + "/feed.xml";
        db.feedDao().insertIgnore(f);
        TrackedRepo repo = new TrackedRepo();
        repo.owner = "o";
        repo.name = "r";
        db.trackedRepoDao().insertIgnore(repo);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        server.shutdown();
    }

    @Test
    public void firstSyncCollectsAllFourSourcesAndSecondSyncAddsNothing() {
        SyncReport first = sync.syncAll();
        for (Map.Entry<String, SyncReport.SourceOutcome> e : first.outcomes.entrySet()) {
            assertEquals(e.getKey() + ": " + e.getValue().error, SyncReport.Status.OK, e.getValue().status);
        }
        assertEquals(2, first.outcomes.get("hn").stats.inserted);
        assertEquals(12, first.outcomes.get("gh_trending").stats.inserted);
        assertEquals(3, first.outcomes.get("rss").stats.inserted);
        assertEquals(1, first.outcomes.get("gh_release").stats.inserted);
        assertEquals(1, first.newReleases);
        assertEquals(18, db.contentDao().count());

        SyncReport second = sync.syncAll();
        assertEquals(0, second.totalNew());
        assertEquals(18, db.contentDao().count());
        assertFalse(second.allFailed());
    }

    @Test
    public void hnStoryAndTrendingRepoShareADedupKey() {
        sync.syncAll();
        ContentItem hn = db.contentDao().findBySource("hn", "1");
        ContentItem repo = db.contentDao().findBySource("gh_trending", "morluto/rea");
        assertNotNull(hn);
        assertNotNull(repo);
        assertEquals(repo.dedupKey, hn.dedupKey);
        assertEquals(2, db.contentDao().getGroup(hn.dedupKey).size());
    }

    @Test
    public void oneFailingSourceDoesNotBlockTheOthers() {
        routes.put("/feed.xml", new MockResponse().setResponseCode(500));
        routes.remove("/trending?since=daily");
        SyncReport r = sync.syncAll();
        assertEquals(SyncReport.Status.OK, r.outcomes.get("hn").status);
        assertEquals(SyncReport.Status.FAILED, r.outcomes.get("rss").status);
        assertEquals(SyncReport.Status.FAILED, r.outcomes.get("gh_trending").status);
        assertEquals(SyncReport.Status.OK, r.outcomes.get("gh_release").status);
        assertTrue(r.outcomes.get("rss").retryable);
        assertFalse("404 is not retryable", r.outcomes.get("gh_trending").retryable);
        assertFalse(r.shouldRetry()); // something succeeded
        assertFalse(r.allFailed());
        Feed f = db.feedDao().getAll().get(0);
        assertEquals(1, f.consecutiveFailures);
        assertNotNull(f.lastError);
    }

    @Test
    public void feedValidatorsAreStoredAndRecoveryClearsTheError() {
        routes.put("/feed.xml", new MockResponse().setBody(
                "<rss version='2.0'><channel><title>T</title><item><title>x</title><link>https://a.example/1</link></item></channel></rss>")
                .setHeader("ETag", "\"abc\""));
        sync.syncAll();
        Feed f = db.feedDao().getAll().get(0);
        assertEquals("T", f.title);
        assertEquals("\"abc\"", f.etag);
        assertNull(f.lastError);
        assertEquals(0, f.consecutiveFailures);
    }

    @Test
    public void failingFeedIsBackedOffAfterRepeatedFailures() {
        routes.put("/feed.xml", new MockResponse().setResponseCode(500));
        for (int i = 0; i < 6; i++) sync.syncAll();
        Feed f = db.feedDao().getAll().get(0);
        assertEquals(5, f.consecutiveFailures); // the 6th pass skipped it
        // A manual refresh bypasses the backoff.
        sync.syncFeed(f.id);
        assertEquals(6, db.feedDao().getById(f.id).consecutiveFailures);
    }

    @Test
    public void disabledSourcesAreNotContacted() {
        config.enabled.clear();
        config.enabled.add(SourceType.HACKER_NEWS);
        SyncReport r = sync.syncAll();
        assertEquals(1, r.outcomes.size());
        assertEquals(2, db.contentDao().count());
    }

    @Test
    public void releaseEtagIsPersistedAndUsed() throws Exception {
        sync.syncAll();
        assertEquals("\"r1\"", db.trackedRepoDao().getEnabled().get(0).etag);
        routes.put("/gh/repos/o/r/releases?per_page=10", new MockResponse().setResponseCode(304));
        SyncReport r = sync.syncAll();
        assertEquals(0, r.newReleases);
        assertEquals(SyncReport.Status.OK, r.outcomes.get("gh_release").status);
    }

    @Test
    public void rateLimitedReleaseCheckIsRetryableAndStopsRemainingRepos() {
        TrackedRepo second = new TrackedRepo();
        second.owner = "p";
        second.name = "q";
        db.trackedRepoDao().insertIgnore(second);
        routes.put("/gh/repos/o/r/releases?per_page=10", new MockResponse().setResponseCode(403)
                .setHeader("x-ratelimit-remaining", "0").setHeader("x-ratelimit-reset", "1760000000"));
        SyncReport r = sync.syncAll();
        SyncReport.SourceOutcome o = r.outcomes.get("gh_release");
        assertEquals(SyncReport.Status.FAILED, o.status);
        assertTrue(o.retryable);
        assertEquals(1_760_000_000_000L, o.retryAfter);
    }
}
