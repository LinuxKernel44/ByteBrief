package app.bytebrief.ui.vm;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.security.GeneralSecurityException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import app.bytebrief.AppContainer;
import app.bytebrief.data.db.Feed;
import app.bytebrief.data.db.TrackedRepo;
import app.bytebrief.domain.SourceType;
import app.bytebrief.net.SourceException;
import app.bytebrief.repo.FeedRepository;
import app.bytebrief.settings.AppSettings;
import app.bytebrief.source.github.TrendingSource;
import app.bytebrief.source.hn.HackerNewsSource;

/** Source configuration: built-in source switches, RSS feeds with OPML, followed repositories and the optional token. */
public final class SourcesViewModel extends ViewModel {
    private final AppContainer c;
    public final LiveData<List<Feed>> feeds;
    public final LiveData<List<TrackedRepo>> repos;
    /** Latest result of an action, shown once. */
    public final MutableLiveData<String> message = new MutableLiveData<>();
    public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    public final MutableLiveData<Long> settingsVersion = new MutableLiveData<>(0L);

    SourcesViewModel(AppContainer c) {
        this.c = c;
        feeds = c.feeds.feeds();
        repos = c.feeds.trackedRepos();
    }

    public AppSettings settings() {
        return c.settings;
    }

    private void bump() {
        settingsVersion.postValue(System.nanoTime());
    }

    // ---- built-in sources ----------------------------------------------------------------------

    public void setSourceEnabled(SourceType type, boolean enabled) {
        c.settings.setSourceEnabled(type, enabled);
        bump();
    }

    public void toggleHnFeed(HackerNewsSource.Feed feed) {
        Set<HackerNewsSource.Feed> current = EnumSet.noneOf(HackerNewsSource.Feed.class);
        current.addAll(c.settings.hackerNewsFeeds());
        if (!current.remove(feed)) current.add(feed);
        if (current.isEmpty()) current.add(HackerNewsSource.Feed.TOP); // at least one list stays selected
        c.settings.setHackerNewsFeeds(current);
        bump();
    }

    public void setTrendingPeriod(TrendingSource.Period p) {
        c.settings.setTrendingPeriod(p);
        bump();
    }

    public void setTrendingLanguage(String language) {
        c.settings.setTrendingLanguage(language);
        bump();
    }

    public void setIncludePrereleases(boolean v) {
        c.settings.setIncludePrereleases(v);
        bump();
    }

    // ---- feeds ---------------------------------------------------------------------------------

    public void addFeed(String url) {
        if (url == null || url.trim().isEmpty()) return;
        busy.setValue(true);
        c.io.execute(() -> {
            try {
                Feed f = c.feeds.addFeed(url.trim());
                message.postValue("Added " + (f.title != null ? f.title : f.url));
            } catch (SourceException e) {
                message.postValue("Couldn't add this feed: " + friendly(e));
            } finally {
                busy.postValue(false);
            }
        });
    }

    public void removeFeed(Feed f) {
        c.feeds.removeFeed(f.id, false);
    }

    public void setFeedEnabled(Feed f, boolean enabled) {
        c.feeds.setFeedEnabled(f.id, enabled);
    }

    public void refreshFeed(Feed f) {
        busy.setValue(true);
        c.io.execute(() -> {
            c.sync.syncFeed(f.id);
            busy.postValue(false);
            message.postValue("Refreshed " + (f.title != null ? f.title : f.url));
        });
    }

    public void importOpml(String opml) {
        busy.setValue(true);
        c.io.execute(() -> {
            try {
                FeedRepository.ImportSummary s = c.feeds.importOpml(opml);
                message.postValue("Imported " + s.added + " feeds" + (s.alreadyPresent > 0 ? ", " + s.alreadyPresent + " already present" : "")
                        + (s.rejected > 0 ? ", " + s.rejected + " skipped (unsafe or invalid address)" : "") + ". They load on the next refresh.");
            } catch (SourceException e) {
                message.postValue("That file isn't a valid OPML export.");
            } finally {
                busy.postValue(false);
            }
        });
    }

    /** Blocking and small; call from a background thread. */
    public String exportOpml() {
        return c.feeds.exportOpml();
    }

    // ---- repositories --------------------------------------------------------------------------

    public void trackRepo(String input) {
        if (input == null || input.trim().isEmpty()) return;
        busy.setValue(true);
        c.io.execute(() -> {
            try {
                TrackedRepo r = c.feeds.trackRepo(input.trim());
                message.postValue("Following " + r.owner + "/" + r.name + ". Releases appear after the next refresh.");
            } catch (SourceException e) {
                message.postValue(e.httpCode == 404 ? "That repository wasn't found on GitHub." : "Couldn't follow it: " + friendly(e));
            } finally {
                busy.postValue(false);
            }
        });
    }

    public void untrack(TrackedRepo r) {
        c.feeds.untrackRepo(r.id);
    }

    public void setRepoEnabled(TrackedRepo r, boolean enabled) {
        c.feeds.setRepoEnabled(r, enabled);
    }

    // ---- GitHub token --------------------------------------------------------------------------

    public boolean hasToken() {
        return c.tokens.hasToken();
    }

    public void saveToken(String token) {
        try {
            c.tokens.set(token);
            c.settings.setGithubTokenPresent(true);
            message.setValue("Token saved securely on this device.");
        } catch (IllegalArgumentException e) {
            message.setValue("That doesn't look like a GitHub token.");
        } catch (GeneralSecurityException e) {
            message.setValue("The token could not be stored securely, so it was not saved.");
        }
        bump();
    }

    public void clearToken() {
        c.tokens.clear();
        c.settings.setGithubTokenPresent(false);
        message.setValue("Token removed.");
        bump();
    }

    public void consumeMessage() {
        message.setValue(null);
    }

    private static String friendly(SourceException e) {
        switch (e.kind) {
            case INVALID_URL: return e.getMessage();
            case NETWORK: return "no connection or the server didn't respond.";
            case RATE_LIMITED: return "rate limited; try again later.";
            case PARSE: return e.getMessage();
            case TOO_LARGE: return "the response was too large.";
            default: return "the server answered " + e.httpCode + ".";
        }
    }
}
