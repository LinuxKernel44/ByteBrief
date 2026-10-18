package app.bytebrief.repo;

import androidx.lifecycle.LiveData;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.Forum;
import app.bytebrief.net.SourceException;
import app.bytebrief.source.forum.ForumPageModels;
import app.bytebrief.source.forum.ForumSource;

/** Management of followed XDA forums. Blocking methods must not run on the main thread. */
public final class ForumRepository {
    private final AppDatabase db;
    private final ForumSource source;
    private final Ingestor ingestor;
    private final ExecutorService io;
    private final Clock clock;

    public ForumRepository(AppDatabase db, ForumSource source, Ingestor ingestor, ExecutorService io, Clock clock) {
        this.db = db;
        this.source = source;
        this.ingestor = ingestor;
        this.io = io;
        this.clock = clock;
    }

    private Runnable onNoForumsLeft = () -> { };

    /** Called on the I/O thread after the last followed forum was removed (used to forget the site's cookies). */
    public void setOnNoForumsLeft(Runnable r) {
        this.onNoForumsLeft = r;
    }

    public LiveData<List<Forum>> forums() {
        return db.forumDao().observeAll();
    }

    /** Loads the forum, stores it and ingests its current threads. Returns the existing forum if already followed. */
    public Forum addForum(String input) throws SourceException {
        ForumSource.Validated v = source.validate(input);
        Forum existing = db.forumDao().getByUrl(v.url);
        if (existing != null) return existing;
        Forum f = new Forum();
        f.url = v.url;
        f.title = v.title;
        f.createdAt = clock.millis();
        long id = db.forumDao().insertIgnore(f);
        f.id = id > 0 ? id : db.forumDao().getByUrl(f.url).id;
        try {
            List<ContentItem> items = source.toItems(f, v.threads);
            ingestor.ingest(items);
            f.lastFetchedAt = clock.millis();
        } catch (RuntimeException e) {
            f.lastError = "The threads could not be stored";
        }
        db.forumDao().update(f);
        return f;
    }

    public ForumPageModels.NodeList browse(String url) throws SourceException {
        return source.browse(url);
    }

    public void remove(long id, boolean deleteThreads) {
        io.execute(() -> {
            if (deleteThreads) db.forumDao().deleteUnbookmarkedThreads(id);
            db.forumDao().delete(id);
            if (db.forumDao().getAll().isEmpty()) onNoForumsLeft.run();
        });
    }

    public void setEnabled(long id, boolean enabled) {
        io.execute(() -> {
            Forum f = db.forumDao().getById(id);
            if (f == null) return;
            f.enabled = enabled;
            db.forumDao().update(f);
        });
    }

    public void setNotify(long id, boolean notify) {
        io.execute(() -> {
            Forum f = db.forumDao().getById(id);
            if (f == null) return;
            f.notifyNewThreads = notify;
            db.forumDao().update(f);
        });
    }

    /** Called once the user has passed the site's check, so forums that were waiting are tried again. */
    public void markVerified() {
        io.execute(() -> db.forumDao().clearVerificationFlags());
    }
}
