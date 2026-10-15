package app.bytebrief.ui.vm;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import app.bytebrief.AppContainer;
import app.bytebrief.data.db.Affinity;
import app.bytebrief.settings.AppSettings;

public final class SettingsViewModel extends ViewModel {
    private final AppContainer c;
    public final LiveData<List<Affinity>> interests;
    /** Bumped after every change so screens re-read the settings. */
    public final MutableLiveData<Long> version = new MutableLiveData<>(0L);

    SettingsViewModel(AppContainer c) {
        this.c = c;
        interests = c.db.affinityDao().observeExplicitTopics();
    }

    public AppSettings s() {
        return c.settings;
    }

    private void bump() {
        version.postValue(System.nanoTime());
    }

    public boolean notificationsAllowed() {
        return c.notifier.canPost();
    }

    public void setBackgroundSync(boolean v) { c.settings.setBackgroundSyncEnabled(v); c.scheduler.applySync(); bump(); }
    public void setSyncInterval(int hours) { c.settings.setSyncIntervalHours(hours); c.scheduler.applySync(); bump(); }
    public void setUnmeteredOnly(boolean v) { c.settings.setSyncOnUnmeteredOnly(v); c.scheduler.applySync(); bump(); }
    public void setRetention(int days) { c.settings.setRetentionDays(days); bump(); }
    public void setBriefing(boolean v) { c.settings.setBriefingEnabled(v); c.scheduler.applyBriefing(); bump(); }
    public void setBriefingTime(int minuteOfDay) { c.settings.setBriefingMinuteOfDay(minuteOfDay); c.scheduler.applyBriefing(); bump(); }
    public void setReleaseAlerts(boolean v) { c.settings.setReleaseAlertsEnabled(v); bump(); }
    public void setLearning(boolean v) { c.settings.setLearningEnabled(v); bump(); }
    public void setOpaque(boolean v) { c.settings.setOpaqueSurfaces(v); bump(); }
    public void setCacheBookmarks(boolean v) { c.settings.setCacheBookmarkedText(v); bump(); }
    public void setShowImages(boolean v) { c.settings.setShowImages(v); bump(); }
    public void setMeteredModelDownload(boolean v) { c.settings.setAllowMeteredModelDownload(v); bump(); }

    public void addInterest(String topic) {
        c.io.execute(() -> c.personalizer.addInterest(topic));
    }

    public void removeInterest(String topic) {
        c.io.execute(() -> c.personalizer.removeInterest(topic));
    }

    public void resetLearned() {
        c.io.execute(() -> c.personalizer.resetLearned());
    }

    public void resetAll() {
        c.io.execute(() -> c.personalizer.resetAll());
    }

    public void clearAiOutputs() {
        c.io.execute(() -> c.db.aiOutputDao().deleteAll());
    }

    public boolean onboardingDone() {
        return c.settings.onboardingDone();
    }

    public void finishOnboarding() {
        c.settings.setOnboardingDone(true);
        bump();
    }
}
