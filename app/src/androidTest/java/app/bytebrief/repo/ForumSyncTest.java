package app.bytebrief.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.Forum;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.net.PageFetcher;
import app.bytebrief.net.SourceException;
import app.bytebrief.settings.SyncConfig;
import app.bytebrief.source.forum.ForumSource;
import app.bytebrief.source.github.TrendingSource;
import app.bytebrief.source.hn.HackerNewsSource;

/** Forum synchronization against a real Room database and a canned page loader. */
public class ForumSyncTest {
    private static final long NOW = Instant.parse("2026-10-18T12:00:00Z").toEpochMilli();
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private static final String URL = "https://xdaforums.com/f/oneplus-15.12971/";

    private AppDatabase db;
    private SyncManager sync;
    private final AtomicBoolean metered = new AtomicBoolean(false);
    private final List<String> loads = new ArrayList<>();
    private volatile String html;
    private volatile SourceException failure;
    private boolean wifiOnly = true;

    private final PageFetcher fetcher = url -> {
        loads.add(url);
        if (failure != null) throw failure;
        return new PageFetcher.Page(html, url);
    };

    private static String row(long id, String title, long startedSeconds) {
        return "<div class=\"structItem structItem--thread\" data-author=\"koaaN\"><div class=\"structItem-cell structItem-cell--main\">"
                + "<div class=\"structItem-title\"><a href=\"/t/t-" + id + "." + id + "/\" data-tp-primary=\"on\">" + title + "</a></div>"
                + "<div class=\"structItem-minor\"><ul><li class=\"structItem-startDate\"><time class=\"u-dt\" data-time=\"" + startedSeconds + "\">x</time></li></ul></div></div>"
                + "<div class=\"structItem-cell structItem-cell--meta\"><dl><dt>Replies</dt><dd>3</dd></dl><dl><dt>Views</dt><dd>90</dd></dl></div>"
                + "<div class=\"structItem-cell structItem-cell--latest\"><time class=\"structItem-latestDate u-dt\" data-time=\"" + startedSeconds + "\">x</time></div></div>";
    }

    private static String page(String... rows) {
        return "<html id=\"XF\" data-app=\"public\" data-template=\"forum_view\"><head><title>OnePlus 15 | XDA Forums</title></head><body><div class=\"p-body\">"
                + "<h1 class=\"p-title-value\">OnePlus 15</h1>" + String.join("", rows) + "</div></body></html>";
    }

    private Forum addForum(long lastFetchedAt) {
        Forum f = new Forum();
        f.url = URL;
        f.title = "OnePlus 15";
        f.lastFetchedAt = lastFetchedAt;
        f.id = db.forumDao().insertIgnore(f);
        return f;
    }

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        SyncConfig config = new SyncConfig() {
            @Override public boolean isSourceEnabled(SourceType t) { return t == SourceType.XDA_FORUM; }
            @Override public Set<HackerNewsSource.Feed> hackerNewsFeeds() { return EnumSet.of(HackerNewsSource.Feed.TOP); }
            @Override public int hackerNewsLimit() { return 10; }
            @Override public TrendingSource.Period trendingPeriod() { return TrendingSource.Period.DAILY; }
            @Override public String trendingLanguage() { return null; }
            @Override public boolean includePrereleases() { return false; }
            @Override public int retentionDays() { return 36500; }
            @Override public int trendingEnrichBudget() { return 0; }
            @Override public boolean forumsWifiOnly() { return wifiOnly; }
        };
        sync = new SyncManager(db, new Ingestor(db, CLOCK), null, null, null, null, config, CLOCK);
        sync.setForumSupport(new ForumSource(fetcher, new ItemFactory(CLOCK)), metered::get);
        html = page(row(1, "Old thread", NOW / 1000 - 30 * 86_400));
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void aNewForumIsFetchedAndItsThreadsStored() {
        Forum f = addForum(0);
        SyncReport r = sync.syncAll();
        assertEquals(SyncReport.Status.OK, r.outcomes.get("xda").status);
        assertEquals(1, r.outcomes.get("xda").stats.inserted);
        assertTrue("no alert for a forum's first load", r.newForumThreads.isEmpty());
        Forum after = db.forumDao().getById(f.id);
        assertEquals(NOW, after.lastFetchedAt);
        assertNull(after.lastError);
        ContentItem item = db.contentDao().findBySource("xda", f.id + ":1");
        assertNotNull(item);
        assertEquals(Long.valueOf(f.id), item.feedId);
    }

    @Test
    public void aRecentlyCheckedForumIsLeftAloneUnlessTheUserAsks() {
        addForum(NOW - 60 * 60 * 1000); // one hour ago
        SyncReport r = sync.syncAll();
        assertTrue(loads.isEmpty());
        assertEquals(0, r.totalNew());
        sync.syncAll(true);
        assertEquals(1, loads.size());
    }

    @Test
    public void newRecentThreadsRaiseAnAlertAndOldOnesDoNot() {
        addForum(NOW - 5 * 3_600_000L);
        html = page(row(2, "Brand new ROM", NOW / 1000 - 3600), row(3, "Old sticky resurfacing", NOW / 1000 - 400 * 86_400));
        SyncReport r = sync.syncAll();
        assertEquals(List.of("Brand new ROM"), r.newForumThreads);
        assertEquals(2, r.outcomes.get("xda").stats.inserted);
        // The same page again is not news.
        sync.syncAll(true);
        assertEquals(2, db.contentDao().count());
    }

    @Test
    public void alertsCanBeSwitchedOffPerForum() {
        Forum f = addForum(NOW - 5 * 3_600_000L);
        f.notifyNewThreads = false;
        db.forumDao().update(f);
        html = page(row(2, "Brand new ROM", NOW / 1000 - 3600));
        SyncReport r = sync.syncAll();
        assertTrue(r.newForumThreads.isEmpty());
        assertEquals(1, r.outcomes.get("xda").stats.inserted);
    }

    @Test
    public void verificationMarksTheForumAndStopsTheRun() {
        addForum(NOW - 5 * 3_600_000L);
        Forum second = new Forum();
        second.url = "https://xdaforums.com/f/oneplus-15r.12990/";
        second.lastFetchedAt = NOW; // fresh, so only the first forum is due
        db.forumDao().insertIgnore(second);
        failure = SourceException.verificationRequired("verify");

        SyncReport r = sync.syncAll();
        assertEquals(SyncReport.Status.FAILED, r.outcomes.get("xda").status);
        assertEquals(1, loads.size());
        assertTrue(db.forumDao().getAll().get(0).needsVerification || db.forumDao().getAll().get(1).needsVerification);

        // A forum that waits for verification is not retried in the background, but is by a manual refresh.
        loads.clear();
        sync.syncAll();
        assertTrue(loads.isEmpty());
        failure = null;
        sync.syncForum(db.forumDao().getAll().stream().filter(x -> x.needsVerification).findFirst().get().id);
        assertEquals(1, loads.size());
        assertFalse(db.forumDao().getAll().stream().anyMatch(x -> x.needsVerification));
    }

    @Test
    public void metered_networkSkipsForumsWhenWifiOnlyIsOn() {
        addForum(0);
        metered.set(true);
        SyncReport r = sync.syncAll();
        assertEquals(SyncReport.Status.SKIPPED, r.outcomes.get("xda").status);
        assertTrue(loads.isEmpty());
        // A manual run ignores the preference.
        sync.syncAll(true);
        assertEquals(1, loads.size());
        wifiOnly = false;
        loads.clear();
        sync.syncAll(false);
        assertEquals("not skipped when Wi-Fi only is off (the forum was just fetched, so it waits)", 0, loads.size());
    }

    @Test
    public void aFailingForumBacksOffAfterRepeatedErrors() {
        addForum(NOW - 5 * 3_600_000L);
        failure = new SourceException(SourceException.Kind.HTTP, "HTTP 500", 500, 0, null);
        for (int i = 0; i < 6; i++) {
            Forum f = db.forumDao().getAll().get(0);
            f.lastFetchedAt = NOW - 5 * 3_600_000L; // pretend time passed so only the failure count matters
            db.forumDao().update(f);
            sync.syncAll();
        }
        assertEquals("five attempts, then a day's pause", 5, loads.size());
        assertEquals("HTTP 500", db.forumDao().getAll().get(0).lastError);
    }
}
