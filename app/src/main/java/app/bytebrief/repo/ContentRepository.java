package app.bytebrief.repo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import app.bytebrief.data.db.Affinity;
import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.ContentListItem;
import app.bytebrief.domain.FtsQuery;
import app.bytebrief.ranking.Personalizer;
import app.bytebrief.ranking.RankedItem;
import app.bytebrief.ranking.Ranker;
import app.bytebrief.settings.AppSettings;

/** UI-facing access to stored content. Reads are observable; writes run off the main thread. */
public final class ContentRepository {
    /** Filters shared by the list and search screens. Null/-1 mean "any". */
    public static final class Filters {
        public String source;
        public String language;
        public String topic;
        public long sinceMillis;
        /** -1 any, 0 unread only, 1 read only. */
        public int readFilter = -1;
        public boolean bookmarkedOnly;

        public Filters copy() {
            Filters f = new Filters();
            f.source = source;
            f.language = language;
            f.topic = topic;
            f.sinceMillis = sinceMillis;
            f.readFilter = readFilter;
            f.bookmarkedOnly = bookmarkedOnly;
            return f;
        }
    }

    public static final class SyncStatus {
        public final boolean running;
        public final SyncReport lastReport;
        public final long lastSyncAt;
        /** Units of work finished and planned in the running sync; {@code total} is 0 until the plan is known. */
        public final int done;
        public final int total;
        public final String currentLabel;

        SyncStatus(boolean running, SyncReport lastReport, long lastSyncAt) {
            this(running, lastReport, lastSyncAt, 0, 0, "");
        }

        SyncStatus(boolean running, SyncReport lastReport, long lastSyncAt, int done, int total, String currentLabel) {
            this.running = running;
            this.lastReport = lastReport;
            this.lastSyncAt = lastSyncAt;
            this.done = done;
            this.total = total;
            this.currentLabel = currentLabel;
        }

        /** Progress in 0..1, or a negative value while the amount of work is still unknown. */
        public float fraction() {
            return total <= 0 ? -1f : Math.min(1f, done / (float) total);
        }
    }

    private static final int LIST_LIMIT = 300;
    private static final int FOR_YOU_POOL = 400;
    private static final int FOR_YOU_SIZE = 80;

    private final AppDatabase db;
    private final SyncManager sync;
    private final Personalizer personalizer;
    private final AppSettings settings;
    private final ExecutorService io;
    private final Clock clock;
    private final MutableLiveData<SyncStatus> syncStatus;
    private final ArticleCacheTrigger cacheTrigger;

    /** Called when an item is bookmarked so its full text can be saved for offline reading. */
    public interface ArticleCacheTrigger {
        void onBookmarked(long itemId);
    }

    public ContentRepository(AppDatabase db, SyncManager sync, Personalizer personalizer, AppSettings settings,
                             ExecutorService io, Clock clock, ArticleCacheTrigger cacheTrigger) {
        this.db = db;
        this.sync = sync;
        this.personalizer = personalizer;
        this.settings = settings;
        this.io = io;
        this.clock = clock;
        this.cacheTrigger = cacheTrigger;
        this.syncStatus = new MutableLiveData<>(new SyncStatus(false, null, settings.lastSyncAt()));
    }

    // ---- reading -----------------------------------------------------------------------------

    public LiveData<List<ContentListItem>> latest(Filters f) {
        return db.contentDao().observeList(f.source, f.language, f.topic, f.sinceMillis, f.readFilter, f.bookmarkedOnly ? 1 : 0, LIST_LIMIT);
    }

    public LiveData<List<ContentListItem>> search(String query, Filters f) {
        String fts = FtsQuery.build(query);
        if (fts == null) return new MutableLiveData<>(java.util.Collections.emptyList());
        return db.contentDao().search(fts, f.source, f.language, f.topic, f.sinceMillis, f.readFilter, f.bookmarkedOnly ? 1 : 0, LIST_LIMIT);
    }

    public LiveData<List<ContentListItem>> bookmarks() {
        return db.contentDao().observeBookmarks(LIST_LIMIT);
    }

    /** "For you": the ranker over recent items; recomputed when content or preferences change. */
    public LiveData<List<RankedItem>> forYou() {
        MediatorLiveData<List<RankedItem>> out = new MediatorLiveData<>();
        LiveData<List<ContentListItem>> candidates = db.contentDao().observeCandidates(clock.millis() - TimeUnit.DAYS.toMillis(7), FOR_YOU_POOL);
        LiveData<List<Affinity>> affinities = db.affinityDao().observeAll();
        Runnable recompute = () -> {
            List<ContentListItem> c = candidates.getValue();
            if (c == null) return;
            io.execute(() -> out.postValue(Ranker.rank(c, personalizer.buildContext(), FOR_YOU_SIZE)));
        };
        out.addSource(candidates, v -> recompute.run());
        out.addSource(affinities, v -> recompute.run());
        return out;
    }

    public LiveData<ContentItem> item(long id) {
        return db.contentDao().observeById(id);
    }

    /** All sources that reference the same subject as an item, newest first. */
    public LiveData<List<ContentItem>> group(String dedupKey) {
        return db.contentDao().observeGroup(dedupKey);
    }

    public LiveData<List<String>> languages() {
        return db.contentDao().observeLanguages();
    }

    public LiveData<Integer> unreadCount() {
        return db.contentDao().observeUnreadCount();
    }

    // ---- user actions ------------------------------------------------------------------------

    /** Marks an item read and lets the personalizer learn from the open (if learning is enabled). */
    public void markOpened(long id) {
        io.execute(() -> {
            ContentItem item = db.contentDao().getById(id);
            if (item == null) return;
            db.contentDao().setRead(id, true, clock.millis());
            personalizer.onOpened(item);
        });
    }

    public void setRead(long id, boolean read) {
        io.execute(() -> db.contentDao().setRead(id, read, clock.millis()));
    }

    public void setBookmarked(long id, boolean bookmarked) {
        io.execute(() -> {
            ContentItem item = db.contentDao().getById(id);
            if (item == null) return;
            db.contentDao().setBookmarked(id, bookmarked, clock.millis());
            personalizer.onBookmarked(item, bookmarked);
            if (bookmarked) cacheTrigger.onBookmarked(id);
        });
    }

    /** @param feedback -1 not relevant, 0 clear, 1 relevant */
    public void setFeedback(long id, int feedback) {
        io.execute(() -> {
            ContentItem item = db.contentDao().getById(id);
            if (item == null) return;
            db.contentDao().setFeedback(id, feedback, clock.millis());
            personalizer.onFeedback(item, feedback);
        });
    }

    // ---- synchronization ---------------------------------------------------------------------

    public LiveData<SyncStatus> syncStatus() {
        return syncStatus;
    }

    /** Runs a full sync now. Ignored if one is already running. */
    public void refresh() {
        SyncStatus current = syncStatus.getValue();
        if (current != null && current.running) return;
        syncStatus.setValue(new SyncStatus(true, current == null ? null : current.lastReport, settings.lastSyncAt()));
        io.execute(() -> {
            SyncReport previous = current == null ? null : current.lastReport;
            SyncReport report = sync.syncAll(true, (done, total, label) ->
                    syncStatus.postValue(new SyncStatus(true, previous, settings.lastSyncAt(), done, total, label)));
            settings.setLastSyncAt(report.finishedAt);
            syncStatus.postValue(new SyncStatus(false, report, report.finishedAt));
        });
    }
}
