package app.bytebrief.ai;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import app.bytebrief.ai.model.Answer;
import app.bytebrief.ai.model.BriefingCandidate;
import app.bytebrief.ai.model.ContentInput;
import app.bytebrief.ai.model.DailyBriefing;
import app.bytebrief.ai.model.ExplainLevel;
import app.bytebrief.ai.model.Explanation;
import app.bytebrief.ai.model.Summary;
import app.bytebrief.ai.model.Takeaways;
import app.bytebrief.llama.LlamaException;
import app.bytebrief.llama.LlamaNative;

/** {@link LocalAiEngine} running llama.cpp on the CPU. All native calls happen on one dedicated thread. */
public final class LlamaAiEngine implements LocalAiEngine {
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bytebrief-ai");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });
    private final MutableLiveData<State> stateLive = new MutableLiveData<>(State.UNLOADED);
    private final int threads;

    private volatile State state = State.UNLOADED;
    private volatile ModelDescriptor activeModel;
    // Touched only on the engine thread, except for the volatile reads below.
    private LlamaGenerator generator;
    private AiPipeline pipeline;
    private volatile LlamaGenerator running;

    public LlamaAiEngine(Context context) {
        this(context, defaultThreads());
    }

    public LlamaAiEngine(Context context, int threads) {
        this.context = context.getApplicationContext();
        this.threads = threads;
    }

    /** Performance cores matter most for token generation; leave some headroom for the UI. */
    static int defaultThreads() {
        return Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() - 2));
    }

    @Override
    public CompletableFuture<AiResult<ModelDescriptor>> initialize(ModelDescriptor model, File modelFile) {
        CompletableFuture<AiResult<ModelDescriptor>> f = new CompletableFuture<>();
        setState(State.LOADING);
        executor.execute(() -> {
            closeGenerator();
            AiResult<ModelDescriptor> result = load(model, modelFile);
            setState(result.isSuccess() ? State.READY : State.FAILED);
            if (result.isSuccess()) activeModel = model;
            f.complete(result);
        });
        return f;
    }

    private AiResult<ModelDescriptor> load(ModelDescriptor model, File file) {
        if (!file.isFile() || file.length() != model.sizeBytes) {
            return AiResult.failure(AiResult.Status.MODEL_UNAVAILABLE,
                    "The model file is missing or incomplete. Download it again from the Model screen.");
        }
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mem);
        if (mem.totalMem < model.sizeBytes * 3 / 2) {
            return AiResult.failure(AiResult.Status.OUT_OF_MEMORY, "This device has too little RAM (" + mem.totalMem / (1024 * 1024)
                    + " MiB) for " + model.name + ". Choose the light model.");
        }
        long modelHandle = 0;
        long sessionHandle = 0;
        try {
            LlamaNative.init(context.getApplicationInfo().nativeLibraryDir);
            modelHandle = LlamaNative.loadModel(file.getAbsolutePath(), 0);
            sessionHandle = LlamaNative.createSession(modelHandle, model.contextTokens, threads);
            LlamaGenerator g = new LlamaGenerator(modelHandle, sessionHandle, model.contextTokens);
            // Prove the model can actually decode before anyone is told it is ready.
            TextGenerator.Generation warmup = g.generate(g.formatChat("You are a helpful assistant.", "Reply with one word."), 1, 0f, null, null);
            if (warmup.tokens < 1) {
                g.close();
                return AiResult.failure(AiResult.Status.ERROR, "The model loaded but did not produce any output");
            }
            generator = g;
            pipeline = new AiPipeline(g, new PromptBuilder());
            return AiResult.success(model);
        } catch (LlamaException e) {
            if (sessionHandle != 0) LlamaNative.freeSession(sessionHandle);
            if (modelHandle != 0) LlamaNative.freeModel(modelHandle);
            return AiResult.failure(e.getCode() == LlamaException.CODE_OUT_OF_MEMORY ? AiResult.Status.OUT_OF_MEMORY
                    : e.getCode() == LlamaException.CODE_MODEL_LOAD ? AiResult.Status.MODEL_UNAVAILABLE : AiResult.Status.ERROR, e.getMessage());
        } catch (TextGenerator.GenerationException e) {
            if (sessionHandle != 0) LlamaNative.freeSession(sessionHandle);
            if (modelHandle != 0) LlamaNative.freeModel(modelHandle);
            return AiResult.failure(e.kind == TextGenerator.GenerationException.Kind.OUT_OF_MEMORY
                    ? AiResult.Status.OUT_OF_MEMORY : AiResult.Status.ERROR, e.getMessage());
        } catch (UnsatisfiedLinkError e) {
            return AiResult.failure(AiResult.Status.UNSUPPORTED, "The native inference library is not available on this device");
        }
    }

    private void closeGenerator() {
        if (generator != null) {
            generator.close();
            generator = null;
            pipeline = null;
        }
        activeModel = null;
    }

    @Override public boolean isReady() { return state == State.READY; }

    @Override public State state() { return state; }

    @Override public LiveData<State> stateLive() { return stateLive; }

    @Nullable
    @Override public ModelDescriptor activeModel() { return activeModel; }

    private void setState(State s) {
        state = s;
        stateLive.postValue(s);
    }

    // ---- operations ---------------------------------------------------------------------------

    private <T> AiTask<T> submit(AiProgressListener listener, Function<AiPipeline.Progress, java.util.function.Function<java.util.function.BooleanSupplier, AiResult<T>>> op) {
        AiTask<T> task = new AiTask<>();
        if (!isReady()) {
            task.complete(AiResult.failure(AiResult.Status.MODEL_UNAVAILABLE,
                    "No language model is loaded. Download and load one in the Model screen; reading and search still work without it."));
            return task;
        }
        executor.execute(() -> {
            if (task.isCancelled()) {
                task.complete(AiResult.failure(AiResult.Status.CANCELLED, "Cancelled"));
                return;
            }
            LlamaGenerator g = generator;
            AiPipeline p = pipeline;
            if (g == null || p == null) {
                task.complete(AiResult.failure(AiResult.Status.MODEL_UNAVAILABLE, "The model was unloaded"));
                return;
            }
            running = g;
            task.setOnCancel(g::cancel);
            AiPipeline.Progress progress = new AiPipeline.Progress() {
                @Override public void stage(String d) { listener.onStage(d); }
                @Override public void text(String piece) { listener.onText(piece); }
            };
            try {
                task.complete(op.apply(progress).apply(task::isCancelled));
            } catch (RuntimeException e) {
                task.complete(AiResult.failure(AiResult.Status.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage()));
            } finally {
                running = null;
            }
        });
        return task;
    }

    @Override
    public AiTask<Summary> summarizeArticle(ContentInput input, AiProgressListener l) {
        return submit(l, progress -> cancelled -> pipeline.summarize(input, progress, cancelled));
    }

    @Override
    public AiTask<Takeaways> extractKeyTakeaways(ContentInput input, AiProgressListener l) {
        return submit(l, progress -> cancelled -> pipeline.extractKeyTakeaways(input, progress, cancelled));
    }

    @Override
    public AiTask<Explanation> explainTechnicalContent(ContentInput input, ExplainLevel level, AiProgressListener l) {
        return submit(l, progress -> cancelled -> pipeline.explain(input, level, progress, cancelled));
    }

    @Override
    public AiTask<Answer> answerQuestion(ContentInput input, String question, AiProgressListener l) {
        return submit(l, progress -> cancelled -> pipeline.answer(input, question, progress, cancelled));
    }

    @Override
    public AiTask<DailyBriefing> generateDailyBriefing(List<BriefingCandidate> candidates, AiProgressListener l) {
        return submit(l, progress -> cancelled -> pipeline.briefing(candidates, progress, cancelled));
    }

    @Override
    public CompletableFuture<AiResult<Benchmark>> benchmark() {
        CompletableFuture<AiResult<Benchmark>> f = new CompletableFuture<>();
        if (!isReady()) {
            f.complete(AiResult.failure(AiResult.Status.MODEL_UNAVAILABLE, "Load a model first"));
            return f;
        }
        executor.execute(() -> {
            LlamaGenerator g = generator;
            if (g == null) {
                f.complete(AiResult.failure(AiResult.Status.MODEL_UNAVAILABLE, "The model was unloaded"));
                return;
            }
            running = g;
            try {
                String text = "Explain how a lock-free queue works, in plain words. ".repeat(24);
                String prompt = g.formatChat("You are a helpful assistant.", text);
                int promptTokens = g.countTokens(prompt);
                long t0 = System.nanoTime();
                g.generate(prompt, 1, 0f, null, null);
                double prefillSeconds = (System.nanoTime() - t0) / 1e9;
                long t1 = System.nanoTime();
                TextGenerator.Generation gen = g.generate(prompt, 49, 0f, null, null);
                double totalSeconds = (System.nanoTime() - t1) / 1e9;
                double decodeSeconds = Math.max(1e-3, totalSeconds - prefillSeconds);
                f.complete(AiResult.success(new Benchmark(promptTokens / Math.max(1e-3, prefillSeconds),
                        Math.max(0, gen.tokens - 1) / decodeSeconds, promptTokens, threads)));
            } catch (TextGenerator.GenerationException e) {
                f.complete(AiResult.failure(AiResult.Status.ERROR, e.getMessage()));
            } finally {
                running = null;
            }
        });
        return f;
    }

    @Override
    public CompletableFuture<Void> releaseResources() {
        CompletableFuture<Void> f = new CompletableFuture<>();
        setState(State.UNLOADED); // new operations are refused immediately
        LlamaGenerator g = running;
        if (g != null) g.cancel();
        executor.execute(() -> {
            closeGenerator();
            f.complete(null);
        });
        return f;
    }
}
