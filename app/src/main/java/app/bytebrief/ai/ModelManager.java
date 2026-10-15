package app.bytebrief.ai;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import app.bytebrief.settings.AppSettings;
import app.bytebrief.work.ModelDownloadWorker;

/** Everything the Models screen needs: status, download control, loading and deletion. */
public final class ModelManager {
    public enum Phase { NOT_DOWNLOADED, WAITING_FOR_NETWORK, DOWNLOADING, VERIFYING, PAUSED, INSTALLED, LOADING, READY, FAILED }

    public enum Fit { COMFORTABLE, TIGHT, TOO_LARGE }

    public static final class Status {
        public final ModelDescriptor model;
        public final Phase phase;
        public final long downloadedBytes;
        public final long totalBytes;
        @Nullable
        public final String message;

        Status(ModelDescriptor model, Phase phase, long downloadedBytes, long totalBytes, @Nullable String message) {
            this.model = model;
            this.phase = phase;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
            this.message = message;
        }

        public int percent() {
            return totalBytes <= 0 ? 0 : (int) (downloadedBytes * 100 / totalBytes);
        }
    }

    private final Context context;
    private final ModelStore store;
    private final LocalAiEngine engine;
    private final AppSettings settings;
    private final MutableLiveData<Long> tick = new MutableLiveData<>(0L);
    private volatile String loadingModelId;
    private volatile String lastLoadError;

    public ModelManager(Context context, ModelStore store, LocalAiEngine engine, AppSettings settings) {
        this.context = context.getApplicationContext();
        this.store = store;
        this.engine = engine;
        this.settings = settings;
    }

    public static String workName(ModelDescriptor m) {
        return "model-download-" + m.id;
    }

    public LiveData<Status> status(ModelDescriptor m) {
        MediatorLiveData<Status> out = new MediatorLiveData<>();
        LiveData<List<WorkInfo>> work = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(workName(m));
        Runnable recompute = () -> out.setValue(compute(m, work.getValue()));
        out.addSource(work, v -> recompute.run());
        out.addSource(engine.stateLive(), v -> recompute.run());
        out.addSource(tick, v -> recompute.run());
        return out;
    }

    private Status compute(ModelDescriptor m, @Nullable List<WorkInfo> infos) {
        ModelDescriptor active = engine.activeModel();
        if (active != null && active.id.equals(m.id) && engine.state() == LocalAiEngine.State.READY) {
            return new Status(m, Phase.READY, m.sizeBytes, m.sizeBytes, null);
        }
        if (m.id.equals(loadingModelId) && engine.state() == LocalAiEngine.State.LOADING) {
            return new Status(m, Phase.LOADING, m.sizeBytes, m.sizeBytes, null);
        }
        WorkInfo w = latest(infos);
        if (w != null) {
            Data progress = w.getProgress();
            switch (w.getState()) {
                case RUNNING:
                    if (progress.getBoolean(ModelDownloadWorker.KEY_VERIFYING, false)) {
                        return new Status(m, Phase.VERIFYING, m.sizeBytes, m.sizeBytes, "Verifying the download");
                    }
                    return new Status(m, Phase.DOWNLOADING, progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, store.partialBytes(m)),
                            m.sizeBytes, null);
                case ENQUEUED:
                case BLOCKED:
                    return new Status(m, Phase.WAITING_FOR_NETWORK, store.partialBytes(m), m.sizeBytes,
                            "Waiting for a suitable network");
                case FAILED:
                    if (store.stateOf(m) != ModelStore.State.INSTALLED) {
                        return new Status(m, Phase.FAILED, store.partialBytes(m), m.sizeBytes,
                                w.getOutputData().getString(ModelDownloadWorker.KEY_MESSAGE));
                    }
                    break;
                default:
                    break;
            }
        }
        switch (store.stateOf(m)) {
            case INSTALLED:
                return new Status(m, Phase.INSTALLED, m.sizeBytes, m.sizeBytes, m.id.equals(lastLoadErrorModel()) ? lastLoadError : null);
            case PARTIAL:
                return new Status(m, Phase.PAUSED, store.partialBytes(m), m.sizeBytes, null);
            default:
                return new Status(m, Phase.NOT_DOWNLOADED, 0, m.sizeBytes, null);
        }
    }

    private String lastLoadErrorModel() {
        return loadFailedFor;
    }

    private volatile String loadFailedFor;

    @Nullable
    private static WorkInfo latest(@Nullable List<WorkInfo> infos) {
        if (infos == null || infos.isEmpty()) return null;
        return infos.get(infos.size() - 1);
    }

    // ---- actions -----------------------------------------------------------------------------

    /**
     * Starts or resumes a download. By default it waits for an unmetered (Wi-Fi) network; pass
     * {@code allowMetered} only after the user explicitly confirmed using mobile data.
     */
    public void download(ModelDescriptor m, boolean allowMetered) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(allowMetered ? NetworkType.CONNECTED : NetworkType.UNMETERED)
                .setRequiresStorageNotLow(true)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ModelDownloadWorker.class)
                .setInputData(new Data.Builder().putString(ModelDownloadWorker.KEY_MODEL_ID, m.id).build())
                .setConstraints(constraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(workName(m), ExistingWorkPolicy.REPLACE, req);
    }

    /** Stops the download; the partial file is kept so it can resume. */
    public void pause(ModelDescriptor m) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(m));
        tick.postValue(System.nanoTime());
    }

    public CompletableFuture<Void> delete(ModelDescriptor m) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(m));
        CompletableFuture<Void> unload = CompletableFuture.completedFuture(null);
        ModelDescriptor active = engine.activeModel();
        if (active != null && active.id.equals(m.id)) unload = engine.releaseResources();
        if (m.id.equals(settings.activeModelId())) settings.setActiveModelId(null);
        return unload.thenRun(() -> {
            store.delete(m);
            tick.postValue(System.nanoTime());
        });
    }

    /** Loads an installed model into memory and, on success, makes it the active one. */
    public CompletableFuture<AiResult<ModelDescriptor>> load(ModelDescriptor m) {
        if (store.stateOf(m) != ModelStore.State.INSTALLED) {
            return CompletableFuture.completedFuture(AiResult.failure(AiResult.Status.MODEL_UNAVAILABLE, "The model is not downloaded"));
        }
        loadingModelId = m.id;
        loadFailedFor = null;
        tick.postValue(System.nanoTime());
        return engine.initialize(m, store.fileFor(m)).thenApply(r -> {
            loadingModelId = null;
            if (r.isSuccess()) {
                settings.setActiveModelId(m.id);
            } else {
                loadFailedFor = m.id;
                lastLoadError = r.message;
            }
            tick.postValue(System.nanoTime());
            return r;
        });
    }

    public CompletableFuture<Void> unload() {
        return engine.releaseResources().thenRun(() -> tick.postValue(System.nanoTime()));
    }

    /** Loads the previously active model at startup, if it is still installed. */
    public void autoLoad() {
        ModelDescriptor m = ModelCatalog.find(settings.activeModelId());
        if (m != null && !engine.isReady() && store.stateOf(m) == ModelStore.State.INSTALLED) load(m);
    }

    // ---- device facts ------------------------------------------------------------------------

    public long totalRamBytes() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        context.getSystemService(ActivityManager.class).getMemoryInfo(mi);
        return mi.totalMem;
    }

    public long freeStorageBytes() {
        java.io.File base = store.dir().getParentFile();
        return base == null ? 0 : base.getUsableSpace();
    }

    /** A guideline from total RAM versus the model's recommendation; the real test is loading it. */
    public Fit fit(ModelDescriptor m) {
        long ramMiB = totalRamBytes() / (1024 * 1024);
        // Android reports slightly less than the marketed RAM, so allow ~12% under the recommendation.
        if (ramMiB * 1.12 >= m.recommendedRamMiB) return Fit.COMFORTABLE;
        if (totalRamBytes() >= m.sizeBytes * 2) return Fit.TIGHT;
        return Fit.TOO_LARGE;
    }
}
