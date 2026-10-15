package app.bytebrief.ai;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import app.bytebrief.ai.model.Answer;
import app.bytebrief.ai.model.BriefingCandidate;
import app.bytebrief.ai.model.ContentInput;
import app.bytebrief.ai.model.DailyBriefing;
import app.bytebrief.ai.model.ExplainLevel;
import app.bytebrief.ai.model.Explanation;
import app.bytebrief.ai.model.Summary;
import app.bytebrief.ai.model.Takeaways;

/**
 * On-device language-model engine. Operations are queued and run one at a time. Every operation
 * returns a typed {@link AiResult}; none ever returns invented content on failure, and none contacts
 * a network service.
 */
public interface LocalAiEngine {
    enum State { UNLOADED, LOADING, READY, FAILED }

    /**
     * Loads {@code modelFile} and proves it works with a one-token decode before reporting READY.
     * Replaces any model that is currently loaded.
     */
    CompletableFuture<AiResult<ModelDescriptor>> initialize(ModelDescriptor model, File modelFile);

    /** True only after {@link #initialize} has fully succeeded. */
    boolean isReady();

    State state();

    LiveData<State> stateLive();

    @Nullable
    ModelDescriptor activeModel();

    AiTask<Summary> summarizeArticle(ContentInput input, AiProgressListener listener);

    AiTask<Takeaways> extractKeyTakeaways(ContentInput input, AiProgressListener listener);

    AiTask<Explanation> explainTechnicalContent(ContentInput input, ExplainLevel level, AiProgressListener listener);

    AiTask<Answer> answerQuestion(ContentInput input, String question, AiProgressListener listener);

    /** @param candidates pre-selected items in rank order; the model only explains them, it never selects. */
    AiTask<DailyBriefing> generateDailyBriefing(List<BriefingCandidate> candidates, AiProgressListener listener);

    /** Stops the running operation (if any) and frees the model and its memory. */
    CompletableFuture<Void> releaseResources();
}
