package app.bytebrief.work;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

import app.bytebrief.settings.AppSettings;

/** Registers background work with WorkManager according to the user's settings. */
public final class Scheduler {
    public static final String PERIODIC_SYNC = "periodic-sync";
    public static final String DAILY_BRIEFING = "daily-briefing";

    private final Context context;
    private final AppSettings settings;

    public Scheduler(Context context, AppSettings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    /** Applies the periodic sync settings; call after any change to them. */
    public void applySync() {
        WorkManager wm = WorkManager.getInstance(context);
        if (!settings.backgroundSyncEnabled()) {
            wm.cancelUniqueWork(PERIODIC_SYNC);
            return;
        }
        int hours = settings.syncIntervalHours();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(settings.syncOnUnmeteredOnly() ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(SyncWorker.class, hours, TimeUnit.HOURS, Math.max(15, hours * 10L), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        wm.enqueueUniquePeriodicWork(PERIODIC_SYNC, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    /** Schedules the next daily briefing (or cancels it). The worker reschedules itself after each run. */
    public void applyBriefing() {
        WorkManager wm = WorkManager.getInstance(context);
        if (!settings.briefingEnabled()) {
            wm.cancelUniqueWork(DAILY_BRIEFING);
            return;
        }
        long delay = delayUntilNext(settings.briefingMinuteOfDay(), System.currentTimeMillis(), ZoneId.systemDefault());
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(BriefingWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build();
        wm.enqueueUniqueWork(DAILY_BRIEFING, ExistingWorkPolicy.REPLACE, req);
    }

    /** Fetches an item's full text in the background (needs a network). */
    public void cacheArticle(long itemId) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ArticleCacheWorker.class)
                .setInputData(new Data.Builder().putLong(ArticleCacheWorker.KEY_ITEM_ID, itemId).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork("cache-article-" + itemId, ExistingWorkPolicy.KEEP, req);
    }

    /**
     * Milliseconds from {@code nowMillis} until the next local occurrence of {@code minuteOfDay}.
     * The target is built from a local date and time and then resolved in the zone, so it stays on the
     * same wall-clock time across daylight-saving changes (adding minutes to a ZonedDateTime would not).
     */
    public static long delayUntilNext(int minuteOfDay, long nowMillis, ZoneId zone) {
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        LocalTime time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
        ZonedDateTime target = now.toLocalDate().atTime(time).atZone(zone);
        if (!target.isAfter(now)) target = now.toLocalDate().plusDays(1).atTime(time).atZone(zone);
        return Math.max(0, target.toInstant().toEpochMilli() - nowMillis);
    }
}
