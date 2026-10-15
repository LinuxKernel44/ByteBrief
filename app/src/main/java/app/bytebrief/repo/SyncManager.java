package app.bytebrief.repo;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.Feed;
import app.bytebrief.data.db.TrackedRepo;
import app.bytebrief.domain.SourceType;
import app.bytebrief.net.SourceException;
import app.bytebrief.settings.SyncConfig;
import app.bytebrief.source.github.ReleaseSource;
import app.bytebrief.source.github.TrendingSource;
import app.bytebrief.source.hn.HackerNewsSource;
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

    public SyncReport syncAll() {
        SyncReport report = new SyncReport();
        if (config.isSourceEnabled(SourceType.HACKER_NEWS)) syncHackerNews(report);
        if (config.isSourceEnabled(SourceType.GITHUB_TRENDING)) syncTrending(report);
        if (config.isSourceEnabled(SourceType.RSS)) syncFeeds(report, null);
        if (config.isSourceEnabled(SourceType.GITHUB_RELEASE)) syncReleases(report);
        report.pruned = db.contentDao().deleteOlderThan(clock.millis() - TimeUnit.DAYS.toMillis(config.retentionDays()));
        report.finishedAt = clock.millis();
        return report;
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
                out.stats.add(ingestor.ingest(r.items));
                if (r.failures > 0) out.fail(r.failures + " stories could not be retrieved", true, 0);
            } catch (SourceException e) {
                out.fail("Hacker News " + list.name().toLowerCase() + ": " + e.getMessage(), e.isRetryable(), e.retryAfterEpochMillis);
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
            out.stats.add(ingestor.ingest(items));
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
                out.stats.add(ingestor.ingest(r.items));
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

    // ---- GitHub Releases ----------------------------------------------------------------------

    void syncReleases(SyncReport report) {
        SyncReport.SourceOutcome out = report.outcome(SourceType.GITHUB_RELEASE.code);
        for (TrackedRepo repo : db.trackedRepoDao().getEnabled()) {
            try {
                ReleaseSource.Result r = releases.fetch(repo, config.includePrereleases());
                if (!r.notModified) {
                    Ingestor.Stats s = ingestor.ingest(r.items);
                    out.stats.add(s);
                    report.newReleases += s.inserted;
                    repo.etag = r.etag;
                }
                repo.lastError = null;
                repo.lastCheckedAt = clock.millis();
                db.trackedRepoDao().update(repo);
                if (r.rateLimitRemaining == 0) {
                    out.fail("GitHub API quota exhausted; remaining repositories were skipped", true, 0);
                    return;
                }
            } catch (SourceException e) {
                repo.lastError = e.getMessage();
                repo.lastCheckedAt = clock.millis();
                db.trackedRepoDao().update(repo);
                out.fail(repo.owner + "/" + repo.name + ": " + e.getMessage(), e.isRetryable(), e.retryAfterEpochMillis);
                if (e.kind == SourceException.Kind.RATE_LIMITED) return; // the quota is shared by all repos
            }
        }
    }
}
