package app.bytebrief.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.bytebrief.data.db.AiOutput;
import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.ContentListItem;
import app.bytebrief.data.db.Feed;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.FtsQuery;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.repo.Ingestor;

public class DatabaseTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private AppDatabase db;
    private ItemFactory factory;
    private Ingestor ingestor;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        factory = new ItemFactory(CLOCK);
        ingestor = new Ingestor(db, CLOCK);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private ContentItem item(SourceType type, String id, String title, String url, String body, long published, String topics) {
        ContentItem i = factory.create(type, id, title, url, "author", published, body, body,
                BodyState.FULL, topics == null ? null : Arrays.asList(topics.split(",")), "en");
        return i;
    }

    private static <T> T get(androidx.lifecycle.LiveData<T> live) {
        final Object[] box = new Object[1];
        androidx.lifecycle.Observer<T> o = v -> box[0] = v;
        live.observeForever(o);
        live.removeObserver(o);
        @SuppressWarnings("unchecked") T t = (T) box[0];
        return t;
    }

    @Test
    public void ingestIsIdempotentAndDeduplicatesBySourceId() {
        ContentItem a = item(SourceType.HACKER_NEWS, "1", "Story", "https://example.com/a", "text", 1000, "hacker-news");
        Ingestor.Stats first = ingestor.ingest(Collections.singletonList(a));
        assertEquals(1, first.inserted);
        Ingestor.Stats second = ingestor.ingest(Collections.singletonList(
                item(SourceType.HACKER_NEWS, "1", "Story", "https://example.com/a", "text", 1000, "hacker-news")));
        assertEquals(0, second.inserted);
        assertEquals(1, second.unchanged);
        assertEquals(1, db.contentDao().count());
    }

    @Test
    public void reIngestKeepsReadAndBookmarkButUpdatesScore() {
        ingestor.ingest(Collections.singletonList(item(SourceType.HACKER_NEWS, "1", "Story", "https://example.com/a", "text", 1000, null)));
        ContentItem stored = db.contentDao().findBySource("hn", "1");
        db.contentDao().setRead(stored.id, true, 1);
        db.contentDao().setBookmarked(stored.id, true, 1);

        ContentItem updated = item(SourceType.HACKER_NEWS, "1", "Story", "https://example.com/a", "text", 1000, null);
        updated.score = 250;
        Ingestor.Stats s = ingestor.ingest(Collections.singletonList(updated));
        assertEquals(1, s.updated);
        ContentItem after = db.contentDao().findBySource("hn", "1");
        assertTrue(after.isRead);
        assertTrue(after.isBookmarked);
        assertEquals(250, after.score);
        assertEquals(stored.uid, after.uid);
    }

    @Test
    public void sameSubjectFromTwoSourcesIsGroupedWithBothLinksPreserved() {
        ingestor.ingest(Arrays.asList(
                item(SourceType.GITHUB_TRENDING, "o/r", "o/r", "https://github.com/O/R", "readme", 1000, "java"),
                item(SourceType.HACKER_NEWS, "7", "Show HN: R", "https://github.com/o/r?utm_source=hn", "t", 2000, "show-hn"),
                item(SourceType.RSS, "1:x", "Unrelated", "https://other.example/post", "t", 3000, null)));
        assertEquals(3, db.contentDao().count());
        List<ContentListItem> list = get(db.contentDao().observeList(null, null, null, 0, -1, 0, 50));
        assertEquals(2, list.size());
        ContentListItem grouped = null;
        for (ContentListItem l : list) if (l.groupSize == 2) grouped = l;
        assertNotNull(grouped);
        assertEquals("gh_trending", grouped.item.sourceType); // first seen is the representative
        assertEquals(2, db.contentDao().getGroup(grouped.item.dedupKey).size());
    }

    @Test
    public void releasesAreNotGroupedWithTheirRepository() {
        ingestor.ingest(Arrays.asList(
                item(SourceType.GITHUB_TRENDING, "o/r", "o/r", "https://github.com/o/r", "readme", 1000, null),
                item(SourceType.GITHUB_RELEASE, "55", "o/r v1", "https://github.com/o/r/releases/tag/v1", "notes", 2000, "release")));
        assertEquals(2, get(db.contentDao().observeList(null, null, null, 0, -1, 0, 50)).size());
    }

    @Test
    public void fullTextSearchFindsTitleBodyAndRespectsFilters() {
        ingestor.ingest(Arrays.asList(
                item(SourceType.RSS, "1:a", "Rust async runtime", "https://a.example/1", "tokio internals", 3000, "rust"),
                item(SourceType.RSS, "1:b", "Gardening tips", "https://a.example/2", "about tomatoes and rust spots", 2000, "garden"),
                item(SourceType.HACKER_NEWS, "9", "Kotlin coroutines", "https://a.example/3", "structured concurrency", 1000, "kotlin")));
        String q = FtsQuery.build("rust");
        List<ContentListItem> r = get(db.contentDao().search(q, null, null, null, 0, -1, 0, 50));
        assertEquals(2, r.size());
        assertEquals("Rust async runtime", r.get(0).item.title); // newest first

        assertEquals(1, get(db.contentDao().search(q, null, null, "rust", 0, -1, 0, 50)).size());
        assertEquals(1, get(db.contentDao().search(FtsQuery.build("coroutine"), "hn", null, null, 0, -1, 0, 50)).size());
        assertEquals(0, get(db.contentDao().search(FtsQuery.build("coroutine"), "rss", null, null, 0, -1, 0, 50)).size());
        assertEquals(1, get(db.contentDao().search(FtsQuery.build("tomatoes"), null, "en", null, 0, -1, 0, 50)).size());
        assertEquals(0, get(db.contentDao().search(FtsQuery.build("tomatoes"), null, "fr", null, 0, -1, 0, 50)).size());
        assertEquals(1, get(db.contentDao().search(q, null, null, null, 2500, -1, 0, 50)).size()); // date filter
    }

    @Test
    public void searchIndexFollowsUpdatesAndDeletes() {
        ingestor.ingest(Collections.singletonList(item(SourceType.RSS, "1:a", "Old title", "https://a.example/1", "alpha", 1000, null)));
        ContentItem stored = db.contentDao().findBySource("rss", "1:a");
        assertEquals(1, get(db.contentDao().search(FtsQuery.build("alpha"), null, null, null, 0, -1, 0, 10)).size());
        ingestor.ingest(Collections.singletonList(item(SourceType.RSS, "1:a", "Old title", "https://a.example/1", "beta", 1000, null)));
        assertEquals(0, get(db.contentDao().search(FtsQuery.build("alpha"), null, null, null, 0, -1, 0, 10)).size());
        assertEquals(1, get(db.contentDao().search(FtsQuery.build("beta"), null, null, null, 0, -1, 0, 10)).size());
        db.contentDao().deleteOlderThan(Long.MAX_VALUE);
        assertEquals(0, get(db.contentDao().search(FtsQuery.build("beta"), null, null, null, 0, -1, 0, 10)).size());
        assertNull(db.contentDao().getById(stored.id));
    }

    @Test
    public void readAndBookmarkFilters() {
        ingestor.ingest(Arrays.asList(
                item(SourceType.RSS, "1:a", "A", "https://a.example/1", "x", 3000, null),
                item(SourceType.RSS, "1:b", "B", "https://a.example/2", "x", 2000, null)));
        ContentItem a = db.contentDao().findBySource("rss", "1:a");
        db.contentDao().setRead(a.id, true, 1);
        db.contentDao().setBookmarked(a.id, true, 1);
        assertEquals(1, get(db.contentDao().observeList(null, null, null, 0, 0, 0, 50)).size());   // unread
        assertEquals(1, get(db.contentDao().observeList(null, null, null, 0, 1, 0, 50)).size());   // read
        assertEquals(1, get(db.contentDao().observeList(null, null, null, 0, -1, 1, 50)).size());  // bookmarked
    }

    @Test
    public void retentionKeepsBookmarkedItems() {
        ingestor.ingest(Arrays.asList(
                item(SourceType.RSS, "1:old", "Old", "https://a.example/o", "x", 100, null),
                item(SourceType.RSS, "1:keep", "Keep", "https://a.example/k", "x", 100, null)));
        db.contentDao().setBookmarked(db.contentDao().findBySource("rss", "1:keep").id, true, 1);
        assertEquals(1, db.contentDao().deleteOlderThan(1000));
        assertEquals(1, db.contentDao().count());
    }

    @Test
    public void aiOutputsCascadeWithTheirItem() {
        ingestor.ingest(Collections.singletonList(item(SourceType.RSS, "1:a", "A", "https://a.example/1", "x", 1000, null)));
        ContentItem a = db.contentDao().findBySource("rss", "1:a");
        AiOutput o = new AiOutput();
        o.itemId = a.id;
        o.kind = AiOutput.KIND_SUMMARY;
        o.json = "{}";
        o.contentHash = a.contentHash;
        o.modelId = "m";
        db.aiOutputDao().upsert(o);
        assertNotNull(db.aiOutputDao().find(a.id, AiOutput.KIND_SUMMARY));
        db.contentDao().deleteOlderThan(Long.MAX_VALUE);
        assertNull(db.aiOutputDao().find(a.id, AiOutput.KIND_SUMMARY));
    }

    @Test
    public void feedUrlsAreUnique() {
        Feed f = new Feed();
        f.url = "https://a.example/feed";
        assertTrue(db.feedDao().insertIgnore(f) > 0);
        Feed dup = new Feed();
        dup.url = "https://a.example/feed";
        assertEquals(-1, db.feedDao().insertIgnore(dup));
    }
}
