package app.bytebrief.source.forum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.Forum;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.net.PageFetcher;
import app.bytebrief.net.SourceException;

public class ForumSourceTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);

    /** Serves canned pages and records what was requested. */
    static final class FakeFetcher implements PageFetcher {
        final Map<String, String> pages = new HashMap<>();
        final List<String> requested = new ArrayList<>();
        SourceException failWith;

        @Override
        public Page load(String url) throws SourceException {
            requested.add(url);
            if (failWith != null) throw failWith;
            String html = pages.get(url);
            if (html == null) throw new SourceException(SourceException.Kind.HTTP, "HTTP 404", 404, 0, null);
            return new Page(html, url);
        }
    }

    private final FakeFetcher fetcher = new FakeFetcher();
    private final ForumSource source = new ForumSource(fetcher, new ItemFactory(CLOCK));

    private static Forum forum() {
        Forum f = new Forum();
        f.id = 7;
        f.url = ForumFixtures.BASE;
        f.title = "OnePlus 15";
        return f;
    }

    @Test
    public void threadsBecomeItemsWithoutABody() throws Exception {
        long started = CLOCK.millis() / 1000 - 3600;
        fetcher.pages.put(ForumFixtures.BASE, ForumFixtures.forumPage("OnePlus 15",
                ForumFixtures.threadRow(4793023, "infinity-x", "Development", "Infinity-X for OnePlus 15", "koaaN", started - 86_400, started, "681", "53K", false), null));
        List<ContentItem> items = source.fetchThreads(forum());

        assertEquals(1, items.size());
        ContentItem i = items.get(0);
        assertEquals("xda", i.sourceType);
        assertEquals("7:4793023", i.sourceId);
        assertEquals(Long.valueOf(7), i.feedId);
        assertEquals("OnePlus 15", i.sourceLabel);
        assertEquals("Infinity-X for OnePlus 15", i.title);
        assertEquals("https://xdaforums.com/t/infinity-x.4793023", i.canonicalUrl);
        assertEquals("https://xdaforums.com/t/infinity-x.4793023/", i.originalUrl);
        assertEquals("koaaN", i.author);
        assertEquals(681, i.commentCount);
        assertEquals(53_000L, i.score);
        assertEquals(started * 1000, i.publishedAt); // the last activity, so active old threads resurface
        assertEquals("Started by koaaN", i.description);
        assertEquals(BodyState.SNIPPET, i.bodyState);
        assertTrue(i.topics, i.topics.contains("development"));
        JSONObject meta = new JSONObject(i.metadataJson);
        assertEquals(681, meta.getInt("replies"));
        assertEquals("Development", meta.getString("prefix"));
    }

    @Test
    public void validateRejectsHomeCategoriesAndOtherSites() {
        assertInvalid("https://xdaforums.com/");
        assertInvalid("https://example.com/f/x.1/");
        assertTrue(fetcher.requested.isEmpty());
    }

    private void assertInvalid(String url) {
        try {
            source.validate(url);
            fail("expected " + url + " to be rejected");
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.INVALID_URL, e.kind);
        }
    }

    @Test
    public void aCategoryIsNotFollowable() {
        String url = "https://xdaforums.com/c/oneplus.11993/";
        fetcher.pages.put(url, ForumFixtures.nodePage("OnePlus", ForumFixtures.node("/f/oneplus-15.12971/", "OnePlus 15", "")));
        try {
            source.validate(url);
            fail();
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.PARSE, e.kind);
            assertTrue(e.getMessage(), e.getMessage().contains("category"));
        }
    }

    @Test
    public void validateReturnsTheTitleAndThreads() throws Exception {
        fetcher.pages.put(ForumFixtures.BASE, ForumFixtures.forumPage("OnePlus 15", ForumFixtures.threadRow(5, "t", null, "T", "a", 1, 1, "0", "1", false), null));
        ForumSource.Validated v = source.validate("xdaforums.com/f/oneplus-15.12971");
        assertEquals(ForumFixtures.BASE, v.url);
        assertEquals("OnePlus 15", v.title);
        assertEquals(1, v.threads.size());
    }

    @Test
    public void verificationAndErrorPagesSurfaceAsErrors() throws Exception {
        fetcher.failWith = SourceException.verificationRequired("verify");
        try {
            source.fetchThreads(forum());
            fail();
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.VERIFICATION_REQUIRED, e.kind);
        }
        fetcher.failWith = null;
        fetcher.pages.put(ForumFixtures.BASE, ForumFixtures.ERROR_PAGE);
        try {
            source.fetchThreads(forum());
            fail();
        } catch (SourceException e) {
            assertEquals(404, e.httpCode);
        }
    }

    @Test
    public void aLayoutChangeIsReportedNotGuessed() {
        fetcher.pages.put(ForumFixtures.BASE, ForumFixtures.forumPage("OnePlus 15", "<div class=\"somethingNew\">threads</div>", null));
        try {
            source.fetchThreads(forum());
            fail();
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.PARSE, e.kind);
        }
    }

    @Test
    public void aThreadIsReadAcrossPages() throws Exception {
        String p1 = "https://xdaforums.com/t/a.9/";
        String p2 = "https://xdaforums.com/t/a.9/page-2";
        fetcher.pages.put(p1, ForumFixtures.threadPage("A", null, ForumFixtures.post(1, "alice", 1_700_000_000, "First", "")
                + ForumFixtures.post(2, "bob", 1_700_000_100, "Second", ""), "/t/a.9/page-2"));
        fetcher.pages.put(p2, ForumFixtures.threadPage("A", null, ForumFixtures.post(3, "carol", 1_700_086_400, "Third", ""), null));

        String text = source.fetchThreadText(p1);
        assertEquals(List.of(p1, p2), fetcher.requested);
        assertEquals("## #1 · alice · 2023-11-14\n\nFirst\n\n## #2 · bob · 2023-11-14\n\nSecond\n\n## #3 · carol · 2023-11-15\n\nThird", text);
    }

    @Test
    public void longThreadsStopAtThePageLimitAndSaySo() throws Exception {
        for (int i = 1; i <= ForumSource.MAX_THREAD_PAGES + 3; i++) {
            String url = "https://xdaforums.com/t/a.9/" + (i == 1 ? "" : "page-" + i);
            String next = i == ForumSource.MAX_THREAD_PAGES + 3 ? null : "/t/a.9/page-" + (i + 1);
            fetcher.pages.put(url, ForumFixtures.threadPage("A", null, ForumFixtures.post(i, "u" + i, 1_700_000_000, "post " + i, ""), next));
        }
        String text = source.fetchThreadText("https://xdaforums.com/t/a.9/");
        assertEquals(ForumSource.MAX_THREAD_PAGES, fetcher.requested.size());
        assertTrue(text, text.contains("post " + ForumSource.MAX_THREAD_PAGES));
        assertTrue(text, text.endsWith("Open the original to read the rest of the thread._"));
    }

    @Test
    public void aPageLoopCannotRunForever() throws Exception {
        String url = "https://xdaforums.com/t/a.9/";
        fetcher.pages.put(url, ForumFixtures.threadPage("A", null, ForumFixtures.post(1, "a", 1, "x", ""), "/t/a.9/"));
        source.fetchThreadText(url);
        assertEquals(1, fetcher.requested.size());
    }

    @Test
    public void aThreadWithoutPostsIsAnErrorAndOtherHostsAreRefused() {
        fetcher.pages.put("https://xdaforums.com/t/a.9/", ForumFixtures.threadPage("A", null, "", null));
        try {
            source.fetchThreadText("https://xdaforums.com/t/a.9/");
            fail();
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.PARSE, e.kind);
        }
        try {
            source.fetchThreadText("https://example.com/t/a.9/");
            fail();
        } catch (SourceException e) {
            assertEquals(SourceException.Kind.INVALID_URL, e.kind);
        }
    }

    @Test
    public void browseStartsAtTheHome() throws Exception {
        fetcher.pages.put(ForumUrls.HOME, ForumFixtures.nodePage("XDA Forums", ForumFixtures.node("/c/oneplus.11993/", "OnePlus", "")));
        ForumPageModels.NodeList list = source.browse(null);
        assertNotNull(list);
        assertEquals(1, list.nodes.size());
        assertTrue(list.nodes.get(0).category);
    }
}
