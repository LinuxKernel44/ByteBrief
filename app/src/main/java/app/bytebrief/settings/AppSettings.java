package app.bytebrief.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.EnumSet;
import java.util.Set;

import app.bytebrief.domain.SourceType;
import app.bytebrief.source.github.TrendingSource;
import app.bytebrief.source.hn.HackerNewsSource;

/** User preferences backed by private SharedPreferences. Secrets (API tokens) are not stored here. */
public class AppSettings implements SyncConfig {
    private static final String FILE = "bytebrief_settings";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE));
    }

    public AppSettings(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public boolean isSourceEnabled(SourceType type) {
        return prefs.getBoolean("source_" + type.code, true);
    }

    public void setSourceEnabled(SourceType type, boolean enabled) {
        prefs.edit().putBoolean("source_" + type.code, enabled).apply();
    }

    @Override
    public Set<HackerNewsSource.Feed> hackerNewsFeeds() {
        Set<HackerNewsSource.Feed> out = EnumSet.noneOf(HackerNewsSource.Feed.class);
        Set<String> stored = prefs.getStringSet("hn_feeds", null);
        if (stored == null) {
            out.add(HackerNewsSource.Feed.TOP);
            return out;
        }
        for (String s : stored) {
            try {
                out.add(HackerNewsSource.Feed.valueOf(s));
            } catch (IllegalArgumentException ignored) {
                // A value written by a newer or older build; ignore it.
            }
        }
        return out;
    }

    public void setHackerNewsFeeds(Set<HackerNewsSource.Feed> feeds) {
        Set<String> names = new java.util.HashSet<>();
        for (HackerNewsSource.Feed f : feeds) names.add(f.name());
        prefs.edit().putStringSet("hn_feeds", names).apply();
    }

    @Override
    public int hackerNewsLimit() {
        return clamp(prefs.getInt("hn_limit", 30), 5, 100);
    }

    public void setHackerNewsLimit(int limit) {
        prefs.edit().putInt("hn_limit", clamp(limit, 5, 100)).apply();
    }

    @Override
    public TrendingSource.Period trendingPeriod() {
        try {
            return TrendingSource.Period.valueOf(prefs.getString("trending_period", "DAILY"));
        } catch (IllegalArgumentException e) {
            return TrendingSource.Period.DAILY;
        }
    }

    public void setTrendingPeriod(TrendingSource.Period period) {
        prefs.edit().putString("trending_period", period.name()).apply();
    }

    @Nullable
    @Override
    public String trendingLanguage() {
        String v = prefs.getString("trending_language", null);
        return v == null || v.isEmpty() ? null : v;
    }

    public void setTrendingLanguage(@Nullable String language) {
        prefs.edit().putString("trending_language", language == null ? "" : language.trim()).apply();
    }

    @Override
    public boolean includePrereleases() {
        return prefs.getBoolean("include_prereleases", false);
    }

    public void setIncludePrereleases(boolean v) {
        prefs.edit().putBoolean("include_prereleases", v).apply();
    }

    @Override
    public int retentionDays() {
        return clamp(prefs.getInt("retention_days", 30), 3, 365);
    }

    public void setRetentionDays(int days) {
        prefs.edit().putInt("retention_days", clamp(days, 3, 365)).apply();
    }

    @Override
    public int trendingEnrichBudget() {
        return prefs.getBoolean("github_token_present", false) ? 25 : 8;
    }

    /** Remembers only whether a token exists (to size the API budget); the token itself lives in {@code TokenStore}. */
    public void setGithubTokenPresent(boolean present) {
        prefs.edit().putBoolean("github_token_present", present).apply();
    }

    // ---- background sync -------------------------------------------------------------------

    public int syncIntervalHours() {
        return clamp(prefs.getInt("sync_interval_hours", 6), 1, 48);
    }

    public void setSyncIntervalHours(int hours) {
        prefs.edit().putInt("sync_interval_hours", clamp(hours, 1, 48)).apply();
    }

    public boolean syncOnUnmeteredOnly() {
        return prefs.getBoolean("sync_unmetered_only", false);
    }

    public void setSyncOnUnmeteredOnly(boolean v) {
        prefs.edit().putBoolean("sync_unmetered_only", v).apply();
    }

    public boolean backgroundSyncEnabled() {
        return prefs.getBoolean("background_sync", true);
    }

    public void setBackgroundSyncEnabled(boolean v) {
        prefs.edit().putBoolean("background_sync", v).apply();
    }

    // ---- briefing & notifications ----------------------------------------------------------

    public boolean briefingEnabled() {
        return prefs.getBoolean("briefing_enabled", false);
    }

    public void setBriefingEnabled(boolean v) {
        prefs.edit().putBoolean("briefing_enabled", v).apply();
    }

    /** Minutes after local midnight at which the daily briefing is generated. */
    public int briefingMinuteOfDay() {
        return clamp(prefs.getInt("briefing_minute", 7 * 60 + 30), 0, 24 * 60 - 1);
    }

    public void setBriefingMinuteOfDay(int minute) {
        prefs.edit().putInt("briefing_minute", clamp(minute, 0, 24 * 60 - 1)).apply();
    }

    public boolean releaseAlertsEnabled() {
        return prefs.getBoolean("release_alerts", false);
    }

    public void setReleaseAlertsEnabled(boolean v) {
        prefs.edit().putBoolean("release_alerts", v).apply();
    }

    // ---- personalization -------------------------------------------------------------------

    public boolean learningEnabled() {
        return prefs.getBoolean("learning_enabled", true);
    }

    public void setLearningEnabled(boolean v) {
        prefs.edit().putBoolean("learning_enabled", v).apply();
    }

    // ---- appearance ------------------------------------------------------------------------

    /** User override to use opaque surfaces instead of Liquid Glass. */
    public boolean opaqueSurfaces() {
        return prefs.getBoolean("opaque_surfaces", false);
    }

    public void setOpaqueSurfaces(boolean v) {
        prefs.edit().putBoolean("opaque_surfaces", v).apply();
    }

    // ---- local AI --------------------------------------------------------------------------

    @Nullable
    public String activeModelId() {
        return prefs.getString("active_model", null);
    }

    public void setActiveModelId(@Nullable String id) {
        prefs.edit().putString("active_model", id).apply();
    }

    /** Downloads over a metered connection require this explicit opt-in. */
    public boolean allowMeteredModelDownload() {
        return prefs.getBoolean("allow_metered_download", false);
    }

    public void setAllowMeteredModelDownload(boolean v) {
        prefs.edit().putBoolean("allow_metered_download", v).apply();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
