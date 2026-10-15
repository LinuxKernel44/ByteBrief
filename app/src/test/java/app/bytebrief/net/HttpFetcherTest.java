package app.bytebrief.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import app.bytebrief.testutil.Fixtures;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class HttpFetcherTest {
    private MockWebServer server;
    private HttpFetcher fetcher;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        fetcher = Fixtures.fetcher();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private SourceException expectFailure(String url, long max) {
        try {
            fetcher.get(url, null, max);
            fail("expected SourceException");
            return null;
        } catch (SourceException e) {
            return e;
        }
    }

    @Test
    public void rejectsLocalAndNonHttpSchemesBeforeAnyRequest() {
        for (String url : new String[]{"file:///etc/passwd", "content://x/y", "javascript:1", "ftp://h/f", "nonsense"}) {
            assertEquals(url, SourceException.Kind.INVALID_URL, expectFailure(url, 1000).kind);
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void sendsIdentifyingUserAgentAndReturnsValidators() throws Exception {
        server.enqueue(new MockResponse().setBody("hello").setHeader("ETag", "\"v1\"").setHeader("Last-Modified", "Wed, 01 Oct 2026 00:00:00 GMT"));
        FetchResult r = fetcher.get(server.url("/x").toString(), null);
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getHeader("User-Agent").startsWith("ByteBrief/"));
        assertEquals("hello", r.body);
        assertEquals("\"v1\"", r.etag);
        assertEquals("Wed, 01 Oct 2026 00:00:00 GMT", r.lastModified);
    }

    @Test
    public void enforcesSizeLimitForUndeclaredAndDeclaredLengths() {
        server.enqueue(new MockResponse().setBody("x".repeat(5000)));
        assertEquals(SourceException.Kind.TOO_LARGE, expectFailure(server.url("/big").toString(), 1000).kind);
        server.enqueue(new MockResponse().setChunkedBody("y".repeat(5000), 100));
        assertEquals(SourceException.Kind.TOO_LARGE, expectFailure(server.url("/chunked").toString(), 1000).kind);
    }

    @Test
    public void mapsRateLimitingWithRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "30"));
        SourceException e = expectFailure(server.url("/r").toString(), 1000);
        assertEquals(SourceException.Kind.RATE_LIMITED, e.kind);
        assertEquals(Fixtures.CLOCK.millis() + 30_000, e.retryAfterEpochMillis);
        assertTrue(e.isRetryable());
    }

    @Test
    public void githubStyleExhaustedQuotaIsRateLimited() {
        server.enqueue(new MockResponse().setResponseCode(403).setHeader("x-ratelimit-remaining", "0")
                .setHeader("x-ratelimit-reset", "1760000000"));
        SourceException e = expectFailure(server.url("/r").toString(), 1000);
        assertEquals(SourceException.Kind.RATE_LIMITED, e.kind);
        assertEquals(1_760_000_000_000L, e.retryAfterEpochMillis);
    }

    @Test
    public void plainForbiddenIsNotRetryable() {
        server.enqueue(new MockResponse().setResponseCode(403));
        SourceException e = expectFailure(server.url("/r").toString(), 1000);
        assertEquals(SourceException.Kind.HTTP, e.kind);
        assertTrue(!e.isRetryable());
    }

    @Test
    public void serverErrorsAreRetryableAndNetworkFailuresAreReported() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(502));
        assertTrue(expectFailure(server.url("/e").toString(), 1000).isRetryable());
        String url = server.url("/gone").toString();
        server.shutdown();
        assertEquals(SourceException.Kind.NETWORK, expectFailure(url, 1000).kind);
    }

    @Test
    public void followsRedirectsAndReportsFinalUrl() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(301).setHeader("Location", "/final"));
        server.enqueue(new MockResponse().setBody("done"));
        FetchResult r = fetcher.get(server.url("/start").toString(), null);
        assertEquals("done", r.body);
        assertTrue(r.finalUrl.endsWith("/final"));
    }
}
