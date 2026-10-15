package app.bytebrief.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import app.bytebrief.data.db.ContentItem;

public class ArticleExtractorTest {
    private static final String PARA = "This paragraph contains enough prose to look like a real article body and to survive the minimum length filter. ";

    private static String page(String body) {
        return "<!doctype html><html><head><title>t</title><script>var leak='SCRIPT-LEAK';</script><style>.x{}</style></head><body>" + body + "</body></html>";
    }

    @Test
    public void extractsArticleAndDropsChrome() {
        String html = page("<header><a href='/'>Home</a><a href='/about'>About</a></header><nav><ul><li><a href='/a'>Menu item</a></li></ul></nav>"
                + "<article><h1>Real Title</h1><p>" + PARA.repeat(2) + "</p><h2>Section</h2><p>" + PARA.repeat(2) + "</p>"
                + "<ul><li>First point is a full sentence of reasonable length.</li><li>Second point also reads like prose text.</li></ul>"
                + "<pre>fn main() {\n    println!(\"hi\");\n}</pre></article>"
                + "<aside>Subscribe to our newsletter!</aside><footer>Copyright</footer><div class='comments'>Great post!</div>");
        String text = ArticleExtractor.extract(html, "https://example.com/post");
        assertTrue(text.startsWith("Real Title\n\nThis paragraph"));
        assertTrue(text.contains("Section"));
        assertTrue(text.contains("- First point"));
        assertTrue(text.contains("println!(\"hi\");"));
        for (String noise : new String[]{"Menu item", "newsletter", "Copyright", "Great post", "SCRIPT-LEAK", "Home"}) {
            assertFalse("leaked: " + noise, text.contains(noise));
        }
    }

    @Test
    public void worksWithoutSemanticTags() {
        String html = page("<div id='wrap'><div class='menu'><a href='/1'>One</a> <a href='/2'>Two</a></div>"
                + "<div class='story'><p>" + PARA.repeat(3) + "</p><p>" + PARA.repeat(2) + "</p></div></div>");
        String text = ArticleExtractor.extract(html, "https://example.com");
        assertTrue(text.contains("This paragraph contains"));
        assertFalse(text.contains("One Two"));
    }

    @Test
    public void thinPagesYieldNothingSoTheItemStaysPartiallyCached() {
        assertEquals("", ArticleExtractor.extract(page("<article><p>Please enable JavaScript.</p></article>"), "https://e.com"));
        assertEquals("", ArticleExtractor.extract(page("<div id='root'></div>"), "https://e.com"));
        assertEquals("", ArticleExtractor.extract("", "https://e.com"));
        assertEquals("", ArticleExtractor.extract(null, "https://e.com"));
    }

    @Test
    public void outputIsPlainTextWithNoMarkupOrEntities() {
        String text = ArticleExtractor.extract(page("<article><p>" + PARA.repeat(4) + " Fish &amp; chips &lt;b&gt;bold&lt;/b&gt;</p></article>"), "https://e.com");
        assertTrue(text.contains("Fish & chips <b>bold</b>")); // decoded text, never live markup
        assertFalse(text.contains("&amp;"));
    }

    @Test
    public void duplicateConsecutiveParagraphsAreCollapsed() {
        String p = "<p>UNIQUE-LEAD-123 " + PARA.repeat(3) + "</p>";
        String text = ArticleExtractor.extract(page("<article>" + p + p + "<p>Different ending paragraph that is also reasonably long to be kept as is in output.</p></article>"), "https://e.com");
        assertEquals(1, text.split("UNIQUE-LEAD-123", -1).length - 1);
    }

    @Test
    public void hugePagesAreCapped() {
        StringBuilder body = new StringBuilder("<article>");
        for (int i = 0; i < 5000; i++) body.append("<p>").append(PARA).append(i).append("</p>");
        String text = ArticleExtractor.extract(page(body.append("</article>").toString()), "https://e.com");
        assertTrue(text.length() <= ArticleExtractor.MAX_CHARS);
        assertTrue(text.length() > 100_000);
    }

    @Test
    public void metadataFormatterReportsOnlyWhatTheSourceProvided() {
        ContentItem hn = new ContentItem();
        hn.sourceType = "hn";
        hn.score = 1234;
        hn.commentCount = 56;
        hn.author = "pg";
        assertEquals("Hacker News points: 1,234\nHacker News comments: 56\nAuthor: pg", MetadataFormatter.format(hn));

        ContentItem repo = new ContentItem();
        repo.sourceType = "gh_trending";
        repo.score = 255645;
        repo.metadataJson = "{\"forks\":64229,\"stars_in_period\":41034,\"period\":\"daily\",\"programming_language\":\"TypeScript\",\"license\":\"MIT\"}";
        String f = MetadataFormatter.format(repo);
        assertTrue(f.contains("GitHub stars (total, as listed by GitHub): 255,645"));
        assertTrue(f.contains("Stars gained today (GitHub Trending): 41,034"));
        assertTrue(f.contains("License: MIT"));
        assertFalse(f.contains("Last pushed"));

        ContentItem rss = new ContentItem();
        rss.sourceType = "rss";
        rss.metadataJson = "{broken";
        assertEquals("", MetadataFormatter.format(rss));
    }
}
