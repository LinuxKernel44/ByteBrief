package app.bytebrief;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

/** Application entry point: creates the dependency container and registers background work. */
public final class ByteBriefApp extends Application {
    private static volatile AppContainer container;
    private static volatile int startedActivities;

    public static AppContainer container(Context context) {
        AppContainer c = container;
        if (c == null) {
            synchronized (ByteBriefApp.class) {
                c = container;
                if (c == null) {
                    c = new AppContainer(context.getApplicationContext());
                    container = c;
                }
            }
        }
        return c;
    }

    /** True while any activity is visible; background jobs use it to decide whether to free the model. */
    public static boolean isForeground() {
        return startedActivities > 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppContainer c = container(this);
        c.notifier.ensureChannels();
        c.scheduler.applySync();
        c.scheduler.applyBriefing();
        c.io.execute(c.models::autoLoad);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) { startedActivities++; }
            @Override public void onActivityStopped(Activity a) { startedActivities--; }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }
}
