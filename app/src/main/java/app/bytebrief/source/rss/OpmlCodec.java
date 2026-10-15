package app.bytebrief.source.rss;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.bytebrief.data.db.Feed;
import app.bytebrief.domain.UrlCanonicalizer;
import app.bytebrief.net.SourceException;

/** OPML 2.0 import and export for feed portability. */
public final class OpmlCodec {
    public static final class Outline {
        public final String xmlUrl;
        @Nullable public final String title;
        @Nullable public final String htmlUrl;

        Outline(String xmlUrl, @Nullable String title, @Nullable String htmlUrl) {
            this.xmlUrl = xmlUrl;
            this.title = title;
            this.htmlUrl = htmlUrl;
        }
    }

    /** Result of an import: usable feeds and how many entries were rejected (bad or unsafe URLs). */
    public static final class ImportResult {
        public final List<Outline> feeds = new ArrayList<>();
        public int rejected;
    }

    private static final int MAX_OUTLINES = 2000;

    private OpmlCodec() {}

    /** Parses OPML, flattening folders. Only http(s) feed URLs are kept (http is upgraded to https). */
    public static ImportResult parse(String opml) throws SourceException {
        ImportResult result = new ImportResult();
        Map<String, Outline> unique = new LinkedHashMap<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser p = factory.newPullParser();
            p.setInput(new StringReader(opml.replaceFirst("^[\\s\\uFEFF]+", "")));
            boolean sawOpml = false;
            int seen = 0;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev != XmlPullParser.START_TAG) continue;
                if (p.getName().equals("opml")) sawOpml = true;
                if (!p.getName().equals("outline")) continue;
                String xmlUrl = p.getAttributeValue(null, "xmlUrl");
                if (xmlUrl == null) continue; // a folder
                if (++seen > MAX_OUTLINES) break;
                String https = UrlCanonicalizer.toHttps(xmlUrl);
                if (https == null) {
                    result.rejected++;
                    continue;
                }
                String title = p.getAttributeValue(null, "title");
                if (title == null) title = p.getAttributeValue(null, "text");
                String site = p.getAttributeValue(null, "htmlUrl");
                unique.putIfAbsent(https, new Outline(https, title, UrlCanonicalizer.isHttpUrl(site) ? site : null));
            }
            if (!sawOpml) throw SourceException.parse("Not an OPML document", null);
        } catch (XmlPullParserException | IOException e) {
            throw SourceException.parse("Malformed OPML", e);
        }
        result.feeds.addAll(unique.values());
        return result;
    }

    public static String write(List<Feed> feeds, String title, String createdRfc822) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n  <head>\n    <title>")
                .append(escape(title)).append("</title>\n    <dateCreated>").append(escape(createdRfc822))
                .append("</dateCreated>\n  </head>\n  <body>\n");
        for (Feed f : feeds) {
            String name = f.title != null && !f.title.isEmpty() ? f.title : f.url;
            sb.append("    <outline type=\"rss\" text=\"").append(escape(name)).append("\" title=\"").append(escape(name))
                    .append("\" xmlUrl=\"").append(escape(f.url)).append('"');
            if (f.siteUrl != null && !f.siteUrl.isEmpty()) sb.append(" htmlUrl=\"").append(escape(f.siteUrl)).append('"');
            sb.append("/>\n");
        }
        return sb.append("  </body>\n</opml>\n").toString();
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    // Drop characters that are illegal in XML 1.0.
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') sb.append(c);
            }
        }
        return sb.toString();
    }
}
