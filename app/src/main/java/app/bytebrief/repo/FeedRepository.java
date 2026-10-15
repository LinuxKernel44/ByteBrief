package app.bytebrief.repo;

import androidx.lifecycle.LiveData;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.Feed;
import app.bytebrief.data.db.TrackedRepo;
import app.bytebrief.net.SourceException;
import app.bytebrief.source.github.GithubApi;
import app.bytebrief.source.rss.FeedSource;
import app.bytebrief.source.rss.OpmlCodec;

/** Management of RSS feeds and followed GitHub repositories. Blocking methods must not run on the main thread. */
public final class FeedRepository {
    public static final class ImportSummary {
        public int added;
        public int alreadyPresent;
        public int rejected;
    }

    private static final Pattern REPO_REF = Pattern.compile(
            "^(?:https?://(?:www\\.)?github\\.com/)?([A-Za-z0-9_.-]{1,100})/([A-Za-z0-9_.-]{1,100}?)(?:\\.git)?(?:/.*)?$");

    private final AppDatabase db;
    private final FeedSource feedSource;
    private final GithubApi github;
    private final Ingestor ingestor;
    private final ExecutorService io;
    private final Clock clock;

    public FeedRepository(AppDatabase db, FeedSource feedSource, GithubApi github, Ingestor ingestor, ExecutorService io, Clock clock) {
        this.db = db;
        this.feedSource = feedSource;
        this.github = github;
        this.ingestor = ingestor;
        this.io = io;
        this.clock = clock;
    }

    public LiveData<List<Feed>> feeds() {
        return db.feedDao().observeAll();
    }

    public LiveData<List<TrackedRepo>> trackedRepos() {
        return db.trackedRepoDao().observeAll();
    }

    /**
     * Validates {@code url} (following one HTML autodiscovery hop), stores the feed and ingests its entries.
     *
     * @return the stored feed, or the existing one if the URL was already added
     */
    public Feed addFeed(String url) throws SourceException {
        FeedSource.Validated v = feedSource.validate(url);
        Feed existing = db.feedDao().getByUrl(v.feedUrl);
        if (existing != null) return existing;
        Feed f = new Feed();
        f.url = v.feedUrl;
        f.title = v.title;
        f.siteUrl = v.siteUrl;
        f.etag = v.etag;
        f.lastModified = v.lastModified;
        f.lastFetchedAt = clock.millis();
        f.createdAt = clock.millis();
        long id = db.feedDao().insertIgnore(f);
        f.id = id > 0 ? id : db.feedDao().getByUrl(f.url).id;
        // Entries are fetched again by the refresh below so that ids and labels use the stored feed.
        try {
            FeedSource.Refresh r = feedSource.refresh(f);
            ingestor.ingest(r.items);
        } catch (SourceException e) {
            f.lastError = e.getMessage();
            db.feedDao().update(f);
        }
        return f;
    }

    public void removeFeed(long id, boolean deleteItems) {
        io.execute(() -> {
            if (deleteItems) db.feedDao().deleteUnbookmarkedItems(id);
            db.feedDao().delete(id);
        });
    }

    public void setFeedEnabled(long id, boolean enabled) {
        io.execute(() -> {
            Feed f = db.feedDao().getById(id);
            if (f == null) return;
            f.enabled = enabled;
            db.feedDao().update(f);
        });
    }

    /** Imports feeds from OPML without fetching them; they are loaded by the next sync. */
    public ImportSummary importOpml(String opml) throws SourceException {
        OpmlCodec.ImportResult parsed = OpmlCodec.parse(opml);
        ImportSummary s = new ImportSummary();
        s.rejected = parsed.rejected;
        for (OpmlCodec.Outline o : parsed.feeds) {
            Feed f = new Feed();
            f.url = o.xmlUrl;
            f.title = o.title;
            f.siteUrl = o.htmlUrl;
            f.createdAt = clock.millis();
            if (db.feedDao().insertIgnore(f) > 0) s.added++; else s.alreadyPresent++;
        }
        return s;
    }

    public String exportOpml() {
        List<Feed> all = new ArrayList<>(db.feedDao().getAll());
        return OpmlCodec.write(all, "ByteBrief feeds", DateTimeFormatter.RFC_1123_DATE_TIME.format(clock.instant().atZone(ZoneOffset.UTC)));
    }

    // ---- followed repositories ---------------------------------------------------------------

    /** Parses "owner/name" or a github.com URL; null if it is neither. */
    public static String[] parseRepoRef(String input) {
        if (input == null) return null;
        Matcher m = REPO_REF.matcher(input.trim());
        if (!m.matches()) return null;
        String owner = m.group(1);
        String name = m.group(2);
        return GithubApi.isValidSegment(owner) && GithubApi.isValidSegment(name) ? new String[]{owner, name} : null;
    }

    /** Verifies the repository exists on GitHub, then follows it. */
    public TrackedRepo trackRepo(String input) throws SourceException {
        String[] ref = parseRepoRef(input);
        if (ref == null) throw SourceException.invalidUrl("Enter a repository as owner/name or a github.com link");
        github.fetchRepo(ref[0], ref[1]); // throws HTTP 404 if it does not exist
        TrackedRepo r = new TrackedRepo();
        r.owner = ref[0];
        r.name = ref[1];
        r.createdAt = clock.millis();
        db.trackedRepoDao().insertIgnore(r);
        return r;
    }

    public void untrackRepo(long id) {
        io.execute(() -> db.trackedRepoDao().delete(id));
    }

    public void setRepoEnabled(TrackedRepo repo, boolean enabled) {
        io.execute(() -> {
            repo.enabled = enabled;
            db.trackedRepoDao().update(repo);
        });
    }
}
