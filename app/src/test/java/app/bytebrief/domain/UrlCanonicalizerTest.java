package app.bytebrief.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UrlCanonicalizerTest {
    @Test
    public void rejectsNonHttpSchemes() {
        String[] bad = {"file:///etc/passwd", "content://media/external/1", "javascript:alert(1)",
                "ftp://example.com/feed", "data:text/html,hi", "intent://x#Intent;end", "", "   ", null,
                "not a url", "//example.com/x"};
        for (String url : bad) {
            assertNull("canonicalize " + url, UrlCanonicalizer.canonicalize(url));
            assertNull("dedupKey " + url, UrlCanonicalizer.dedupKey(url));
            assertFalse("isHttpUrl " + url, UrlCanonicalizer.isHttpUrl(url));
            assertNull("toHttps " + url, UrlCanonicalizer.toHttps(url));
        }
    }

    @Test
    public void rejectsOverlongUrls() {
        StringBuilder sb = new StringBuilder("https://example.com/");
        for (int i = 0; i < 2100; i++) sb.append('a');
        assertNull(UrlCanonicalizer.canonicalize(sb.toString()));
    }

    @Test
    public void stripsTrackingParamsFragmentAndCredentials() {
        assertEquals("https://example.com/post?id=7",
                UrlCanonicalizer.canonicalize("https://user:pw@example.com/post?utm_source=x&id=7&fbclid=abc#top"));
    }

    @Test
    public void keepsSignificantQueryParams() {
        assertEquals("https://example.com/item?id=42",
                UrlCanonicalizer.canonicalize("https://example.com/item?id=42"));
    }

    @Test
    public void dedupKeyIgnoresSchemeWwwSlashQueryOrderAndTracking() {
        String a = UrlCanonicalizer.dedupKey("http://www.Example.com/a/b/?b=2&a=1&utm_campaign=z#frag");
        String b = UrlCanonicalizer.dedupKey("https://example.com/a/b?a=1&b=2");
        assertEquals(a, b);
    }

    @Test
    public void dedupKeyKeepsDifferentPathsDistinct() {
        assertNotEquals(UrlCanonicalizer.dedupKey("https://example.com/a"),
                UrlCanonicalizer.dedupKey("https://example.com/b"));
        assertNotEquals(UrlCanonicalizer.dedupKey("https://example.com/item?id=1"),
                UrlCanonicalizer.dedupKey("https://example.com/item?id=2"));
    }

    @Test
    public void githubRepositoryUrlsCollapseToOwnerRepo() {
        String expected = "u:github.com/kyant0/androidliquidglass";
        assertEquals(expected, UrlCanonicalizer.dedupKey("https://github.com/Kyant0/AndroidLiquidGlass"));
        assertEquals(expected, UrlCanonicalizer.dedupKey("https://www.github.com/kyant0/androidliquidglass/"));
        assertEquals(expected, UrlCanonicalizer.dedupKey("https://github.com/Kyant0/AndroidLiquidGlass.git"));
    }

    @Test
    public void githubReleaseIsNotMergedWithRepository() {
        assertNotEquals(UrlCanonicalizer.dedupKey("https://github.com/o/r"),
                UrlCanonicalizer.dedupKey("https://github.com/o/r/releases/tag/v1.0"));
    }

    @Test
    public void nonDefaultPortIsPartOfTheKey() {
        assertNotEquals(UrlCanonicalizer.dedupKey("https://example.com/x"),
                UrlCanonicalizer.dedupKey("https://example.com:8443/x"));
    }

    @Test
    public void toHttpsUpgradesPlainHttp() {
        assertEquals("https://example.com/feed.xml", UrlCanonicalizer.toHttps("http://example.com/feed.xml"));
        assertTrue(UrlCanonicalizer.toHttps("https://example.com/").startsWith("https://"));
    }
}
