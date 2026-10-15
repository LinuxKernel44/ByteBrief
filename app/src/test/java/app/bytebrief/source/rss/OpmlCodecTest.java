package app.bytebrief.source.rss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import app.bytebrief.data.db.Feed;
import app.bytebrief.net.SourceException;
import app.bytebrief.testutil.Fixtures;

public class OpmlCodecTest {
    @Test
    public void importFlattensFoldersUpgradesHttpDedupesAndRejectsUnsafeUrls() throws Exception {
        OpmlCodec.ImportResult r = OpmlCodec.parse(Fixtures.resource("fixtures/opml_sample.opml"));
        assertEquals(2, r.feeds.size());
        assertEquals("https://a.example.com/feed", r.feeds.get(0).xmlUrl);
        assertEquals("Blog A", r.feeds.get(0).title);
        assertEquals("https://a.example.com", r.feeds.get(0).htmlUrl);
        assertEquals("Blog B & Co", r.feeds.get(1).title);
        assertEquals(2, r.rejected);
    }

    @Test
    public void nonOpmlIsRejected() {
        for (String bad : new String[]{"<rss version='2.0'/>", "garbage", ""}) {
            try {
                OpmlCodec.parse(bad);
                fail("expected failure for " + bad);
            } catch (SourceException e) {
                assertEquals(SourceException.Kind.PARSE, e.kind);
            }
        }
    }

    @Test
    public void exportRoundTripsAndEscapes() throws Exception {
        List<Feed> feeds = new ArrayList<>();
        Feed a = new Feed();
        a.url = "https://a.example.com/feed?x=1&y=2";
        a.title = "Tom & \"Jerry\" <news>";
        a.siteUrl = "https://a.example.com";
        feeds.add(a);
        Feed b = new Feed();
        b.url = "https://b.example.com/rss";
        feeds.add(b);
        String opml = OpmlCodec.write(feeds, "ByteBrief feeds", "Thu, 15 Oct 2026 12:00:00 GMT");
        assertTrue(opml.contains("&amp;y=2"));
        assertFalse(opml.contains("<news>"));

        OpmlCodec.ImportResult back = OpmlCodec.parse(opml);
        assertEquals(2, back.feeds.size());
        assertEquals(a.url, back.feeds.get(0).xmlUrl);
        assertEquals(a.title, back.feeds.get(0).title);
        assertEquals(b.url, back.feeds.get(1).title);
        assertEquals(0, back.rejected);
    }

    @Test
    public void illegalXmlCharactersAreDroppedOnExport() throws Exception {
        Feed f = new Feed();
        f.url = "https://c.example.com/f";
        f.title = "bad\u0001title";
        OpmlCodec.ImportResult back = OpmlCodec.parse(OpmlCodec.write(java.util.Collections.singletonList(f), "t", "d"));
        assertEquals("badtitle", back.feeds.get(0).title);
        assertNull(back.feeds.get(0).htmlUrl);
    }
}
