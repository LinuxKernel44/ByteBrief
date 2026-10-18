package app.bytebrief.source.forum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.bytebrief.source.forum.ForumPageModels.NodeList;
import app.bytebrief.source.forum.ForumPageModels.Post;
import app.bytebrief.source.forum.ForumPageModels.ThreadList;
import app.bytebrief.source.forum.ForumPageModels.ThreadPage;
import app.bytebrief.source.forum.ForumPageModels.ThreadSummary;

public class ForumParserTest {
    @Test
    public void readsAThreadList() {
        String html = ForumFixtures.forumPage("OnePlus 15",
                ForumFixtures.threadRow(11, "read-first", "General", "Read first", "mod", 1_600_000_000, 1_700_000_000, "0", "4K", true)
                        + ForumFixtures.threadRow(4793023, "infinity-x", "Development", "Infinity-X for OnePlus 15", "koaaN", 1_790_000_000, 1_792_324_240, "1,234", "1.5M", false),
                "/f/oneplus-15.12971/page-2");
        ThreadList list = ForumParser.parseThreadList(html, ForumFixtures.BASE);

        assertEquals("OnePlus 15", list.title);
        assertEquals("https://xdaforums.com/f/oneplus-15.12971/page-2", list.nextUrl);
        assertEquals(2, list.threads.size());
        ThreadSummary sticky = list.threads.get(0);
        assertTrue(sticky.sticky);
        assertEquals(11L, sticky.threadId);
        assertEquals(4000L, sticky.views);
        ThreadSummary t = list.threads.get(1);
        assertFalse(t.sticky);
        assertEquals(4793023L, t.threadId);
        assertEquals("Infinity-X for OnePlus 15", t.title);
        assertEquals("https://xdaforums.com/t/infinity-x.4793023/", t.url);
        assertEquals("koaaN", t.author);
        assertEquals("Development", t.prefix);
        assertEquals(1234, t.replies);
        assertEquals(1_500_000L, t.views);
        assertEquals(1_790_000_000_000L, t.startedAt);
        assertEquals(1_792_324_240_000L, t.lastPostAt);
    }

    @Test
    public void rowsWithoutAThreadLinkAreSkipped() {
        String html = ForumFixtures.forumPage("F", "<div class=\"structItem structItem--thread\"><div class=\"structItem-title\">no link</div></div>", null);
        ThreadList list = ForumParser.parseThreadList(html, ForumFixtures.BASE);
        assertTrue(list.threads.isEmpty());
        assertNull(list.nextUrl);
    }

    @Test
    public void linksOffTheForumAreNeverFollowed() {
        String html = ForumFixtures.forumPage("F", ForumFixtures.threadRow(5, "t", null, "T", "a", 1, 1, "0", "1", false), "https://evil.example/page-2");
        assertNull(ForumParser.parseThreadList(html, ForumFixtures.BASE).nextUrl);
    }

    @Test
    public void readsPostsAndTheirText() {
        String body = "Hello <b>world</b>.<br><br>Second paragraph with <a href=\"https://example.com/file\">a link</a>."
                + "<div class=\"bbCodeBlock bbCodeBlock--screenLimited bbCodeBlock--code\"><div class=\"bbCodeBlock-title\">Code:</div>"
                + "<div class=\"bbCodeBlock-content\"><pre class=\"bbCodeCode\"><code>fastboot flash boot boot.img\nfastboot reboot</code></pre></div></div>"
                + "<script>alert(1)</script>Done";
        String html = ForumFixtures.threadPage("A thread", "Question",
                ForumFixtures.post(101, "alice", 1_700_000_000, body, "<section class=\"message-attachments\"><ul><li><span class=\"file-name\">rom.zip</span></li></ul></section>")
                        + ForumFixtures.post(102, "bob", 1_700_000_100, "Thanks!", ""),
                "/t/a-thread.9/page-2");
        ThreadPage page = ForumParser.parseThreadPage(html, "https://xdaforums.com/t/a-thread.9/");

        assertEquals("A thread", page.title);
        assertEquals("https://xdaforums.com/t/a-thread.9/page-2", page.nextUrl);
        assertEquals(2, page.posts.size());
        Post p = page.posts.get(0);
        assertEquals(101L, p.id);
        assertEquals("alice", p.author);
        assertEquals(1_700_000_000_000L, p.postedAt);
        assertTrue(p.text, p.text.startsWith("Hello **world**."));
        assertTrue(p.text, p.text.contains("a link (https://example.com/file)"));
        assertTrue(p.text, p.text.contains("```\nfastboot flash boot boot.img\nfastboot reboot\n```"));
        assertFalse(p.text, p.text.contains("alert"));
        assertTrue(p.text, p.text.endsWith("Done"));
        assertEquals(1, p.attachments.size());
        assertEquals("rom.zip", p.attachments.get(0));
        assertEquals("Thanks!", page.posts.get(1).text);
    }

    @Test
    public void quotesAreShortenedAndLabelled() {
        String longText = "word ".repeat(200);
        String quote = "<blockquote class=\"bbCodeBlock bbCodeBlock--expandable bbCodeBlock--quote\" data-quote=\"carol\">"
                + "<div class=\"bbCodeBlock-title\"><a>carol said:</a></div><div class=\"bbCodeBlock-content\"><div class=\"bbCodeBlock-expandContent\">" + longText + "</div>"
                + "<div class=\"bbCodeBlock-expandLink\">Click to expand...</div></div></blockquote>My answer";
        ThreadPage page = ForumParser.parseThreadPage(ForumFixtures.threadPage("T", null, ForumFixtures.post(1, "dave", 1, quote, ""), null), "https://xdaforums.com/t/t.1/");
        String text = page.posts.get(0).text;
        assertTrue(text, text.startsWith("> carol said:\n> word word"));
        assertTrue(text, text.contains("…\n\nMy answer"));
        assertFalse(text, text.contains("Click to expand"));
        assertTrue(text.length() < 400);
    }

    @Test
    public void imagesAreNotesAndEmptyPostsAreDropped() {
        String html = ForumFixtures.threadPage("T", null,
                ForumFixtures.post(1, "a", 1, "<img src=\"/x.png\" alt=\"screenshot.png\">", "")
                        + ForumFixtures.post(2, "b", 1, "   ", ""), null);
        ThreadPage page = ForumParser.parseThreadPage(html, "https://xdaforums.com/t/t.1/");
        assertEquals(1, page.posts.size());
        assertEquals("[image: screenshot.png]", page.posts.get(0).text);
    }

    @Test
    public void readsTheForumTree() {
        String html = ForumFixtures.nodePage("OnePlus",
                ForumFixtures.node("/f/oneplus-15.12971/", "OnePlus 15", "Discussion")
                        + ForumFixtures.node("/c/oneplus-7.8833/", "OnePlus 7", "")
                        + ForumFixtures.node("https://evil.example/f/x.1/", "Evil", "")
                        + ForumFixtures.node("/f/oneplus-15.12971/", "Duplicate", ""));
        NodeList list = ForumParser.parseNodes(html, "https://xdaforums.com/c/oneplus.11993/");
        assertEquals("OnePlus", list.title);
        assertFalse(list.hasThreads);
        assertEquals(2, list.nodes.size());
        assertEquals("https://xdaforums.com/f/oneplus-15.12971/", list.nodes.get(0).url);
        assertEquals("Discussion", list.nodes.get(0).description);
        assertFalse(list.nodes.get(0).category);
        assertTrue(list.nodes.get(1).category);
        assertNull(list.nodes.get(1).description);
    }

    @Test
    public void theHomePageAlsoListsBrandsFromItsMenus() {
        String menu = "<nav><a href=\"/c/oneplus.11993/\">OnePlus</a><a href=\"/c/samsung.11975/\">Samsung</a><a href=\"/c/3833/\">Android Auto</a><a href=\"/c/oneplus.11993/\">OnePlus</a></nav>";
        String html = ForumFixtures.nodePage("XDA Forums", ForumFixtures.node("/f/general-topics.256/", "General Topics", "") + menu);
        NodeList list = ForumParser.parseNodes(html, "https://xdaforums.com/");
        assertEquals(4, list.nodes.size());
        assertEquals("General Topics", list.nodes.get(0).title);
        assertEquals("Samsung", list.nodes.get(2).title);
        assertTrue(list.nodes.get(3).category);
        // Other pages do not scrape their menus.
        assertEquals(1, ForumParser.parseNodes(html, "https://xdaforums.com/c/x.1/").nodes.size());
    }

    @Test
    public void aForumPageIsFollowable() {
        String html = ForumFixtures.forumPage("OnePlus 15", ForumFixtures.threadRow(5, "t", null, "T", "a", 1, 1, "0", "1", false), null);
        assertTrue(ForumParser.parseNodes(html, ForumFixtures.BASE).hasThreads);
    }

    @Test
    public void errorPagesAreDetected() {
        assertTrue(ForumParser.isErrorPage(ForumFixtures.ERROR_PAGE));
        assertFalse(ForumParser.isErrorPage(ForumFixtures.forumPage("F", "", null)));
    }

    @Test
    public void countsAreParsedLeniently() {
        assertEquals(487, ForumParser.parseCount("487"));
        assertEquals(1234, ForumParser.parseCount("1,234"));
        assertEquals(213_000, ForumParser.parseCount("213K"));
        assertEquals(1_200_000, ForumParser.parseCount("1.2M"));
        assertEquals(0, ForumParser.parseCount("n/a"));
    }
}
