package app.bytebrief.source.rss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.time.Instant;

import app.bytebrief.net.SourceException;
import app.bytebrief.testutil.Fixtures;

public class FeedParserTest {
    private static long ms(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    @Test
    public void parsesRss2WithCdataNamespacesAndMedia() throws Exception {
        ParsedFeed f = FeedParser.parse(Fixtures.resource("fixtures/rss2_sample.xml"));
        assertEquals("Example Engineering Blog", f.title);
        assertEquals("https://blog.example.com/", f.siteUrl);
        assertEquals(5, f.entries.size());
        ParsedFeed.Entry e = f.entries.get(0);
        assertEquals("Building a <b>fast</b> parser", e.title);
        assertEquals("post-7", e.id);
        assertEquals("Ada Lovelace", e.author);
        assertEquals(ms("2026-10-13T09:30:00Z"), e.publishedAt);
        assertEquals(2, e.categories.size());
        assertTrue(e.summaryHtml.contains("short teaser"));
        assertTrue(e.contentHtml.contains("Second paragraph"));
        assertEquals("https://cdn.example.com/thumb.jpg", e.imageUrl);
    }

    @Test
    public void rss2EnclosureAndOffsetDatesAndAuthorText() throws Exception {
        ParsedFeed.Entry e = FeedParser.parse(Fixtures.resource("fixtures/rss2_sample.xml")).entries.get(1);
        assertEquals("https://cdn.example.com/cover.png", e.imageUrl);
        assertEquals(ms("2026-10-13T08:00:00Z"), e.publishedAt);
        assertEquals("editor@example.com (The Editor)", e.author);
    }

    @Test
    public void unparseableDateIsZeroNotGuessed() throws Exception {
        ParsedFeed.Entry e = FeedParser.parse(Fixtures.resource("fixtures/rss2_sample.xml")).entries.get(4);
        assertEquals(0, e.publishedAt);
    }

    @Test
    public void parsesAtomLinksAuthorsCategoriesAndDates() throws Exception {
        ParsedFeed f = FeedParser.parse(Fixtures.resource("fixtures/atom_sample.xml"));
        assertEquals("Atom Journal", f.title);
        assertEquals("https://journal.example.org/", f.siteUrl);
        assertEquals(2, f.entries.size());
        ParsedFeed.Entry e = f.entries.get(0);
        assertEquals("https://journal.example.org/one", e.link);
        assertEquals("tag:journal.example.org,2026:1", e.id);
        assertEquals("Grace Hopper", e.author);
        assertEquals(ms("2026-10-14T06:15:00Z"), e.publishedAt);
        assertEquals("compilers", e.categories.get(0));
        assertEquals("https://journal.example.org/one.jpg", e.imageUrl);
        assertTrue(e.contentHtml.contains("HTML"));
        assertEquals("Short summary.", e.summaryHtml);
        assertEquals(ms("2026-10-12T00:00:00Z"), f.entries.get(1).publishedAt);
        assertEquals("https://journal.example.org/two", f.entries.get(1).link);
    }

    @Test
    public void parsesRss1Rdf() throws Exception {
        ParsedFeed f = FeedParser.parse(Fixtures.resource("fixtures/rdf_sample.xml"));
        assertEquals("RSS 1.0 Feed", f.title);
        assertEquals(1, f.entries.size());
        assertEquals("https://old.example.net/a", f.entries.get(0).link);
        assertEquals(ms("2026-10-01T00:00:00Z"), f.entries.get(0).publishedAt);
    }

    @Test
    public void toleratesBomAndLeadingWhitespace() throws Exception {
        ParsedFeed f = FeedParser.parse("﻿ \n\n" + Fixtures.resource("fixtures/rdf_sample.xml"));
        assertEquals(1, f.entries.size());
    }

    @Test
    public void truncatedDocumentKeepsRecoveredEntries() throws Exception {
        String xml = Fixtures.resource("fixtures/rss2_sample.xml");
        ParsedFeed f = FeedParser.parse(xml.substring(0, xml.indexOf("<title>Relative link post")));
        assertTrue(f.truncated);
        assertEquals(1, f.entries.size());
        assertEquals("post-7", f.entries.get(0).id);
    }

    @Test
    public void garbageAndNonFeedXmlAreParseErrors() {
        for (String bad : new String[]{"", "not xml at all", "<html><body>hi</body></html>", "<root/>", "<rss"}) {
            try {
                FeedParser.parse(bad);
                fail("expected SourceException for: " + bad);
            } catch (SourceException e) {
                assertEquals(SourceException.Kind.PARSE, e.kind);
            }
        }
    }

    @Test
    public void externalEntitiesAreNeverResolved() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<rss version=\"2.0\"><channel><title>&xxe;</title><item><title>t &xxe;</title><link>https://a.example/x</link></item></channel></rss>";
        try {
            ParsedFeed f = FeedParser.parse(xxe);
            assertFalse(String.valueOf(f.title).contains("root:"));
            for (ParsedFeed.Entry e : f.entries) assertFalse(String.valueOf(e.title).contains("root:"));
        } catch (SourceException expected) {
            // Rejecting the document is also an acceptable outcome.
        }
    }

    @Test
    public void billionLaughsStyleDocumentDoesNotExpand() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY a \"aaaaaaaaaa\">");
        for (char c = 'b'; c <= 'f'; c++) {
            char prev = (char) (c - 1);
            sb.append("<!ENTITY ").append(c).append(" \"");
            for (int i = 0; i < 10; i++) sb.append('&').append(prev).append(';');
            sb.append("\">");
        }
        sb.append("]><rss version=\"2.0\"><channel><title>&f;</title></channel></rss>");
        try {
            ParsedFeed f = FeedParser.parse(sb.toString());
            assertTrue(f.title == null || f.title.length() < 1000);
        } catch (SourceException expected) {
            // acceptable
        }
    }

    @Test
    public void dateFormats() {
        assertEquals(ms("2026-10-13T09:30:00Z"), FeedParser.parseDate("Tue, 13 Oct 2026 09:30:00 GMT"));
        assertEquals(ms("2026-10-13T09:30:00Z"), FeedParser.parseDate("Tue, 13 Oct 2026 11:30:00 +0200"));
        assertEquals(ms("2026-10-13T00:00:00Z"), FeedParser.parseDate("2026-10-13"));
        assertEquals(ms("2026-10-13T09:30:00Z"), FeedParser.parseDate("2026-10-13T09:30:00Z"));
        assertEquals(ms("2026-10-13T09:30:00Z"), FeedParser.parseDate("2026-10-13T11:30:00+02:00"));
        assertEquals(0, FeedParser.parseDate("yesterday"));
        assertEquals(0, FeedParser.parseDate(null));
        assertEquals(0, FeedParser.parseDate("  "));
    }
}
