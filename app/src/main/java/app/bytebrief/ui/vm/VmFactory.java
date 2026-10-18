package app.bytebrief.ui.vm;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import app.bytebrief.AppContainer;

/** Creates ViewModels that need the application container. */
public final class VmFactory implements ViewModelProvider.Factory {
    private final AppContainer container;
    private final long itemId;

    public VmFactory(AppContainer container) {
        this(container, -1);
    }

    public VmFactory(AppContainer container, long itemId) {
        this.container = container;
        this.itemId = itemId;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> cls) {
        if (cls == FeedViewModel.class) return (T) new FeedViewModel(container);
        if (cls == SearchViewModel.class) return (T) new SearchViewModel(container);
        if (cls == DetailViewModel.class) return (T) new DetailViewModel(container, itemId);
        if (cls == BriefingViewModel.class) return (T) new BriefingViewModel(container);
        if (cls == SourcesViewModel.class) return (T) new SourcesViewModel(container);
        if (cls == ModelsViewModel.class) return (T) new ModelsViewModel(container);
        if (cls == ForumsViewModel.class) return (T) new ForumsViewModel(container);
        if (cls == ForumBrowserViewModel.class) return (T) new ForumBrowserViewModel(container);
        if (cls == SettingsViewModel.class) return (T) new SettingsViewModel(container);
        throw new IllegalArgumentException("Unknown ViewModel " + cls);
    }
}
