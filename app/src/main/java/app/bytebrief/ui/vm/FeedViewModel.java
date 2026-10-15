package app.bytebrief.ui.vm;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.List;

import app.bytebrief.AppContainer;
import app.bytebrief.data.db.ContentListItem;
import app.bytebrief.ranking.RankedItem;
import app.bytebrief.repo.ContentRepository;

/** Home feeds: ranked "For you" and a filterable chronological list, plus sync state. */
public final class FeedViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<ContentRepository.Filters> filters = new MutableLiveData<>(new ContentRepository.Filters());
    public final LiveData<List<RankedItem>> forYou;
    public final LiveData<List<ContentListItem>> latest;
    public final LiveData<List<ContentListItem>> bookmarks;
    public final LiveData<ContentRepository.SyncStatus> syncStatus;

    FeedViewModel(AppContainer c) {
        this.c = c;
        forYou = c.content.forYou();
        latest = Transformations.switchMap(filters, c.content::latest);
        bookmarks = c.content.bookmarks();
        syncStatus = c.content.syncStatus();
    }

    public ContentRepository.Filters filters() {
        return filters.getValue();
    }

    /** @param source source code (see SourceType) or null for all */
    public void setSource(String source) {
        ContentRepository.Filters f = filters().copy();
        f.source = source;
        filters.setValue(f);
    }

    public void setUnreadOnly(boolean unreadOnly) {
        ContentRepository.Filters f = filters().copy();
        f.readFilter = unreadOnly ? 0 : -1;
        filters.setValue(f);
    }

    public void refresh() {
        c.content.refresh();
    }

    public void toggleBookmark(long id, boolean bookmarked) {
        c.content.setBookmarked(id, bookmarked);
    }

    public void setRead(long id, boolean read) {
        c.content.setRead(id, read);
    }

    public boolean hasAnySources() {
        return true;
    }
}
