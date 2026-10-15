package app.bytebrief.ai;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;

import app.bytebrief.ai.model.Answer;
import app.bytebrief.ai.model.ContentInput;
import app.bytebrief.ai.model.ExplainLevel;
import app.bytebrief.ai.model.Explanation;
import app.bytebrief.ai.model.Summary;
import app.bytebrief.ai.model.Takeaways;
import app.bytebrief.content.MetadataFormatter;
import app.bytebrief.data.db.AiOutput;
import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.AiStatus;
import app.bytebrief.domain.BodyState;
import app.bytebrief.net.SourceException;

/**
 * Item-level AI with a local cache. Stored outputs are keyed by item, kind and content hash, so they
 * are returned without inference (and without a loaded model) until the item's text changes.
 */
public final class AiService {
    /** Fetches top comments for an item's discussion; may use the network, so it runs only on explicit request. */
    public interface DiscussionLoader {
        List<String> load(ContentItem item) throws SourceException;
    }

    private interface Run<T> {
        AiTask<T> start(ContentInput input);
    }

    private interface Ser<T> {
        JSONObject toJson(T value) throws JSONException;
    }

    private interface De<T> {
        T fromJson(JSONObject o);
    }

    private final AppDatabase db;
    private final LocalAiEngine engine;
    private final ExecutorService io;
    private final Clock clock;
    private final DiscussionLoader discussion;

    public AiService(AppDatabase db, LocalAiEngine engine, ExecutorService io, Clock clock, DiscussionLoader discussion) {
        this.db = db;
        this.engine = engine;
        this.io = io;
        this.clock = clock;
        this.discussion = discussion;
    }

    public AiCall<Summary> summarize(long itemId, boolean force, AiProgressListener l) {
        return cached(itemId, AiOutput.KIND_SUMMARY, force, true, in -> engine.summarizeArticle(in, l), Summary::toJson, Summary::fromJson);
    }

    public AiCall<Takeaways> takeaways(long itemId, boolean force, AiProgressListener l) {
        return cached(itemId, AiOutput.KIND_TAKEAWAYS, force, true, in -> engine.extractKeyTakeaways(in, l), Takeaways::toJson, Takeaways::fromJson);
    }

    public AiCall<Explanation> explain(long itemId, ExplainLevel level, boolean force, AiProgressListener l) {
        return cached(itemId, AiOutput.KIND_EXPLAIN_PREFIX + level.code, force, true,
                in -> engine.explainTechnicalContent(in, level, l), Explanation::toJson, Explanation::fromJson);
    }

    /** Stored summary only; never runs the model. Completes with NOT_CACHED when nothing valid is stored. */
    public AiCall<Summary> peekSummary(long itemId) {
        return cached(itemId, AiOutput.KIND_SUMMARY, false, false, in -> null, Summary::toJson, Summary::fromJson);
    }

    public AiCall<Takeaways> peekTakeaways(long itemId) {
        return cached(itemId, AiOutput.KIND_TAKEAWAYS, false, false, in -> null, Takeaways::toJson, Takeaways::fromJson);
    }

    public AiCall<Explanation> peekExplain(long itemId, ExplainLevel level) {
        return cached(itemId, AiOutput.KIND_EXPLAIN_PREFIX + level.code, false, false, in -> null, Explanation::toJson, Explanation::fromJson);
    }

    /** Questions are not cached. With {@code includeDiscussion} the top Hacker News comments are fetched first. */
    public AiCall<Answer> ask(long itemId, String question, boolean includeDiscussion, AiProgressListener l) {
        AiCall<Answer> call = new AiCall<>();
        io.execute(() -> {
            try {
                ContentItem item = db.contentDao().getById(itemId);
                if (item == null) {
                    call.complete(outcome(AiResult.failure(AiResult.Status.ERROR, "The item no longer exists"), false, false, null));
                    return;
                }
                if (!engine.isReady()) {
                    call.complete(outcome(unavailable(), false, false, null));
                    return;
                }
                List<String> comments = null;
                if (includeDiscussion) {
                    try {
                        comments = discussion.load(item);
                    } catch (SourceException e) {
                        // Offline or rate limited: answer from the stored text and say nothing was added.
                    }
                }
                run(call, engine.answerQuestion(inputFor(item, comments), question, l), false);
            } catch (RuntimeException e) {
                call.complete(outcome(AiResult.failure(AiResult.Status.ERROR, e.getClass().getSimpleName()), false, false, null));
            }
        });
        return call;
    }

    private <T> AiCall<T> cached(long itemId, String kind, boolean force, boolean runIfMissing, Run<T> start, Ser<T> ser, De<T> de) {
        AiCall<T> call = new AiCall<>();
        io.execute(() -> {
            try {
                ContentItem item = db.contentDao().getById(itemId);
                if (item == null) {
                    call.complete(outcome(AiResult.failure(AiResult.Status.ERROR, "The item no longer exists"), false, false, null));
                    return;
                }
                ModelDescriptor active = engine.activeModel();
                if (!force) {
                    AiOutput stored = db.aiOutputDao().find(itemId, kind);
                    if (stored != null && stored.contentHash.equals(item.contentHash)) {
                        try {
                            T value = de.fromJson(new JSONObject(stored.json));
                            call.complete(outcome(AiResult.success(value), true,
                                    active != null && !active.id.equals(stored.modelId), stored.modelId));
                            return;
                        } catch (JSONException | IllegalArgumentException e) {
                            // Unreadable cache entry: fall through and regenerate.
                        }
                    }
                }
                if (!runIfMissing) {
                    call.complete(outcome(AiResult.failure(AiResult.Status.NOT_CACHED, "Nothing generated yet"), false, false, null));
                    return;
                }
                if (!engine.isReady()) {
                    call.complete(outcome(unavailable(), false, false, null));
                    return;
                }
                db.contentDao().setAiStatus(itemId, AiStatus.PENDING);
                AiTask<T> task = start.start(inputFor(item, null));
                call.setOnCancel(task::cancel);
                AiResult<T> result = task.future().get();
                String modelId = engine.activeModel() == null ? "" : engine.activeModel().id;
                if (result.isSuccess()) {
                    ContentItem current = db.contentDao().getById(itemId);
                    // Only store if the text did not change while the model was working.
                    if (current != null && current.contentHash.equals(item.contentHash)) {
                        AiOutput out = new AiOutput();
                        out.itemId = itemId;
                        out.kind = kind;
                        out.json = ser.toJson(result.value).toString();
                        out.contentHash = item.contentHash;
                        out.modelId = modelId;
                        out.createdAt = clock.millis();
                        db.aiOutputDao().upsert(out);
                        db.contentDao().setAiStatus(itemId, AiStatus.DONE);
                    }
                } else {
                    db.contentDao().setAiStatus(itemId, result.status == AiResult.Status.CANCELLED ? AiStatus.NONE : AiStatus.FAILED);
                }
                call.complete(outcome(result, false, false, modelId));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                call.complete(outcome(AiResult.failure(AiResult.Status.CANCELLED, "Interrupted"), false, false, null));
            } catch (Exception e) {
                call.complete(outcome(AiResult.failure(AiResult.Status.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage()), false, false, null));
            }
        });
        return call;
    }

    private <T> void run(AiCall<T> call, AiTask<T> task, boolean unused) {
        call.setOnCancel(task::cancel);
        try {
            AiResult<T> r = task.future().get();
            call.complete(outcome(r, false, false, engine.activeModel() == null ? null : engine.activeModel().id));
        } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            call.complete(outcome(AiResult.failure(AiResult.Status.CANCELLED, "Interrupted"), false, false, null));
        }
    }

    /** Builds the model input from what is stored locally; nothing is fetched here. */
    ContentInput inputFor(ContentItem item, @Nullable List<String> comments) {
        String text = item.bodyText != null ? item.bodyText : item.description != null ? item.description : "";
        return new ContentInput(item.id, item.title, item.sourceLabel != null ? item.sourceLabel : item.sourceType,
                item.canonicalUrl, MetadataFormatter.format(item), text, item.bodyState != BodyState.FULL, comments);
    }

    private static <T> AiCall.Outcome<T> outcome(AiResult<T> r, boolean fromCache, boolean stale, @Nullable String modelId) {
        return new AiCall.Outcome<>(r, fromCache, stale, modelId);
    }

    private static <T> AiResult<T> unavailable() {
        return AiResult.failure(AiResult.Status.MODEL_UNAVAILABLE,
                "No language model is loaded. Download and load one in Models; reading, search and saved summaries still work.");
    }
}
