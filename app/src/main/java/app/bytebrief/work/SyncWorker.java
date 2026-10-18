package app.bytebrief.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;

import app.bytebrief.ByteBriefApp;
import app.bytebrief.AppContainer;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.repo.SyncReport;

/** Periodic background synchronization of all enabled sources. */
public final class SyncWorker extends Worker {
    private static final int MAX_ATTEMPTS = 4;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppContainer c = ByteBriefApp.container(getApplicationContext());
        long before = System.currentTimeMillis();
        SyncReport report = c.sync.syncAll();
        c.settings.setLastSyncAt(report.finishedAt);

        if (report.newReleases > 0 && c.settings.releaseAlertsEnabled()) {
            List<String> titles = new ArrayList<>();
            for (ContentItem item : c.db.contentDao().getReleasesRetrievedSince(before - 1000, 5)) titles.add(item.title);
            c.notifier.postReleases(report.newReleases, titles);
        }
        if (!report.newForumThreads.isEmpty()) c.notifier.postForumThreads(report.newForumThreads);
        // A fully failed run caused by a transient problem is retried with exponential backoff, a few times;
        // after that the next periodic run tries again.
        if (report.shouldRetry() && getRunAttemptCount() < MAX_ATTEMPTS) return Result.retry();
        return Result.success(new Data.Builder().putInt("new_items", report.totalNew()).build());
    }
}
