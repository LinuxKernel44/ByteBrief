package app.bytebrief.source.rss;

import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.Feed;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.ContentHasher;
import app.bytebrief.domain.HtmlText;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.domain.UrlCanonicalizer;
import app.bytebrief.net.FetchResult;
import app.bytebrief.net.HttpFetcher;
import app.bytebrief.net.SourceException;
import okhttp3.HttpUrl;

/** Fetches, validates and maps RSS/Atom feeds. */
public class FeedSource {
    /** A feed that was fetched and parsed successfully. */
    public static final class Validated {
        public final String feedUrl;
        @Nullable public final String title;
        @Nullable public final String siteUrl;
        public final ParsedFeed parsed;
        @Nullable public final String etag;
        @Nullable public final String lastModified;

        Validated(String feedUrl, ParsedFeed parsed, @Nullable String etag, @Nullable String lastModified) {
            this.feedUrl = feedUrl;
            this.title = parsed.title;
            this.siteUrl = parsed.siteUrl;
            this.parsed = parsed;
            this.etag = etag;
            this.lastModified = lastModified;
        }
    }

    public static final class Refresh {
        public boolean notModified;
        public final List<ContentItem> items = new ArrayList<>();
        @Nullable public String title;
        @Nullable public String siteUrl;
        @Nullable public String etag;
        @Nullable public String lastModified;
        public boolean truncated;
    }

    private static final long MAX_FEED_BYTES = 3L * 1024 * 1024;

    private final HttpFetcher http;
    private final ItemFactory factory;
    private final boolean allowCleartext;

    /** Production configuration: user-entered http URLs are upgraded to https. */
    public FeedSource(HttpFetcher http, ItemFactory factory) {
        this(http, factory, false);
    }

    /** @param allowCleartext keep http URLs as typed instead of upgrading them (tests, local servers) */
    public FeedSource(HttpFetcher http, ItemFactory factory, boolean allowCleartext) {
        this.http = http;
        this.factory = factory;
        this.allowCleartext = allowCleartext;
    }

    /**
     * Checks that {@code rawUrl} is a feed. If it is an HTML page that advertises a feed, that feed is
     * used instead (one hop only).
     */
    public Validated validate(String rawUrl) throws SourceException {
        String url = allowCleartext && UrlCanonicalizer.isHttpUrl(rawUrl) ? rawUrl.trim() : UrlCanonicalizer.toHttps(rawUrl);
        if (url == null) throw SourceException.invalidUrl("Feed URLs must start with http:// or https://");
        FetchResult r = http.get(url, null, MAX_FEED_BYTES);
        if (looksLikeHtml(r.body)) {
            String discovered = discover(r.body, r.finalUrl);
            if (discovered == null) throw SourceException.parse("This page does not advertise an RSS or Atom feed", null);
            url = discovered;
            r = http.get(url, null, MAX_FEED_BYTES);
        }
        ParsedFeed parsed = FeedParser.parse(r.body);
        return new Validated(r.finalUrl, parsed, r.etag, r.lastModified);
    }

    /** Conditional refresh of a stored feed. */
    public Refresh refresh(Feed feed) throws SourceException {
        Map<String, String> headers = new HashMap<>();
        if (feed.etag != null) headers.put("If-None-Match", feed.etag);
        if (feed.lastModified != null) headers.put("If-Modified-Since", feed.lastModified);
        FetchResult r = http.get(feed.url, headers, MAX_FEED_BYTES);
        Refresh out = new Refresh();
        if (r.notModified()) {
            out.notModified = true;
            return out;
        }
        ParsedFeed parsed = FeedParser.parse(r.body);
        out.title = parsed.title;
        out.siteUrl = parsed.siteUrl;
        out.etag = r.etag;
        out.lastModified = r.lastModified;
        out.truncated = parsed.truncated;
        out.items.addAll(toItems(feed, parsed, r.finalUrl));
        return out;
    }

    List<ContentItem> toItems(Feed feed, ParsedFeed parsed, String baseUrl) {
        HttpUrl base = HttpUrl.parse(baseUrl);
        String label = feed.title != null ? feed.title : parsed.title;
        List<ContentItem> items = new ArrayList<>();
        for (ParsedFeed.Entry e : parsed.entries) {
            String link = resolve(base, e.link);
            if (link == null && e.id != null && UrlCanonicalizer.isHttpUrl(e.id)) link = e.id;
            if (link == null) continue;

            String contentText = HtmlText.toPlainText(e.contentHtml);
            String summaryText = HtmlText.toPlainText(e.summaryHtml);
            String full = !contentText.isEmpty() ? contentText : summaryText;
            String idPart = e.id != null && !e.id.isEmpty() ? e.id : link;
            if (idPart.length() > 300) idPart = ContentHasher.sha256Hex(idPart);

            boolean hasContent = !contentText.isEmpty();
            ContentItem item = factory.create(SourceType.RSS, feed.id + ":" + idPart,
                    e.title == null || e.title.isEmpty() ? link : HtmlText.toPlainText(e.title), link, e.author,
                    e.publishedAt, full.isEmpty() ? null : HtmlText.truncate(full, 500),
                    // Feed content is the full entry body; a bare summary is only a snippet.
                    full.length() > 500 || hasContent ? full : null,
                    hasContent ? BodyState.FULL : BodyState.SNIPPET, e.categories, null);
            if (item == null) continue;
            item.feedId = feed.id;
            item.sourceLabel = label;
            item.imageUrl = imageFor(base, e);
            items.add(item);
        }
        return items;
    }

    @Nullable
    private static String imageFor(@Nullable HttpUrl base, ParsedFeed.Entry e) {
        String img = resolve(base, e.imageUrl);
        if (img != null) return img;
        String html = e.contentHtml != null ? e.contentHtml : e.summaryHtml;
        if (html == null) return null;
        Element first = Jsoup.parse(html).selectFirst("img[src]");
        return first == null ? null : resolve(base, first.attr("src"));
    }

    @Nullable
    private static String resolve(@Nullable HttpUrl base, @Nullable String ref) {
        if (ref == null || ref.trim().isEmpty()) return null;
        String trimmed = ref.trim();
        HttpUrl resolved = base != null ? base.resolve(trimmed) : HttpUrl.parse(trimmed);
        return resolved == null ? null : UrlCanonicalizer.isHttpUrl(resolved.toString()) ? resolved.toString() : null;
    }

    static boolean looksLikeHtml(@Nullable String body) {
        if (body == null) return false;
        String head = body.substring(0, Math.min(body.length(), 1024)).toLowerCase();
        int xml = head.indexOf("<rss");
        int atom = head.indexOf("<feed");
        int rdf = head.indexOf("<rdf:rdf");
        if (xml >= 0 || atom >= 0 || rdf >= 0) return false;
        return head.contains("<!doctype html") || head.contains("<html");
    }

    @Nullable
    static String discover(String html, String pageUrl) {
        Document doc = Jsoup.parse(html, pageUrl);
        for (Element link : doc.select("link[rel~=(?i)alternate][href]")) {
            String type = link.attr("type").toLowerCase();
            if (type.contains("rss") || type.contains("atom")) {
                String abs = link.absUrl("href");
                if (UrlCanonicalizer.isHttpUrl(abs)) return abs;
            }
        }
        return null;
    }
}
