package app.bytebrief.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import app.bytebrief.AppContainer;
import app.bytebrief.ByteBriefApp;
import app.bytebrief.net.SourceException;

/** Saves an item's full text for offline reading. */
public final class ArticleCacheWorker extends Worker {
    public static final String KEY_ITEM_ID = "item_id";

    public ArticleCacheWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long id = getInputData().getLong(KEY_ITEM_ID, -1);
        if (id < 0) return Result.failure();
        AppContainer c = ByteBriefApp.container(getApplicationContext());
        try {
            c.articles.load(id);
            return Result.success();
        } catch (SourceException e) {
            return e.isRetryable() && getRunAttemptCount() < 4 ? Result.retry() : Result.failure();
        }
    }
}
