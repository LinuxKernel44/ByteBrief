package app.bytebrief.settings;

import androidx.annotation.Nullable;

import java.util.Set;

import app.bytebrief.domain.SourceType;
import app.bytebrief.source.github.TrendingSource;
import app.bytebrief.source.hn.HackerNewsSource;

/** Read-only view of the settings that drive synchronization. */
public interface SyncConfig {
    boolean isSourceEnabled(SourceType type);

    Set<HackerNewsSource.Feed> hackerNewsFeeds();

    int hackerNewsLimit();

    TrendingSource.Period trendingPeriod();

    @Nullable
    String trendingLanguage();

    boolean includePrereleases();

    int retentionDays();

    /** Max trending repositories to enrich with README/topics per sync (each costs up to 2 API calls). */
    int trendingEnrichBudget();

    /** Forum pages are heavy (a whole web page each); by default they are only loaded on an unmetered network. */
    default boolean forumsWifiOnly() {
        return true;
    }
}
