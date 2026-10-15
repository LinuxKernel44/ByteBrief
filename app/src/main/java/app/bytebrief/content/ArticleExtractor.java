package app.bytebrief.content;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts readable plain text from an article page. It is deliberately conservative: the result is
 * text only (never markup), and when nothing article-like is found it returns an empty string so
 * the caller keeps the item marked as partially cached instead of storing page chrome.
 */
public final class ArticleExtractor {
    public static final int MAX_CHARS = 200_000;
    /** Below this the page is treated as having no extractable article (paywall, app shell, login). */
    public static final int MIN_ARTICLE_CHARS = 400;

    private static final String NOISE = "script, style, noscript, template, svg, iframe, object, embed, canvas, form, button, "
            + "nav, aside, footer, header, dialog, select, input, textarea, [role=navigation], [role=banner], "
            + "[role=complementary], [aria-hidden=true], [hidden], .advert, .ads, .ad, .sidebar, .cookie, .cookies, "
            + ".newsletter, .share, .social, .related, .comments, #comments, #sidebar";
    private static final String[] ROOT_CANDIDATES = {
            "article", "[itemprop=articleBody]", "main", "[role=main]", ".post-content", ".entry-content",
            ".article-body", ".article-content", ".post-body", "#content", ".content"};

    private ArticleExtractor() {}

    public static String extract(String html, String baseUrl) {
        if (html == null || html.isEmpty()) return "";
        Document doc = Jsoup.parse(html, baseUrl == null ? "" : baseUrl);
        doc.select(NOISE).remove();
        Element root = pickRoot(doc);
        if (root == null) return "";
        List<String> blocks = new ArrayList<>();
        collect(root, blocks);
        StringBuilder sb = new StringBuilder();
        String previous = null;
        for (String b : blocks) {
            if (b.equals(previous)) continue; // sites often repeat a lead paragraph
            previous = b;
            if (sb.length() + b.length() > MAX_CHARS) break;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(b);
        }
        String text = sb.toString().trim();
        return text.length() >= MIN_ARTICLE_CHARS ? text : "";
    }

    private static Element pickRoot(Document doc) {
        Element best = null;
        int bestScore = 0;
        for (String selector : ROOT_CANDIDATES) {
            for (Element e : doc.select(selector)) {
                int score = paragraphChars(e);
                if (score > bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            // A semantic element with real content beats generic containers.
            if (best != null && bestScore >= MIN_ARTICLE_CHARS && (selector.equals("article") || selector.equals("[itemprop=articleBody]"))) {
                return best;
            }
        }
        if (best != null && bestScore >= MIN_ARTICLE_CHARS) return best;
        // Fallback: the element whose direct paragraphs hold the most text.
        for (Element e : doc.body() == null ? new ArrayList<Element>() : doc.body().getAllElements()) {
            int score = 0;
            for (Element c : e.children()) if (c.tagName().equals("p")) score += c.text().length();
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static int paragraphChars(Element e) {
        int n = 0;
        for (Element p : e.select("p")) n += p.text().length();
        return n;
    }

    private static void collect(Element el, List<String> out) {
        for (Node node : el.childNodes()) {
            if (node instanceof TextNode) {
                String t = ((TextNode) node).text().trim();
                if (t.length() > 40) out.add(t); // loose text inside divs
                continue;
            }
            if (!(node instanceof Element)) continue;
            Element child = (Element) node;
            switch (child.tagName()) {
                case "h1": case "h2": case "h3": case "h4": case "h5": case "h6": {
                    String t = child.text().trim();
                    if (!t.isEmpty()) out.add(t);
                    break;
                }
                case "p": case "blockquote": {
                    String t = child.text().trim();
                    if (!t.isEmpty() && !isLinkFarm(child, t)) out.add(t);
                    break;
                }
                case "ul": case "ol":
                    for (Element li : child.children()) {
                        String t = li.text().trim();
                        if (!t.isEmpty() && !isLinkFarm(li, t)) out.add("- " + t);
                    }
                    break;
                case "pre": {
                    String t = child.wholeText().replaceAll("\\n{3,}", "\n\n").trim();
                    if (!t.isEmpty()) out.add(t);
                    break;
                }
                case "table":
                    break; // tables are rarely prose; skipping them avoids garbled output
                default:
                    collect(child, out);
            }
        }
    }

    /** Short blocks that are mostly links are navigation, not prose. */
    private static boolean isLinkFarm(Element e, String text) {
        if (text.length() >= 160) return false;
        int linkChars = 0;
        for (Element a : e.select("a")) linkChars += a.text().length();
        return linkChars > text.length() * 0.7;
    }
}
