package app.bytebrief.source.forum;

import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import app.bytebrief.source.forum.ForumPageModels.Node;
import app.bytebrief.source.forum.ForumPageModels.NodeList;
import app.bytebrief.source.forum.ForumPageModels.Post;
import app.bytebrief.source.forum.ForumPageModels.ThreadList;
import app.bytebrief.source.forum.ForumPageModels.ThreadPage;
import app.bytebrief.source.forum.ForumPageModels.ThreadSummary;

/**
 * Reads XenForo 2 pages (the engine behind XDA Forums). The markup is untrusted: only text and links
 * on the forum's own host are taken from it, and a page that does not look like what was asked for
 * yields empty results rather than guesses.
 */
public final class ForumParser {
    private ForumParser() {}

    public static boolean isErrorPage(String html) {
        Document doc = Jsoup.parse(html);
        return "error".equals(doc.select("html").attr("data-template"));
    }

    public static NodeList parseNodes(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        NodeList out = new NodeList();
        out.title = pageTitle(doc);
        out.hasThreads = !doc.select(".structItem--thread").isEmpty();
        Set<String> seen = new LinkedHashSet<>();
        for (Element n : doc.select(".node")) {
            Element a = n.selectFirst(".node-title a[href]");
            if (a == null) continue;
            String url = ForumUrls.resolve(baseUrl, a.attr("href"));
            String norm = url == null ? null : ForumUrls.normalizeNode(url);
            String title = a.text().trim();
            if (norm == null || title.isEmpty() || !seen.add(norm)) continue;
            Node node = new Node();
            node.title = title;
            node.url = norm;
            node.category = ForumUrls.isCategory(norm);
            Element d = n.selectFirst(".node-description");
            node.description = d == null || d.text().trim().isEmpty() ? null : d.text().trim();
            out.nodes.add(node);
        }
        if (ForumUrls.HOME.equals(ForumUrls.normalizeNode(baseUrl))) {
            // The home page lists a few popular forums; the device brands are only in its menus.
            for (Element a : doc.select("a[href^=/c/]")) {
                String url = ForumUrls.resolve(baseUrl, a.attr("href"));
                String norm = url == null ? null : ForumUrls.normalizeNode(url);
                String title = a.text().trim();
                if (norm == null || title.isEmpty() || !seen.add(norm)) continue;
                Node node = new Node();
                node.title = title;
                node.url = norm;
                node.category = true;
                out.nodes.add(node);
            }
        }
        return out;
    }

    public static ThreadList parseThreadList(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        ThreadList out = new ThreadList();
        out.title = pageTitle(doc);
        for (Element row : doc.select(".structItem--thread")) {
            Element a = row.selectFirst(".structItem-title a[data-tp-primary], .structItem-title a[href*=/t/], .structItem-title a[href*=/threads/]");
            if (a == null) continue;
            String url = ForumUrls.resolve(baseUrl, a.attr("href"));
            long id = ForumUrls.threadId(url);
            String title = a.text().trim();
            if (url == null || id < 0 || title.isEmpty()) continue;
            ThreadSummary t = new ThreadSummary();
            t.threadId = id;
            t.title = title;
            t.url = url;
            t.author = emptyToNull(row.attr("data-author"));
            Element label = row.selectFirst(".structItem-title .label");
            t.prefix = label == null ? null : emptyToNull(label.text().trim());
            t.sticky = row.selectFirst(".structItem-status--sticky") != null;
            Element start = row.selectFirst(".structItem-startDate time");
            t.startedAt = epochMillis(start);
            Element latest = row.selectFirst(".structItem-latestDate");
            t.lastPostAt = latest != null ? epochMillis(latest) : t.startedAt;
            if (t.startedAt == 0) t.startedAt = t.lastPostAt;
            for (Element dl : row.select(".structItem-cell--meta dl")) {
                String key = dl.selectFirst("dt") == null ? "" : dl.selectFirst("dt").text().trim().toLowerCase(Locale.ROOT);
                Element dd = dl.selectFirst("dd");
                if (dd == null) continue;
                if (key.startsWith("repl")) t.replies = (int) Math.min(Integer.MAX_VALUE, parseCount(dd.text()));
                else if (key.startsWith("view")) t.views = parseCount(dd.text());
            }
            out.threads.add(t);
        }
        out.nextUrl = nextPage(doc, baseUrl);
        return out;
    }

    public static ThreadPage parseThreadPage(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        ThreadPage out = new ThreadPage();
        out.title = pageTitle(doc);
        for (Element art : doc.select("article.message--post")) {
            Element body = art.selectFirst(".message-body .bbWrapper");
            if (body == null) continue;
            Post p = new Post();
            String idAttr = art.attr("data-content").replace("post-", "");
            try {
                p.id = Long.parseLong(idAttr);
            } catch (NumberFormatException e) {
                p.id = 0;
            }
            p.author = emptyToNull(art.attr("data-author"));
            Element time = art.selectFirst(".message-attribution time, time.u-dt");
            p.postedAt = epochMillis(time);
            p.text = PostText.render(body);
            for (Element f : art.select(".message-attachments .file-name")) {
                String name = f.text().trim();
                if (!name.isEmpty()) p.attachments.add(name);
            }
            if (!p.text.isEmpty() || !p.attachments.isEmpty()) out.posts.add(p);
        }
        out.nextUrl = nextPage(doc, baseUrl);
        return out;
    }

    @Nullable
    private static String nextPage(Document doc, String baseUrl) {
        Element next = doc.selectFirst("a.pageNav-jump--next[href]");
        return next == null ? null : ForumUrls.resolve(baseUrl, next.attr("href"));
    }

    @Nullable
    private static String pageTitle(Document doc) {
        Element h1 = doc.selectFirst("h1.p-title-value");
        if (h1 != null) {
            Element copy = h1.clone();
            copy.select(".label, .labelLink").remove(); // the thread prefix is not part of the title
            if (!copy.text().trim().isEmpty()) return copy.text().trim();
        }
        String t = doc.title();
        int bar = t.lastIndexOf(" | ");
        if (bar > 0) t = t.substring(0, bar);
        return emptyToNull(t.trim());
    }

    private static long epochMillis(@Nullable Element time) {
        if (time == null) return 0;
        try {
            return Long.parseLong(time.attr("data-time").trim()) * 1000L;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Reads counts such as "487", "1,234", "213K" or "1.2M". */
    static long parseCount(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(",", "");
        double mult = 1;
        if (s.endsWith("K")) { mult = 1_000; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("M")) { mult = 1_000_000; s = s.substring(0, s.length() - 1); }
        try {
            return Math.round(Double.parseDouble(s.trim()) * mult);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
