package app.bytebrief.source.forum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ForumUrlsTest {
    @Test
    public void normalizesForumAddresses() {
        String want = "https://xdaforums.com/f/oneplus-15.12971/";
        assertEquals(want, ForumUrls.normalizeNode("https://xdaforums.com/f/oneplus-15.12971/"));
        assertEquals(want, ForumUrls.normalizeNode("xdaforums.com/f/oneplus-15.12971"));
        assertEquals(want, ForumUrls.normalizeNode("  https://www.xdaforums.com/f/oneplus-15.12971/page-3?prefix_id=4#top "));
        assertEquals(want, ForumUrls.normalizeNode("http://xdaforums.com/f/oneplus-15.12971/")); // upgraded, never kept as cleartext
        assertEquals(want, ForumUrls.normalizeNode("/f/oneplus-15.12971/"));
        assertEquals("https://xdaforums.com/c/oneplus.11993/", ForumUrls.normalizeNode("https://xdaforums.com/c/oneplus.11993/"));
        assertEquals(ForumUrls.HOME, ForumUrls.normalizeNode("https://xdaforums.com"));
    }

    @Test
    public void rejectsAnythingElse() {
        assertNull(ForumUrls.normalizeNode(null));
        assertNull(ForumUrls.normalizeNode(""));
        assertNull(ForumUrls.normalizeNode("https://example.com/f/oneplus-15.12971/"));
        assertNull(ForumUrls.normalizeNode("https://xdaforums.com.evil.example/f/x.1/"));
        assertNull(ForumUrls.normalizeNode("https://evil.example/https://xdaforums.com/f/x.1/"));
        assertNull(ForumUrls.normalizeNode("https://xdaforums.com/t/some-thread.123/"));
        assertNull(ForumUrls.normalizeNode("https://xdaforums.com/members/someone.1/"));
        assertNull(ForumUrls.normalizeNode("javascript:alert(1)"));
        assertNull(ForumUrls.normalizeNode("file:///etc/passwd"));
    }

    @Test
    public void onlyTheForumHostIsAllowed() {
        assertTrue(ForumUrls.isForumHost("xdaforums.com"));
        assertTrue(ForumUrls.isForumHost("WWW.XDAFORUMS.COM"));
        assertFalse(ForumUrls.isForumHost("notxdaforums.com"));
        assertFalse(ForumUrls.isForumHost("xdaforums.com.evil.example"));
    }

    @Test
    public void resolveStaysOnTheForum() {
        assertEquals("https://xdaforums.com/f/a.1/page-2", ForumUrls.resolve("https://xdaforums.com/f/a.1/", "page-2"));
        assertEquals("https://xdaforums.com/t/x.5/", ForumUrls.resolve("https://xdaforums.com/f/a.1/", "/t/x.5/#post-9"));
        assertNull(ForumUrls.resolve("https://xdaforums.com/f/a.1/", "https://evil.example/x"));
        assertNull(ForumUrls.resolve("https://xdaforums.com/f/a.1/", "http://xdaforums.com/x"));
        assertNull(ForumUrls.resolve("https://xdaforums.com/f/a.1/", "javascript:alert(1)"));
        assertNull(ForumUrls.resolve("https://xdaforums.com/f/a.1/", " "));
    }

    @Test
    public void threadIdIsTheNumberAfterTheLastDot() {
        assertEquals(4793023L, ForumUrls.threadId("https://xdaforums.com/t/project-infinity-x-4-1-for-oneplus-15.4793023/"));
        assertEquals(4793023L, ForumUrls.threadId("https://xdaforums.com/t/oneplus-15.4793023/page-2"));
        assertEquals(77L, ForumUrls.threadId("https://xdaforums.com/threads/old.77"));
        assertEquals(-1L, ForumUrls.threadId("https://xdaforums.com/f/oneplus-15.12971/"));
        assertEquals(-1L, ForumUrls.threadId(null));
    }
}
