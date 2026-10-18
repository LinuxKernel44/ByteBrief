package app.bytebrief.ui.vm;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import app.bytebrief.AppContainer;
import app.bytebrief.data.db.Forum;
import app.bytebrief.net.SourceException;
import app.bytebrief.settings.AppSettings;

/** Followed XDA forums, and the human-verification step that some of their pages require. */
public final class ForumsViewModel extends ViewModel {
    private final AppContainer c;
    public final LiveData<List<Forum>> forums;
    /** Latest result of an action, shown once. */
    public final MutableLiveData<String> message = new MutableLiveData<>();
    public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    /** Set when an action stopped because the site asks the user to verify; the UI opens the verification screen. */
    public final MutableLiveData<Boolean> verificationRequested = new MutableLiveData<>(false);
    public final MutableLiveData<Long> settingsVersion = new MutableLiveData<>(0L);
    private volatile String pendingAdd;

    ForumsViewModel(AppContainer c) {
        this.c = c;
        forums = c.forums.forums();
    }

    public AppSettings settings() {
        return c.settings;
    }

    public void setWifiOnly(boolean v) {
        c.settings.setForumsWifiOnly(v);
        settingsVersion.setValue(System.nanoTime());
    }

    public void addForum(String url) {
        if (url == null || url.trim().isEmpty()) return;
        busy.setValue(true);
        c.io.execute(() -> {
            try {
                Forum f = c.forums.addForum(url.trim());
                pendingAdd = null;
                message.postValue("Following " + (f.title != null ? f.title : f.url) + (f.lastError != null ? ". " + f.lastError : ""));
            } catch (SourceException e) {
                if (e.kind == SourceException.Kind.VERIFICATION_REQUIRED) {
                    pendingAdd = url.trim();
                    verificationRequested.postValue(true);
                } else {
                    message.postValue("Couldn't add this forum: " + e.getMessage());
                }
            } finally {
                busy.postValue(false);
            }
        });
    }

    public void remove(Forum f) {
        c.forums.remove(f.id, false);
    }

    public void setEnabled(Forum f, boolean enabled) {
        c.forums.setEnabled(f.id, enabled);
    }

    public void setNotify(Forum f, boolean notify) {
        c.forums.setNotify(f.id, notify);
    }

    public void refresh(Forum f) {
        busy.setValue(true);
        c.io.execute(() -> {
            c.sync.syncForum(f.id);
            Forum now = c.db.forumDao().getById(f.id);
            if (now != null && now.needsVerification) verificationRequested.postValue(true);
            else message.postValue("Refreshed " + (f.title != null ? f.title : f.url));
            busy.postValue(false);
        });
    }

    /** The UI has opened the verification screen for the current request. */
    public void verificationShown() {
        verificationRequested.setValue(false);
    }

    /** The user passed the check: forums that were waiting are tried again, and an interrupted "add" is repeated. */
    public void onVerified() {
        c.forums.markVerified();
        String url = pendingAdd;
        if (url != null) addForum(url);
        else c.content.refresh();
    }

    public void consumeMessage() {
        message.setValue(null);
    }
}
