package app.bytebrief.source.rss;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import app.bytebrief.net.SourceException;

/**
 * Tolerant parser for RSS 2.0, RSS 1.0 (RDF) and Atom 1.0. External entities and DTDs are never
 * processed. A document that breaks after some entries yields those entries with
 * {@link ParsedFeed#truncated} set; a document with no feed structure at all is a parse error.
 */
public final class FeedParser {
    private static final int MAX_ENTRIES = 200;
    private static final String MEDIA_NS = "search.yahoo.com/mrss";

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, d MMM yy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
    };

    private FeedParser() {}

    public static ParsedFeed parse(String xml) throws SourceException {
        // Servers sometimes prepend a BOM or whitespace, which is illegal before the XML declaration.
        String cleaned = stripLeading(xml);
        ParsedFeed feed = new ParsedFeed();
        boolean sawFeedRoot = false;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser p = factory.newPullParser();
            p.setInput(new StringReader(cleaned));
            int event = p.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = p.getName();
                    if (p.getDepth() == 1) {
                        sawFeedRoot = isRoot(name);
                        if (!sawFeedRoot) throw SourceException.parse("Not an RSS or Atom document (root <" + name + ">)", null);
                    }
                    if (name.equals("item") || name.equals("entry")) {
                        if (feed.entries.size() < MAX_ENTRIES) feed.entries.add(readEntry(p, name));
                        else skip(p);
                    } else if (p.getDepth() <= 3) {
                        readChannelField(p, name, feed);
                    }
                }
                event = p.next();
            }
        } catch (XmlPullParserException | IOException e) {
            if (feed.entries.isEmpty() && feed.title == null) {
                throw SourceException.parse("Malformed feed XML", e);
            }
            feed.truncated = true;
        }
        if (!sawFeedRoot) throw SourceException.parse("Empty document", null);
        return feed;
    }

    private static boolean isRoot(String name) {
        return name.equals("rss") || name.equals("feed") || name.equals("RDF");
    }

    /** Reads channel/feed-level fields that appear outside entries: title and site link. */
    private static void readChannelField(XmlPullParser p, String name, ParsedFeed feed)
            throws XmlPullParserException, IOException {
        if (name.equals("title") && feed.title == null && isFeedLevel(p)) {
            feed.title = text(p);
        } else if (name.equals("link") && feed.siteUrl == null && isFeedLevel(p)) {
            String href = p.getAttributeValue(null, "href");
            if (href != null) {
                String rel = p.getAttributeValue(null, "rel");
                if (rel == null || rel.equals("alternate")) feed.siteUrl = href;
            } else {
                feed.siteUrl = text(p);
            }
        }
    }

    /** Title and link directly under channel/feed (not inside <image>, <textInput> etc.). */
    private static boolean isFeedLevel(XmlPullParser p) {
        int depth = p.getDepth();
        // rss/channel/title -> 3, feed/title -> 2, rdf:RDF/channel/title -> 3
        return depth == 2 || depth == 3;
    }

    private static ParsedFeed.Entry readEntry(XmlPullParser p, String entryTag) throws XmlPullParserException, IOException {
        ParsedFeed.Entry e = new ParsedFeed.Entry();
        int startDepth = p.getDepth();
        while (true) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) throw new XmlPullParserException("Unexpected end inside <" + entryTag + ">");
            if (ev == XmlPullParser.END_TAG && p.getDepth() == startDepth) return e;
            if (ev != XmlPullParser.START_TAG || p.getDepth() != startDepth + 1) continue;

            String name = p.getName();
            String ns = p.getNamespace();
            switch (name) {
                case "title":
                    e.title = text(p);
                    break;
                case "link": {
                    String href = p.getAttributeValue(null, "href");
                    if (href == null) {
                        String t = text(p);
                        if (e.link == null) e.link = t;
                    } else {
                        String rel = p.getAttributeValue(null, "rel");
                        String type = p.getAttributeValue(null, "type");
                        if ((rel == null || rel.equals("alternate")) && e.link == null) {
                            e.link = href;
                        } else if ("enclosure".equals(rel) && type != null && type.startsWith("image/") && e.imageUrl == null) {
                            e.imageUrl = href;
                        }
                        skip(p);
                    }
                    break;
                }
                case "guid":
                case "id":
                    e.id = text(p);
                    break;
                case "pubDate":
                case "published":
                case "issued":
                    e.publishedAt = parseDate(text(p));
                    break;
                case "date": // dc:date
                case "updated":
                case "modified":
                    if (e.publishedAt == 0) e.publishedAt = parseDate(text(p)); else skip(p);
                    break;
                case "description":
                case "summary":
                    e.summaryHtml = text(p);
                    break;
                case "encoded": // content:encoded
                    e.contentHtml = text(p);
                    break;
                case "content":
                    if (ns != null && ns.contains(MEDIA_NS)) {
                        // media:content has a url attribute and no text.
                        String medium = p.getAttributeValue(null, "medium");
                        String type = p.getAttributeValue(null, "type");
                        boolean image = "image".equals(medium) || (type != null && type.startsWith("image/"));
                        if (image && e.imageUrl == null) e.imageUrl = p.getAttributeValue(null, "url");
                        skip(p);
                    } else {
                        e.contentHtml = text(p); // Atom <content>
                    }
                    break;
                case "creator": // dc:creator
                    if (e.author == null) e.author = text(p); else skip(p);
                    break;
                case "author":
                    if (e.author == null) e.author = readAuthor(p); else skip(p);
                    break;
                case "category": {
                    String term = p.getAttributeValue(null, "term");
                    String t = term != null ? term : text(p);
                    if (term != null) skip(p);
                    if (t != null && !t.trim().isEmpty()) e.categories.add(t.trim());
                    break;
                }
                case "enclosure": {
                    String type = p.getAttributeValue(null, "type");
                    String url = p.getAttributeValue(null, "url");
                    if (url != null && type != null && type.startsWith("image/") && e.imageUrl == null) e.imageUrl = url;
                    skip(p);
                    break;
                }
                case "thumbnail":
                    if (e.imageUrl == null) e.imageUrl = p.getAttributeValue(null, "url");
                    skip(p);
                    break;
                default:
                    // media:content is also named "content" but carries a url attribute and no text.
                    skip(p);
            }
        }
    }

    /** RSS author is usually text; Atom author wraps a <name>. */
    @Nullable
    private static String readAuthor(XmlPullParser p) throws XmlPullParserException, IOException {
        int startDepth = p.getDepth();
        String name = null;
        StringBuilder direct = new StringBuilder();
        while (true) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) throw new XmlPullParserException("Unexpected end inside <author>");
            if (ev == XmlPullParser.END_TAG && p.getDepth() == startDepth) break;
            if (ev == XmlPullParser.TEXT && p.getDepth() == startDepth) direct.append(p.getText());
            if (ev == XmlPullParser.START_TAG && p.getDepth() == startDepth + 1) {
                if (p.getName().equals("name")) name = text(p); else skip(p);
            }
        }
        String s = name != null ? name : direct.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** Concatenated text of the current element (including CDATA); leaves the parser on its end tag. */
    private static String text(XmlPullParser p) throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        int startDepth = p.getDepth();
        while (true) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) throw new XmlPullParserException("Unexpected end of document");
            if (ev == XmlPullParser.END_TAG && p.getDepth() == startDepth) break;
            if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT || ev == XmlPullParser.ENTITY_REF) {
                String t = p.getText();
                if (t != null) sb.append(t);
            }
        }
        return sb.toString().trim();
    }

    private static void skip(XmlPullParser p) throws XmlPullParserException, IOException {
        int depth = 1;
        while (depth > 0) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) throw new XmlPullParserException("Unexpected end of document");
            if (ev == XmlPullParser.START_TAG) depth++;
            else if (ev == XmlPullParser.END_TAG) depth--;
        }
    }

    /** @return epoch millis or 0 when the text is not a recognized date. */
    public static long parseDate(@Nullable String raw) {
        if (raw == null) return 0;
        String s = raw.trim();
        if (s.isEmpty()) return 0;
        try {
            return OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // not ISO-8601 with offset
        }
        try {
            return java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // not a plain date
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return ZonedDateTime.parse(s, f).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return 0;
    }

    private static String stripLeading(String xml) {
        int i = 0;
        while (i < xml.length() && (xml.charAt(i) == 0xFEFF || Character.isWhitespace(xml.charAt(i)))) i++;
        return xml.substring(i);
    }
}
