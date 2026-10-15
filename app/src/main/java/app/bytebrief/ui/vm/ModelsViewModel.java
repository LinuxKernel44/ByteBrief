package app.bytebrief.ui.vm;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import app.bytebrief.AppContainer;
import app.bytebrief.ai.AiResult;
import app.bytebrief.ai.LocalAiEngine;
import app.bytebrief.ai.ModelCatalog;
import app.bytebrief.ai.ModelDescriptor;
import app.bytebrief.ai.ModelManager;

public final class ModelsViewModel extends ViewModel {
    private final AppContainer c;
    public final List<ModelDescriptor> models = ModelCatalog.all();
    public final MutableLiveData<String> message = new MutableLiveData<>();
    public final MutableLiveData<Boolean> benchmarking = new MutableLiveData<>(false);
    public final MutableLiveData<LocalAiEngine.Benchmark> benchmark = new MutableLiveData<>();
    private final List<LiveData<ModelManager.Status>> statuses = new ArrayList<>();

    ModelsViewModel(AppContainer c) {
        this.c = c;
        for (ModelDescriptor m : models) statuses.add(c.models.status(m));
    }

    public LiveData<ModelManager.Status> status(int index) {
        return statuses.get(index);
    }

    public ModelManager manager() {
        return c.models;
    }

    public String activeModelId() {
        return c.settings.activeModelId();
    }

    /** Wi-Fi (unmetered) download. */
    public void download(ModelDescriptor m) {
        if (c.models.freeStorageBytes() < m.sizeBytes + 300L * 1024 * 1024) {
            message.setValue("Not enough free storage: " + (m.sizeBytes / (1024 * 1024)) + " MB needed plus some headroom.");
            return;
        }
        c.models.download(m, false);
    }

    /** Only after the user confirmed using mobile data. */
    public void downloadOnMobileData(ModelDescriptor m) {
        c.models.download(m, true);
    }

    public void pause(ModelDescriptor m) {
        c.models.pause(m);
    }

    public void delete(ModelDescriptor m) {
        c.models.delete(m);
    }

    public void load(ModelDescriptor m) {
        c.models.load(m).thenAccept(r -> {
            if (!r.isSuccess()) message.postValue(r.status == AiResult.Status.OUT_OF_MEMORY
                    ? "Not enough memory to load this model. Close other apps or choose the light model."
                    : "Couldn't load the model: " + r.message);
        });
    }

    public void unload() {
        c.models.unload();
    }

    public void runBenchmark() {
        benchmarking.setValue(true);
        c.engine.benchmark().thenAccept(r -> {
            benchmarking.postValue(false);
            if (r.isSuccess()) benchmark.postValue(r.value);
            else message.postValue("Benchmark failed: " + r.message);
        });
    }

    public long totalRamBytes() {
        return c.models.totalRamBytes();
    }

    public long freeStorageBytes() {
        return c.models.freeStorageBytes();
    }

    public boolean allowsMeteredDownloads() {
        return c.settings.allowMeteredModelDownload();
    }

    public void consumeMessage() {
        message.setValue(null);
    }
}
