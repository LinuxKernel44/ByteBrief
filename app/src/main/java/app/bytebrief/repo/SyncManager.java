package app.bytebrief.repo;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.Feed;
import app.bytebrief.data.db.Forum;
import app.bytebrief.data.db.TrackedRepo;
import app.bytebrief.domain.SourceType;
import app.bytebrief.net.SourceException;
import app.bytebrief.settings.SyncConfig;
import app.bytebrief.source.github.ReleaseSource;
import app.bytebrief.source.github.TrendingSource;
import app.bytebrief.source.hn.HackerNewsSource;
import app.bytebrief.source.forum.ForumSource;
import app.bytebrief.source.rss.FeedSource;

/**
 * Runs one synchronization pass over all enabled sources. Sources are processed sequentially (polite
 * to remote servers); inside Hacker News item retrieval is parallel. Failures are isolated per source,
 * and per feed or repository, and reported rather than thrown.
 */
public class SyncManager {
    /** Hacker News stories older than this are not re-requested; their score no longer moves meaningfully. */
    private static final long HN_REFRESH_WINDOW_MS = TimeUnit.HOURS.toMillis(48);
    /** After this many consecutive failures a feed is retried at most once per day. */
    private static final int FEED_BACKOFF_AFTER_FAILURES = 5;

    private final AppDatabase db;
    private final Ingestor ingestor;
    private final HackerNewsSource hackerNews;
    private final TrendingSource trending;
    private final FeedSource feeds;
    private final ReleaseSource releases;
    private final SyncConfig config;
    private final Clock clock;
    private ForumSource forumSource;
    private java.util.function.BooleanSupplier metered = () -> false;

    public SyncManager(AppDatabase db, Ingestor ingestor, HackerNewsSource hackerNews, TrendingSource trending,
                       FeedSource feeds, ReleaseSource releases, SyncConfig config, Clock clock) {
        this.db = db;
        this.ingestor = ingestor;
        this.hackerNews = hackerNews;
        this.trending = trending;
        this.feeds = feeds;
        this.releases = releases;
        this.config = config;
        this.clock = clock;
    }

    /** Enables forum synchronization; without this call forums are ignored. */
    public void setForumSupport(ForumSource forumSource, java.util.function.BooleanSupplier isMeteredNetwork) {
        this.forumSource = forumSource;
        this.metered = isMeteredNetwork;
    }

    /** Receives coarse progress: {@code done} of {@code total} units of work finished, and what just finished. */
    public interface Progress {
        void onProgress(int done, int total, String label);
    }

    private static final class Task {
        final String label;
        final Runnable body;

        Task(String label, Runnable body) {
            this.label = label;
            this.body = body;
        }
    }

    public SyncReport syncAll() {
        return syncAll(false, null);
    }

    public SyncReport syncAll(boolean manual) {
        return syncAll(manual, null);
    }

    /**
     * @param manual   the user asked for this run, so forums are refreshed even if they were checked recently
     * @param progress optional listener, called from worker threads after each unit of work
     */
    public SyncReport syncAll(boolean manual, Progress progress) {
        SyncReport report = new SyncReport();
        List<Task> tasks = planTasks(report, manual);
        runTasks(tasks, report, progress);
        report.pruned = db.contentDao().deleteOlderThan(clock.millis() - TimeUnit.DAYS.toMillis(config.retentionDays()));
        report.finishedAt = clock.millis();
        return report;
    }

    /** One task per feed and per repository so slow sources never hold up the others; slowest kinds go first. */
    private List<Task> planTasks(SyncReport report, boolean manual) {
        List<Task> tasks = new ArrayList<>();
        if (config.isSourceEnabled(SourceType.XDA_FORUM) && forumSource != null) {
            tasks.add(new Task("XDA Forums", () -> syncForums(report, null, manual)));
        }
        if (config.isSourceEnabled(SourceType.HACKER_NEWS)) tasks.add(new Task("Hacker News", () -> syncHackerNews(report)));
        if (config.isSourceEnabled(SourceType.GITHUB_TRENDING)) tasks.add(new Task("GitHub Trending", () -> syncTrending(report)));
        if (config.isSourceEnabled(SourceType.GITHUB_RELEASE)) {
            SyncReport.SourceOutcome out = report.outcome(SourceType.GITHUB_RELEASE.code);
            AtomicBoolean quotaHit = new AtomicBoolean();
            for (TrackedRepo repo : db.trackedRepoDao().getEnabled()) {
                tasks.add(new Task(repo.owner + "/" + repo.name, () -> refreshRepo(repo, report, out, quotaHit)));
            }
        }
        if (config.isSourceEnabled(SourceType.RSS)) {
            SyncReport.SourceOutcome out = report.outcome(SourceType.RSS.code);
            for (Feed f : db.feedDao().getEnabled()) {
                if (isBackedOff(f)) continue;
                tasks.add(new Task(f.title != null ? f.title : f.url, () -> refreshFeed(f, out, false)));
            }
        }
        return tasks;
    }

    private void runTasks(List<Task> tasks, SyncReport report, Progress progress) {
        int total = tasks.size();
        AtomicInteger done = new AtomicInteger();
        if (progress != null) progress.onProgress(0, total, "");
        int threads = config.parallelSync() ? Math.max(1, Math.min(config.syncThreads(), total)) : 1;
        if (threads == 1) {
            for (Task t : tasks) {
                runGuarded(t, report);
                if (progress != null) progress.onProgress(done.incrementAndGet(), total, t.label);
            }
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread th = new Thread(r, "bytebrief-sync");
            th.setDaemon(true);
            return th;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Task t : tasks) {
                futures.add(pool.submit(() -> {
                    runGuarded(t, report);
                    if (progress != null) progress.onProgress(done.incrementAndGet(), total, t.label);
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException ignored) {
                    // runGuarded already recorded the failure
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** A bug or unexpected error in one source must not take the whole run down. */
    private static void runGuarded(Task t, SyncReport report) {
        try {
            t.body.run();
        } catch (RuntimeException e) {
            report.outcome("internal").fail(t.label + ": " + e.getClass().getSimpleName(), false, 0);
        }
    }

    /** Refreshes a single feed regardless of its enabled flag or failure backoff (manual refresh). */
    public SyncReport syncFeed(long feedId) {
        SyncReport report = new SyncReport();
        Feed feed = db.feedDao().getById(feedId);
        if (feed != null) refreshFeed(feed, report.outcome(SourceType.RSS.code), true);
        report.finishedAt = clock.millis();
        return report;
    }

    // ---- Hacker News --------------------------------------------------------------------------

    void syncHackerNews(SyncReport report) {
        SyncReport.SourceOutcome out = report.outcome(SourceType.HACKER_NEWS.code);
        long now = clock.millis();
        for (HackerNewsSource.Feed list : config.hackerNewsFeeds()) {
            try {
                HackerNewsSource.Result r = hackerNews.fetch(list, config.hackerNewsLimit(), id -> {
                    ContentItem existing = db.contentDao().findBySource(SourceType.HACKER_NEWS.code, id);
                    return existing != null && now - existing.publishedAt > HN_REFRESH_WINDOW_MS;
                });
                out.addStats(ingestor.ingest(r.items));
                if (r.failures > 0) out.fail(r.failures + " stories could not be retrieved", true, 0);
            } catch (SourceException e) {
                out.fail("Hacker News " + list.name().toLowerCase(java.util.Locale.ROOT) + ": " + e.getMessage(), e.isRetryable(), e.retryAfterEpochMillis);
            }
        }
    }

    // ---- GitHub Trending ----------------------------------------------------------------------

    void syncTrending(SyncReport report) {
        SyncReport.SourceOutcome out = report.outcome(SourceType.GITHUB_TRENDING.code);
        try {
            List<ContentItem> items = trending.fetch(config.trendingPeriod(), config.trendingLanguage());
            trending.enrich(items, config.trendingEnrichBudget(), item -> {
                ContentItem existing = db.contentDao().findBySource(item.sourceType, item.sourceId);
                return existing == null || existing.metadataJson == null || !existing.metadataJson.contains("\"enriched_at\"");
            });
            out.addStats(ingestor.ingest(items));
        } catch (SourceException e) {
            out.fail("GitHub Trending: " + e.getMessage(), e.isRetryable(), e.retryAfterEpochMillis);
        }
    }

    // ---- RSS ----------------------------------------------------------------------------------

    void syncFeeds(SyncReport report, Long onlyFeedId) {
        SyncReport.SourceOutcome out = report.outcome(SourceType.RSS.code);
        List<Feed> due = new ArrayList<>();
        for (Feed f : db.feedDao().getEnabled()) {
            if (onlyFeedId != null && f.id != onlyFeedId) continue;
            if (isBackedOff(f)) continue;
            due.add(f);
        }
        for (Feed f : due) refreshFeed(f, out, false);
    }

    private boolean isBackedOff(Feed f) {
        return f.consecutiveFailures >= FEED_BACKOFF_AFTER_FAILURES
                && clock.millis() - f.lastFetchedAt < TimeUnit.DAYS.toMillis(1);
    }

    private void refreshFeed(Feed feed, SyncReport.SourceOutcome out, boolean manual) {
        long now = clock.millis();
        try {
            FeedSource.Refresh r = feeds.refresh(feed);
            if (!r.notModified) {
                out.addStats(ingestor.ingest(r.items));
                if (r.title != null && (feed.title == null || feed.title.isEmpty())) feed.title = r.title;
                if (r.siteUrl != null) feed.siteUrl = r.siteUrl;
                feed.etag = r.etag;
                feed.lastModified = r.lastModified;
            }
            feed.lastError = r.truncated ? "Feed was cut off; showing the entries that could be read" : null;
            feed.consecutiveFailures = 0;
        } catch (SourceException e) {
            feed.consecutiveFailures++;
            feed.lastError = e.getMessage();
            out.fail((feed.title != null ? feed.title : feed.url) + ": " + e.getMessage(), e.isRetryable(), e.retryAfterEpochMillis);
        }
        feed.lastFetchedAt = now;
        db.feedDao().update(feed);
    }

    /** Refreshes a single forum regardless of its enabled flag, backoff or network (manual refresh). */
    public SyncReport syncForum(long forumId) {
        SyncReport report = new SyncReport();
        syncForums(report, forumId, true);
        report.finishedAt = clock.millis();
        return report;
    }

    // ---- XDA Forums ---------------------------------------------------------------------------

    /** Forums are checked at most this often by background runs. */
    static final long FORUM_MIN_INTERVAL_MS = TimeUnit.HOURS.toMillis(3);
    /** Only threads started this recently trigger an alert, so an old sticky post coming back never does. */
    static final long FORUM_ALERT_WINDOW_MS = TimeUnit.DAYS.toMillis(3);

    void syncForums(SyncReport report, Long onlyForumId, boolean manual) {
        if (forumSource == null) return;
        List<Forum> forums = new ArrayList<>();
        for (Forum f : onlyForumId != null ? java.util.Collections.singletonList(db.forumDao().getById(onlyForumId)) : db.forumDao().getEnabled()) {
            if (f != null) forums.add(f);
        }
        if (forums.isEmpty()) return;
        SyncReport.SourceOutcome out = report.outcome(SourceType.XDA_FORUM.code);
        if (!manual && config.forumsWifiOnly() && metered.getAsBoolean()) {
            out.status = SyncReport.Status.SKIPPED;
            out.error = "Forums wait for Wi-Fi";
            return;
        }
        for (Forum f : forums) {
            if (!manual && (f.needsVerification || clock.millis() - f.lastFetchedAt < FORUM_MIN_INTERVAL_MS && f.lastError == null)) continue;
            if (!manual && f.consecutiveFailures >= FEED_BACKOFF_AFTER_FAILURES && clock.millis() - f.lastFetchedAt < TimeUnit.DAYS.toMillis(1)) continue;
            long now = clock.millis();
            try {
                List<ContentItem> items = forumSource.fetchThreads(f);
                List<String> fresh = new ArrayList<>();
                for (ContentItem item : items) {
                    boolean isNew = db.contentDao().findBySource(item.sourceType, item.sourceId) == null;
                    long started = startedAt(item);
                    if (isNew && f.lastFetchedAt > 0 && f.notifyNewThreads && now - started < FORUM_ALERT_WINDOW_MS) fresh.add(item.title);
                }
                out.addStats(ingestor.ingest(items));
                report.addNewForumThreads(fresh);
                f.lastError = null;
                f.needsVerification = false;
                f.consecutiveFailures = 0;
            } catch (SourceException e) {
                f.lastError = e.getMessage();
                if (e.kind == SourceException.Kind.VERIFICATION_REQUIRED) {
                    f.needsVerification = true;
                    out.fail((f.title != null ? f.title : f.url) + ": verification needed", false, 0);
                    f.lastFetchedAt = now;
                    db.forumDao().update(f);
                    return; // the other forums are on the same site and would be asked the same
                }
                f.consecutiveFailures++;
                out.fail((f.title != null ? f.title : f.url) + ": " + e.getMessage(), e.isRetryable(), e.retryAfterEpochMillis);
            }
            f.lastFetchedAt = now;
            db.forumDao().update(f);
        }
    }

    private static long startedAt(ContentItem item) {
        try {
            return item.metadataJson == null ? item.publishedAt : new org.json.JSONObject(item.metadataJson).optLong("started_at", item.publishedAt);
        } catch (org.json.JSONException e) {
            return item.publishedAt;
        }
    }

    // ---- GitHub Releases ----------------------------------------------------------------------

    void syncReleases(SyncReport report) {
        SyncReport.SourceOutcome out = report.outcome(SourceType.GITHUB_RELEASE.code);
        AtomicBoolean quotaHit = new AtomicBoolean();
        for (TrackedRepo repo : db.trackedRepoDao().getEnabled()) refreshRepo(repo, report, out, quotaHit);
    }

    /** {@code quotaHit} is shared by all repositories: once the API quota is gone the rest are skipped. */
    private void refreshRepo(TrackedRepo repo, SyncReport report, SyncReport.SourceOutcome out, AtomicBoolean quotaHit) {
        if (quotaHit.get()) return;
        try {
            ReleaseSource.Result r = releases.fetch(repo, config.includePrereleases());
            if (!r.notModified) {
                Ingestor.Stats s = ingestor.ingest(r.items);
                out.addStats(s);
                report.addNewReleases(s.inserted);
                repo.etag = r.etag;
            }
            repo.lastError = null;
            repo.lastCheckedAt = clock.millis();
            db.trackedRepoDao().update(repo);
            if (r.rateLimitRemaining == 0 && !quotaHit.getAndSet(true)) {
                out.fail("GitHub API quota exhausted; remaining repositories were skipped", true, 0);
            }
        } catch (SourceException e) {
            repo.lastError = e.getMessage();
            repo.lastCheckedAt = clock.millis();
            db.trackedRepoDao().update(repo);
            out.fail(repo.owner + "/" + repo.name + ": " + e.getMessage(), e.isRetryable(), e.retryAfterEpochMillis);
            if (e.kind == SourceException.Kind.RATE_LIMITED) quotaHit.set(true); // the quota is shared by all repos
        }
    }
}
