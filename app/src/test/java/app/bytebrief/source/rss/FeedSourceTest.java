package app.bytebrief.source.rss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.Feed;
import app.bytebrief.domain.BodyState;
import app.bytebrief.net.SourceException;
import app.bytebrief.testutil.Fixtures;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class FeedSourceTest {
    private MockWebServer server;
    private final Map<String, MockResponse> routes = new HashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private FeedSource source;

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
        source = new FeedSource(Fixtures.fetcher(), Fixtures.factory(), true);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private Feed feed(String path) {
        Feed f = new Feed();
        f.id = 5;
        f.url = server.url(path).toString();
        return f;
    }

    @Test
    public void mapsEntriesToItemsWithCachingStateAndSafeLinks() throws Exception {
        routes.put("/feed.xml", new MockResponse().setBody(Fixtures.resource("fixtures/rss2_sample.xml"))
                .setHeader("ETag", "\"e1\"").setHeader("Last-Modified", "Tue, 13 Oct 2026 12:00:00 GMT"));
        FeedSource.Refresh r = source.refresh(feed("/feed.xml"));
        assertFalse(r.notModified);
        assertEquals("Example Engineering Blog", r.title);
        assertEquals("\"e1\"", r.etag);
        // Skips: no link/guid, and the javascript: entry.
        assertEquals(3, r.items.size());

        ContentItem full = r.items.get(0);
        assertEquals("rss", full.sourceType);
        assertEquals("5:post-7", full.sourceId);
        assertEquals(Long.valueOf(5), full.feedId);
        assertEquals("Building a fast parser", full.title);
        assertEquals("https://blog.example.com/posts/fast-parser?id=7", full.canonicalUrl);
        assertEquals(BodyState.FULL, full.bodyState);
        assertTrue(full.bodyText.contains("Second paragraph"));
        assertFalse(full.bodyText.contains("alert(1)"));
        assertEquals("Parsing,Performance".toLowerCase(), full.topics);
        assertEquals("https://cdn.example.com/thumb.jpg", full.imageUrl);
        assertEquals("Ada Lovelace", full.author);

        ContentItem snippet = r.items.get(1);
        assertTrue(snippet.canonicalUrl.endsWith("/posts/relative"));
        assertEquals(BodyState.SNIPPET, snippet.bodyState);
        assertEquals("Only a summary here.", snippet.description);
        assertEquals("https://cdn.example.com/cover.png", snippet.imageUrl);

        ContentItem byGuid = r.items.get(2);
        assertEquals("https://blog.example.com/posts/by-guid", byGuid.canonicalUrl);
        assertEquals(Fixtures.CLOCK.millis(), byGuid.publishedAt);
    }

    @Test
    public void imageFallsBackToFirstImgInContent() throws Exception {
        routes.put("/a.xml", new MockResponse().setBody("<rss version='2.0'><channel><title>t</title><item><title>x</title>"
                + "<link>https://a.example/p</link><description><![CDATA[<p>hi</p><img src='/i/a.png'>]]></description></item></channel></rss>"));
        ContentItem it = source.refresh(feed("/a.xml")).items.get(0);
        assertEquals(server.url("/i/a.png").toString(), it.imageUrl);
    }

    @Test
    public void conditionalRequestUsesValidatorsAndHandles304() throws Exception {
        routes.put("/feed.xml", new MockResponse().setResponseCode(304));
        Feed f = feed("/feed.xml");
        f.etag = "\"e1\"";
        f.lastModified = "Tue, 13 Oct 2026 12:00:00 GMT";
        FeedSource.Refresh r = source.refresh(f);
        assertTrue(r.notModified);
        assertTrue(r.items.isEmpty());
        assertEquals("\"e1\"", requests.get(0).getHeader("If-None-Match"));
        assertEquals("Tue, 13 Oct 2026 12:00:00 GMT", requests.get(0).getHeader("If-Modified-Since"));
    }

    @Test
    public void redirectedFeedResolvesRelativeLinksAgainstFinalUrl() throws Exception {
        routes.put("/old", new MockResponse().setResponseCode(301).setHeader("Location", "/new/feed.xml"));
        routes.put("/new/feed.xml", new MockResponse().setBody("<rss version='2.0'><channel><title>t</title><item><title>x</title>"
                + "<link>post</link></item></channel></rss>"));
        ContentItem it = source.refresh(feed("/old")).items.get(0);
        assertEquals(server.url("/new/post").toString(), it.canonicalUrl);
    }

    @Test
    public void transientAndMalformedFailuresAreClassified() {
        routes.put("/down", new MockResponse().setResponseCode(503));
        routes.put("/bad", new MockResponse().setBody("<html>oops</html>"));
        try {
            source.refresh(feed("/down"));
            fail();
        } catch (SourceException e) {
            assertTrue(e.isRetryable());
        }
        try {
            source.refresh(feed("/bad"));
            fail();
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.PARSE, e.kind);
            assertFalse(e.isRetryable());
        }
    }

    @Test
    public void validateAcceptsFeedsAndDiscoversFeedFromHtmlPage() throws Exception {
        routes.put("/direct.xml", new MockResponse().setBody(Fixtures.resource("fixtures/atom_sample.xml")));
        FeedSource.Validated v = source.validate(server.url("/direct.xml").toString());
        assertEquals("Atom Journal", v.title);
        assertEquals(2, v.parsed.entries.size());

        routes.put("/", new MockResponse().setBody(Fixtures.resource("fixtures/page_with_feed.html")));
        routes.put("/feed.xml", new MockResponse().setBody(Fixtures.resource("fixtures/rss2_sample.xml")));
        FeedSource.Validated discovered = source.validate(server.url("/").toString());
        assertEquals("Example Engineering Blog", discovered.title);
        assertTrue(discovered.feedUrl.endsWith("/feed.xml"));
    }

    @Test
    public void validateRejectsUnsafeSchemesAndHtmlWithoutFeed() {
        for (String bad : new String[]{"file:///etc/passwd", "content://x", "javascript:1", "", "garbage"}) {
            try {
                source.validate(bad);
                fail("expected rejection of " + bad);
            } catch (SourceException e) {
                assertEquals(SourceException.Kind.INVALID_URL, e.kind);
            }
        }
        routes.put("/plain.html", new MockResponse().setBody("<!doctype html><html><body>no feed</body></html>"));
        try {
            source.validate(server.url("/plain.html").toString());
            fail();
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.PARSE, e.kind);
        }
    }
}
