package app.bytebrief.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import app.bytebrief.AppContainer;
import app.bytebrief.ByteBriefApp;
import app.bytebrief.ai.AiCall;
import app.bytebrief.ai.AiProgressListener;
import app.bytebrief.ai.AiResult;
import app.bytebrief.ai.BriefingService;
import app.bytebrief.ai.ModelCatalog;
import app.bytebrief.ai.ModelDescriptor;
import app.bytebrief.ai.ModelStore;

/** Generates the daily briefing at the user's chosen time, notifies, and schedules tomorrow's run. */
public final class BriefingWorker extends Worker {
    private static final long FRESH_SYNC_MS = TimeUnit.MINUTES.toMillis(45);

    public BriefingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppContainer c = ByteBriefApp.container(getApplicationContext());
        if (!c.settings.briefingEnabled()) return Result.success();
        boolean loadedHere = false;
        try {
            if (System.currentTimeMillis() - c.settings.lastSyncAt() > FRESH_SYNC_MS) {
                c.settings.setLastSyncAt(c.sync.syncAll().finishedAt);
            }
            ModelDescriptor active = ModelCatalog.find(c.settings.activeModelId());
            if (active != null && !c.engine.isReady() && c.modelStore.stateOf(active) == ModelStore.State.INSTALLED) {
                loadedHere = c.engine.initialize(active, c.modelStore.fileFor(active)).get(3, TimeUnit.MINUTES).isSuccess();
            }
            AiCall.Outcome<BriefingService.View> o = c.briefing.generate(false, AiProgressListener.NONE).future().get(10, TimeUnit.MINUTES);
            if (o.result.isSuccess()) {
                BriefingService.View v = o.result.value;
                List<String> titles = new ArrayList<>();
                for (int i = 0; i < Math.min(3, v.rows.size()); i++) titles.add(v.rows.get(i).item.title);
                String headline = v.headline != null ? v.headline : v.rows.size() + " items picked for you";
                c.notifier.postBriefing(headline, titles);
            } else if (o.result.status == AiResult.Status.CANCELLED) {
                return Result.retry();
            }
            return Result.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        } catch (Exception e) {
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        } finally {
            // Free the model's memory if only this job needed it; then queue tomorrow's briefing.
            if (loadedHere && !ByteBriefApp.isForeground()) c.engine.releaseResources();
            if (!isStopped()) c.scheduler.applyBriefing();
        }
    }
}
