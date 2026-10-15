package app.bytebrief.work;

import android.content.Context;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import app.bytebrief.AppContainer;
import app.bytebrief.ByteBriefApp;
import app.bytebrief.ai.ModelCatalog;
import app.bytebrief.ai.ModelDescriptor;
import app.bytebrief.ai.ModelDownloader;
import app.bytebrief.notify.Notifier;
import okhttp3.OkHttpClient;

/** Downloads a model as a foreground job so it survives leaving the app. Pausing cancels the job and keeps the partial file. */
public final class ModelDownloadWorker extends Worker {
    public static final String KEY_MODEL_ID = "model_id";
    public static final String KEY_DOWNLOADED = "downloaded";
    public static final String KEY_TOTAL = "total";
    public static final String KEY_VERIFYING = "verifying";
    public static final String KEY_MESSAGE = "message";
    private static final int MAX_NETWORK_RETRIES = 12;

    public ModelDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        ModelDescriptor model = ModelCatalog.find(getInputData().getString(KEY_MODEL_ID));
        if (model == null) return Result.failure(message("Unknown model"));
        AppContainer c = ByteBriefApp.container(getApplicationContext());
        setForegroundAsync(foreground(c, model, 0, true));

        OkHttpClient client = new OkHttpClient.Builder()
                .followSslRedirects(false) // never follow an https link down to http
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        java.io.File base = c.modelStore.dir().getParentFile();
        ModelDownloader downloader = new ModelDownloader(client, c.modelStore, () -> base.getUsableSpace());
        long[] lastNotify = {0};
        ModelDownloader.Result r = downloader.download(model, new ModelDownloader.ProgressListener() {
            @Override
            public void onProgress(long done, long total) {
                setProgressAsync(new Data.Builder().putLong(KEY_DOWNLOADED, done).putLong(KEY_TOTAL, total).build());
                long now = System.currentTimeMillis();
                if (now - lastNotify[0] > 1000) {
                    lastNotify[0] = now;
                    setForegroundAsync(foreground(c, model, (int) (done * 100 / total), false));
                }
            }

            @Override
            public void onVerifying() {
                setProgressAsync(new Data.Builder().putBoolean(KEY_VERIFYING, true).putLong(KEY_TOTAL, model.sizeBytes).build());
                setForegroundAsync(foreground(c, model, 100, true));
            }
        }, this::isStopped);

        switch (r.status) {
            case COMPLETE:
                return Result.success();
            case PAUSED:
                return Result.success(message("Paused"));
            case NETWORK_ERROR:
                return getRunAttemptCount() < MAX_NETWORK_RETRIES ? Result.retry() : Result.failure(message(r.message));
            default:
                return Result.failure(message(r.message));
        }
    }

    private ForegroundInfo foreground(AppContainer c, ModelDescriptor model, int percent, boolean indeterminate) {
        android.app.PendingIntent cancel = WorkManager.getInstance(getApplicationContext()).createCancelPendingIntent(getId());
        return new ForegroundInfo(Notifier.ID_MODEL_DOWNLOAD, c.notifier.modelDownload("Downloading " + model.name, percent, indeterminate, cancel),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    private static Data message(String m) {
        return new Data.Builder().putString(KEY_MESSAGE, m).build();
    }
}
