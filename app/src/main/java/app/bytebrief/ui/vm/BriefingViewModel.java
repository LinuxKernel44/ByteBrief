package app.bytebrief.ui.vm;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import app.bytebrief.AppContainer;
import app.bytebrief.ai.AiCall;
import app.bytebrief.ai.AiProgressListener;
import app.bytebrief.ai.BriefingService;

public final class BriefingViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<AiUi<BriefingService.View>> state = new MutableLiveData<>(AiUi.idle());
    private final MutableLiveData<Boolean> modelReady = new MutableLiveData<>(false);
    private AiCall<BriefingService.View> running;

    BriefingViewModel(AppContainer c) {
        this.c = c;
        modelReady.setValue(c.engine.isReady());
        c.engine.stateLive().observeForever(s -> modelReady.postValue(c.engine.isReady()));
        load(c.briefing.preview(), false);
    }

    public LiveData<AiUi<BriefingService.View>> state() {
        return state;
    }

    public LiveData<Boolean> modelReady() {
        return modelReady;
    }

    /** Writes the model's reasons for today's selection. Runs inference, so only on request. */
    public void generate(boolean force) {
        state.setValue(AiUi.running("Starting"));
        load(c.briefing.generate(force, new AiProgressListener() {
            @Override public void onStage(String d) {
                AiUi<BriefingService.View> cur = state.getValue();
                if (cur != null && cur.state == AiUi.State.RUNNING) state.postValue(AiUi.running(d));
            }
            @Override public void onText(String p) { }
        }), true);
    }

    public void refreshSelection() {
        load(c.briefing.preview(), false);
    }

    public void cancel() {
        if (running != null) running.cancel();
    }

    private void load(AiCall<BriefingService.View> call, boolean userInitiated) {
        running = call;
        call.future().thenAccept(o -> {
            if (o.result.isSuccess()) state.postValue(AiUi.done(o.result.value, o.fromCache, o.stale));
            else if (o.result.status == app.bytebrief.ai.AiResult.Status.CANCELLED && userInitiated) refreshSelection();
            else state.postValue(AiUi.failed(o.result.status, o.result.message));
        });
    }

    @Override
    protected void onCleared() {
        cancel();
    }
}
