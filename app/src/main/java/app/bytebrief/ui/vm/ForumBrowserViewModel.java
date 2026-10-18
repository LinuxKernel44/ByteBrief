package app.bytebrief.ui.vm;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import app.bytebrief.AppContainer;
import app.bytebrief.data.db.Forum;
import app.bytebrief.net.SourceException;
import app.bytebrief.source.forum.ForumPageModels.Node;
import app.bytebrief.source.forum.ForumPageModels.NodeList;
import app.bytebrief.source.forum.ForumUrls;

/** Walks the XDA forum tree (home, categories, forums) so a forum can be chosen without typing its address. */
public final class ForumBrowserViewModel extends ViewModel {
    /** What the screen shows. */
    public static final class State {
        public final String url;
        @Nullable public final String title;
        public final List<Node> nodes;
        public final boolean canFollow;
        public final boolean loading;
        @Nullable public final String error;
        public final boolean needsVerification;

        State(String url, @Nullable String title, List<Node> nodes, boolean canFollow, boolean loading, @Nullable String error, boolean needsVerification) {
            this.url = url;
            this.title = title;
            this.nodes = nodes;
            this.canFollow = canFollow;
            this.loading = loading;
            this.error = error;
            this.needsVerification = needsVerification;
        }
    }

    private final AppContainer c;
    private final Deque<String> history = new ArrayDeque<>();
    public final MutableLiveData<State> state = new MutableLiveData<>();
    public final MutableLiveData<String> message = new MutableLiveData<>();
    private String current = ForumUrls.HOME;
    private boolean started;

    ForumBrowserViewModel(AppContainer c) {
        this.c = c;
    }

    /** Loads the root the first time the screen opens. */
    public void start() {
        if (started) return;
        started = true;
        load(ForumUrls.HOME);
    }

    public void open(Node node) {
        history.push(current);
        load(node.url);
    }

    public void reload() {
        load(current);
    }

    /** @return false when already at the root, so the caller can leave the screen */
    public boolean up() {
        if (history.isEmpty()) return false;
        load(history.pop());
        return true;
    }

    private void load(String url) {
        current = url;
        state.setValue(new State(url, null, Collections.emptyList(), false, true, null, false));
        c.io.execute(() -> {
            try {
                NodeList list = c.forums.browse(url);
                state.postValue(new State(url, list.title, list.nodes, list.hasThreads, false,
                        list.nodes.isEmpty() && !list.hasThreads ? "Nothing to show on this page." : null, false));
            } catch (SourceException e) {
                boolean verify = e.kind == SourceException.Kind.VERIFICATION_REQUIRED;
                state.postValue(new State(url, null, Collections.emptyList(), false, false, verify ? "XDA Forums wants to check that you are human." : e.getMessage(), verify));
            }
        });
    }

    /** Follows the forum currently shown. */
    public void followCurrent() {
        String url = current;
        c.io.execute(() -> {
            try {
                Forum f = c.forums.addForum(url);
                message.postValue("Following " + (f.title != null ? f.title : f.url));
            } catch (SourceException e) {
                message.postValue("Couldn't follow it: " + e.getMessage());
            }
        });
    }

    public void onVerified() {
        c.forums.markVerified();
    }
}
