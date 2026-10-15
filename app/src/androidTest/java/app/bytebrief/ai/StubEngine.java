package app.bytebrief.ai;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import app.bytebrief.ai.model.Answer;
import app.bytebrief.ai.model.BriefingCandidate;
import app.bytebrief.ai.model.ContentInput;
import app.bytebrief.ai.model.DailyBriefing;
import app.bytebrief.ai.model.ExplainLevel;
import app.bytebrief.ai.model.Explanation;
import app.bytebrief.ai.model.Summary;
import app.bytebrief.ai.model.Takeaways;

/** Test double for {@link LocalAiEngine}: returns exactly the results a test hands it and counts calls. */
final class StubEngine implements LocalAiEngine {
    boolean ready;
    ModelDescriptor active;
    final AtomicInteger calls = new AtomicInteger();
    Supplier<AiResult<Summary>> summary = () -> AiResult.success(new Summary("A summary.", "", false, false));
    Supplier<AiResult<DailyBriefing>> briefing;
    List<BriefingCandidate> lastBriefingCandidates;
    ContentInput lastInput;
    CompletableFuture<AiResult<Summary>> held; // when set, summarize waits for the test to complete it

    StubEngine ready(ModelDescriptor model) {
        this.ready = true;
        this.active = model;
        return this;
    }

    @Override public CompletableFuture<AiResult<ModelDescriptor>> initialize(ModelDescriptor m, File f) {
        ready = true;
        active = m;
        return CompletableFuture.completedFuture(AiResult.success(m));
    }

    @Override public boolean isReady() { return ready; }
    @Override public State state() { return ready ? State.READY : State.UNLOADED; }
    @Override public LiveData<State> stateLive() { return new MutableLiveData<>(state()); }
    @Nullable @Override public ModelDescriptor activeModel() { return active; }

    private static <T> AiTask<T> done(AiResult<T> r) {
        AiTask<T> t = new AiTask<>();
        t.complete(r);
        return t;
    }

    @Override
    public AiTask<Summary> summarizeArticle(ContentInput input, AiProgressListener l) {
        calls.incrementAndGet();
        lastInput = input;
        if (held != null) {
            AiTask<Summary> t = new AiTask<>();
            t.setOnCancel(() -> held.complete(AiResult.failure(AiResult.Status.CANCELLED, "Cancelled")));
            held.thenAccept(t::complete);
            return t;
        }
        return done(summary.get());
    }

    @Override public AiTask<Takeaways> extractKeyTakeaways(ContentInput i, AiProgressListener l) { calls.incrementAndGet(); return done(AiResult.failure(AiResult.Status.UNSUPPORTED, "stub")); }
    @Override public AiTask<Explanation> explainTechnicalContent(ContentInput i, ExplainLevel v, AiProgressListener l) { calls.incrementAndGet(); return done(AiResult.failure(AiResult.Status.UNSUPPORTED, "stub")); }

    @Override
    public AiTask<Answer> answerQuestion(ContentInput input, String q, AiProgressListener l) {
        calls.incrementAndGet();
        lastInput = input;
        return done(AiResult.success(new Answer(true, "An answer.", "evidence", true, false)));
    }

    @Override
    public AiTask<DailyBriefing> generateDailyBriefing(List<BriefingCandidate> c, AiProgressListener l) {
        calls.incrementAndGet();
        lastBriefingCandidates = c;
        return done(briefing.get());
    }

    @Override public CompletableFuture<Void> releaseResources() { ready = false; active = null; return CompletableFuture.completedFuture(null); }
}
