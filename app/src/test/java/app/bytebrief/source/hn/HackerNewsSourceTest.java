package app.bytebrief.source.hn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.UrlCanonicalizer;
import app.bytebrief.net.SourceException;
import app.bytebrief.testutil.Fixtures;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class HackerNewsSourceTest {
    private MockWebServer server;
    private final Map<String, MockResponse> routes = new HashMap<>();
    private final List<String> requested = new java.util.concurrent.CopyOnWriteArrayList<>();
    private HackerNewsSource source;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requested.add(request.getPath());
                MockResponse r = routes.get(request.getPath());
                return r != null ? r : new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        source = new HackerNewsSource(Fixtures.fetcher(), Fixtures.factory(),
                server.url("/v0").toString().replaceAll("/$", ""));
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private void route(String path, String json) {
        routes.put(path, new MockResponse().setBody(json));
    }

    @Test
    public void mapsLinkStoryAndGroupsWithGithubUrl() throws Exception {
        route("/v0/topstories.json", "[1]");
        route("/v0/item/1.json", "{\"id\":1,\"type\":\"story\",\"by\":\"pg\",\"time\":1760000000,"
                + "\"title\":\"Show HN: Liquid Glass for Android\",\"url\":\"https://github.com/Kyant0/AndroidLiquidGlass?utm_source=hn\","
                + "\"score\":321,\"descendants\":45,\"kids\":[10,11]}");
        HackerNewsSource.Result r = source.fetch(HackerNewsSource.Feed.TOP, 10, null);
        assertEquals(1, r.items.size());
        ContentItem it = r.items.get(0);
        assertEquals("hn", it.sourceType);
        assertEquals("1", it.sourceId);
        assertEquals("pg", it.author);
        assertEquals(321, it.score);
        assertEquals(45, it.commentCount);
        assertEquals("https://github.com/Kyant0/AndroidLiquidGlass", it.canonicalUrl);
        assertEquals(UrlCanonicalizer.dedupKey("https://github.com/kyant0/androidliquidglass"), it.dedupKey);
        assertEquals("hacker-news,show-hn", it.topics);
        assertEquals(BodyState.NONE, it.bodyState);
        assertTrue(it.metadataJson.contains("news.ycombinator.com/item?id=1"));
        assertEquals(Fixtures.CLOCK.millis(), it.retrievedAt);
    }

    @Test
    public void askHnUsesDiscussionUrlAndStoresPostAsBody() throws Exception {
        route("/v0/askstories.json", "[2]");
        route("/v0/item/2.json", "{\"id\":2,\"type\":\"story\",\"by\":\"a\",\"time\":1760000000,"
                + "\"title\":\"Ask HN: Best local LLM?\",\"text\":\"I run <i>llama.cpp</i>.<p>Any tips?\",\"score\":5,\"descendants\":3}");
        ContentItem it = source.fetch(HackerNewsSource.Feed.ASK, 10, null).items.get(0);
        assertEquals("https://news.ycombinator.com/item?id=2", it.canonicalUrl);
        assertEquals(BodyState.FULL, it.bodyState);
        assertTrue(it.bodyText.contains("llama.cpp"));
        assertFalse(it.bodyText.contains("<i>"));
        assertTrue(it.topics.contains("ask-hn"));
    }

    @Test
    public void unsafeLinkFallsBackToDiscussionPage() throws Exception {
        route("/v0/newstories.json", "[3]");
        route("/v0/item/3.json", "{\"id\":3,\"type\":\"story\",\"by\":\"x\",\"time\":1760000000,"
                + "\"title\":\"Sneaky\",\"url\":\"javascript:alert(1)\",\"score\":1}");
        ContentItem it = source.fetch(HackerNewsSource.Feed.NEW, 10, null).items.get(0);
        assertEquals("https://news.ycombinator.com/item?id=3", it.canonicalUrl);
    }

    @Test
    public void dropsJobsCommentsDeadAndDeletedItems() throws Exception {
        route("/v0/topstories.json", "[4,5,6,7,8]");
        route("/v0/item/4.json", "{\"id\":4,\"type\":\"job\",\"title\":\"Hiring\",\"time\":1760000000}");
        route("/v0/item/5.json", "{\"id\":5,\"type\":\"comment\",\"text\":\"hi\"}");
        route("/v0/item/6.json", "{\"id\":6,\"type\":\"story\",\"title\":\"Dead\",\"dead\":true,\"url\":\"https://a.example/x\"}");
        route("/v0/item/7.json", "{\"id\":7,\"type\":\"story\",\"deleted\":true}");
        route("/v0/item/8.json", "null");
        HackerNewsSource.Result r = source.fetch(HackerNewsSource.Feed.TOP, 10, null);
        assertTrue(r.items.isEmpty());
        assertEquals(0, r.failures);
    }

    @Test
    public void skipsAlreadyFreshIdsWithoutRequestingThem() throws Exception {
        route("/v0/topstories.json", "[10,11]");
        route("/v0/item/11.json", "{\"id\":11,\"type\":\"story\",\"by\":\"b\",\"time\":1760000000,"
                + "\"title\":\"Fresh\",\"url\":\"https://b.example/p\",\"score\":2}");
        HackerNewsSource.Result r = source.fetch(HackerNewsSource.Feed.TOP, 10, id -> id.equals("10"));
        assertEquals(1, r.items.size());
        assertEquals(1, r.skipped);
        assertFalse(requested.contains("/v0/item/10.json"));
    }

    @Test
    public void respectsLimitAndCountsPerItemFailures() throws Exception {
        route("/v0/topstories.json", "[20,21,22,23]");
        route("/v0/item/20.json", "{\"id\":20,\"type\":\"story\",\"time\":1760000000,\"title\":\"ok\",\"url\":\"https://c.example/1\"}");
        routes.put("/v0/item/21.json", new MockResponse().setResponseCode(500));
        HackerNewsSource.Result r = source.fetch(HackerNewsSource.Feed.TOP, 2, null);
        assertEquals(1, r.items.size());
        assertEquals(1, r.failures);
        assertFalse(requested.contains("/v0/item/22.json"));
    }

    @Test
    public void idListFailureIsReported() {
        routes.put("/v0/topstories.json", new MockResponse().setResponseCode(503));
        try {
            source.fetch(HackerNewsSource.Feed.TOP, 10, null);
            fail("expected SourceException");
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.HTTP, e.kind);
            assertTrue(e.isRetryable());
        }
    }

    @Test
    public void malformedIdListIsParseError() {
        route("/v0/topstories.json", "{not json");
        try {
            source.fetch(HackerNewsSource.Feed.TOP, 10, null);
            fail("expected SourceException");
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.PARSE, e.kind);
            assertFalse(e.isRetryable());
        }
    }

    @Test
    public void topCommentsAreFlattenedToPlainTextAndFiltered() throws Exception {
        route("/v0/item/30.json", "{\"id\":30,\"type\":\"story\",\"kids\":[31,32,33,34]}");
        route("/v0/item/31.json", "{\"id\":31,\"type\":\"comment\",\"by\":\"u1\",\"text\":\"First &amp; <b>best</b>\"}");
        route("/v0/item/32.json", "{\"id\":32,\"type\":\"comment\",\"deleted\":true}");
        route("/v0/item/33.json", "{\"id\":33,\"type\":\"comment\",\"by\":\"u3\",\"text\":\"Third\"}");
        route("/v0/item/34.json", "{\"id\":34,\"type\":\"comment\",\"by\":\"u4\",\"text\":\"Fourth\"}");
        List<HackerNewsSource.Comment> comments = source.fetchTopComments(30, 2);
        assertEquals(2, comments.size());
        assertEquals("First & best", comments.get(0).text);
        assertEquals("u3", comments.get(1).author);
        assertNotNull(comments.get(1).text);
    }
}
