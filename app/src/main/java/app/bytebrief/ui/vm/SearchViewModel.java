package app.bytebrief.ui.vm;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.concurrent.TimeUnit;

import app.bytebrief.AppContainer;
import app.bytebrief.data.db.ContentListItem;
import app.bytebrief.repo.ContentRepository;

/** Full-text search with filters. Typing is debounced so the index is queried once the user pauses. */
public final class SearchViewModel extends ViewModel {
    public static final class Query {
        public final String text;
        public final ContentRepository.Filters filters;

        Query(String text, ContentRepository.Filters filters) {
            this.text = text;
            this.filters = filters;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Query> query = new MutableLiveData<>(new Query("", new ContentRepository.Filters()));
    private final MutableLiveData<String> text = new MutableLiveData<>("");
    public final LiveData<List<ContentListItem>> results;
    public final LiveData<List<String>> languages;
    private Runnable pending;

    SearchViewModel(AppContainer c) {
        results = Transformations.switchMap(query, q -> c.content.search(q.text, q.filters));
        languages = c.content.languages();
    }

    public LiveData<String> text() {
        return text;
    }

    public ContentRepository.Filters filters() {
        return query.getValue().filters;
    }

    public void setText(String value) {
        text.setValue(value);
        if (pending != null) handler.removeCallbacks(pending);
        pending = () -> query.setValue(new Query(value.trim(), filters()));
        handler.postDelayed(pending, 250);
    }

    public void updateFilters(java.util.function.Consumer<ContentRepository.Filters> change) {
        ContentRepository.Filters f = filters().copy();
        change.accept(f);
        query.setValue(new Query(text.getValue() == null ? "" : text.getValue().trim(), f));
    }

    public static long since(int days) {
        return days <= 0 ? 0 : System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
    }

    @Override
    protected void onCleared() {
        if (pending != null) handler.removeCallbacks(pending);
    }
}
