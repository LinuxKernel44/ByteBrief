package app.bytebrief.ui.vm;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import app.bytebrief.AppContainer;
import app.bytebrief.ai.AiCall;
import app.bytebrief.ai.AiProgressListener;
import app.bytebrief.ai.AiResult;
import app.bytebrief.ai.model.Answer;
import app.bytebrief.ai.model.ExplainLevel;
import app.bytebrief.ai.model.Explanation;
import app.bytebrief.ai.model.Summary;
import app.bytebrief.ai.model.Takeaways;
import app.bytebrief.content.ArticleLoader;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.net.SourceException;

/** One item: stored content, the sources that cover it, saved/AI results, and user actions. */
public final class DetailViewModel extends ViewModel {
    /** One question and its answer state. */
    public static final class Qa {
        public final String question;
        public final boolean withDiscussion;
        public final AiUi<Answer> answer;

        Qa(String question, boolean withDiscussion, AiUi<Answer> answer) {
            this.question = question;
            this.withDiscussion = withDiscussion;
            this.answer = answer;
        }
    }

    /** State of the "save full text for offline reading" action. */
    public static final class ArticleState {
        public final boolean working;
        @Nullable public final String message;

        ArticleState(boolean working, @Nullable String message) {
            this.working = working;
            this.message = message;
        }
    }

    private final AppContainer c;
    private final long itemId;
    public final LiveData<ContentItem> item;
    public final LiveData<List<ContentItem>> group;
    public final MutableLiveData<AiUi<Summary>> summary = new MutableLiveData<>(AiUi.idle());
    public final MutableLiveData<AiUi<Takeaways>> takeaways = new MutableLiveData<>(AiUi.idle());
    public final MutableLiveData<AiUi<Explanation>> explanation = new MutableLiveData<>(AiUi.idle());
    public final MutableLiveData<ExplainLevel> level = new MutableLiveData<>(ExplainLevel.INTERMEDIATE);
    public final MutableLiveData<List<Qa>> qa = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<ArticleState> article = new MutableLiveData<>(new ArticleState(false, null));
    public final MutableLiveData<Boolean> modelReady = new MutableLiveData<>(false);
    private final List<AiCall<?>> running = new ArrayList<>();

    DetailViewModel(AppContainer c, long itemId) {
        this.c = c;
        this.itemId = itemId;
        item = c.content.item(itemId);
        group = Transformations.switchMap(item, i -> i == null ? new MutableLiveData<>(new ArrayList<>()) : c.content.group(i.dedupKey));
        modelReady.setValue(c.engine.isReady());
        c.engine.stateLive().observeForever(s -> modelReady.postValue(c.engine.isReady()));
        c.content.markOpened(itemId);
        // Show anything already generated; this never starts the model.
        peek(c.ai.peekSummary(itemId), summary);
        peek(c.ai.peekTakeaways(itemId), takeaways);
        peek(c.ai.peekExplain(itemId, ExplainLevel.INTERMEDIATE), explanation);
    }

    private <T> void peek(AiCall<T> call, MutableLiveData<AiUi<T>> target) {
        call.future().thenAccept(o -> {
            if (o.result.isSuccess()) target.postValue(AiUi.done(o.result.value, true, o.stale));
        });
    }

    private <T> AiProgressListener stages(MutableLiveData<AiUi<T>> target) {
        return new AiProgressListener() {
            @Override public void onStage(String d) {
                AiUi<T> cur = target.getValue();
                if (cur != null && cur.state == AiUi.State.RUNNING) target.postValue(AiUi.running(d));
            }
            @Override public void onText(String p) { }
        };
    }

    private <T> void finish(AiCall<T> call, MutableLiveData<AiUi<T>> target) {
        synchronized (running) { running.add(call); }
        call.future().thenAccept(o -> {
            synchronized (running) { running.remove(call); }
            if (o.result.isSuccess()) target.postValue(AiUi.done(o.result.value, o.fromCache, o.stale));
            else if (o.result.status == AiResult.Status.CANCELLED) target.postValue(AiUi.idle());
            else target.postValue(AiUi.failed(o.result.status, o.result.message));
        });
    }

    public void summarize(boolean force) {
        summary.setValue(AiUi.running("Starting"));
        finish(c.ai.summarize(itemId, force, stages(summary)), summary);
    }

    public void extractTakeaways(boolean force) {
        takeaways.setValue(AiUi.running("Starting"));
        finish(c.ai.takeaways(itemId, force, stages(takeaways)), takeaways);
    }

    public void explain(ExplainLevel newLevel, boolean force) {
        level.setValue(newLevel);
        explanation.setValue(AiUi.running("Starting"));
        finish(c.ai.explain(itemId, newLevel, force, stages(explanation)), explanation);
    }

    /** Switches the level and shows a saved explanation for it if there is one. */
    public void selectLevel(ExplainLevel newLevel) {
        level.setValue(newLevel);
        explanation.setValue(AiUi.idle());
        peek(c.ai.peekExplain(itemId, newLevel), explanation);
    }

    public void ask(String question, boolean includeDiscussion) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) return;
        List<Qa> list = new ArrayList<>(qa.getValue());
        int index = list.size();
        list.add(new Qa(q, includeDiscussion, AiUi.running("Starting")));
        qa.setValue(list);
        AiCall<Answer> call = c.ai.ask(itemId, q, includeDiscussion, new AiProgressListener() {
            @Override public void onStage(String d) { }
            @Override public void onText(String p) { }
        });
        synchronized (running) { running.add(call); }
        call.future().thenAccept(o -> {
            synchronized (running) { running.remove(call); }
            List<Qa> now = new ArrayList<>(qa.getValue());
            AiUi<Answer> ui = o.result.isSuccess() ? AiUi.done(o.result.value, false, false)
                    : o.result.status == AiResult.Status.CANCELLED ? AiUi.<Answer>failed(AiResult.Status.CANCELLED, null)
                    : AiUi.<Answer>failed(o.result.status, o.result.message);
            if (index < now.size()) now.set(index, new Qa(q, includeDiscussion, ui));
            qa.postValue(now);
        });
    }

    public void cancelAi() {
        List<AiCall<?>> copy;
        synchronized (running) { copy = new ArrayList<>(running); }
        for (AiCall<?> call : copy) call.cancel();
    }

    /** Downloads the page and stores its text for offline reading. */
    public void saveFullText() {
        article.setValue(new ArticleState(true, null));
        c.io.execute(() -> {
            String msg;
            try {
                ArticleLoader.Outcome o = c.articles.load(itemId);
                switch (o) {
                    case STORED: msg = "Full text saved for offline reading."; break;
                    case ALREADY_FULL: msg = "The full text is already saved."; break;
                    case NO_ARTICLE: msg = "Couldn't extract an article from this page (it may need JavaScript, a login or a subscription)."; break;
                    case UNSUPPORTED_TYPE: msg = "This link isn't a web page ByteBrief can read (for example a PDF or video)."; break;
                    default: msg = "Item not found."; break;
                }
            } catch (SourceException e) {
                msg = "Couldn't download the page: " + e.getMessage();
            }
            article.postValue(new ArticleState(false, msg));
        });
    }

    public void setBookmarked(boolean bookmarked) {
        c.content.setBookmarked(itemId, bookmarked);
    }

    public void setRead(boolean read) {
        c.content.setRead(itemId, read);
    }

    public void setFeedback(int feedback) {
        c.content.setFeedback(itemId, feedback);
    }

    @Override
    protected void onCleared() {
        cancelAi();
    }
}
