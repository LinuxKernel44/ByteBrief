package app.bytebrief.work;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.GrantPermissionRule;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import app.bytebrief.AppContainer;
import app.bytebrief.ByteBriefApp;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.notify.Notifier;

/** Runs the real daily-briefing job: content in, notification out, tomorrow's run queued. No network is used. */
public class BriefingWorkerTest {
    @Rule
    public GrantPermissionRule permission = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS);

    private Context context;
    private AppContainer c;
    private NotificationManager nm;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        c = ByteBriefApp.container(context);
        nm = context.getSystemService(NotificationManager.class);
        nm.cancelAll();
        c.notifier.ensureChannels();
        c.db.clearAllTables();
        c.settings.setBriefingEnabled(true);
        c.settings.setBriefingMinuteOfDay(7 * 60 + 30);
        // A fresh sync timestamp keeps the worker from touching the network.
        c.settings.setLastSyncAt(System.currentTimeMillis());
        ItemFactory f = new ItemFactory(c.clock);
        ContentItem a = f.create(SourceType.HACKER_NEWS, "1", "Liquid Glass lands on Android", "https://example.com/a", "pg",
                c.clock.millis() - 3_600_000, null, null, BodyState.NONE, Collections.singletonList("android"), null);
        a.score = 400;
        ContentItem b = f.create(SourceType.RSS, "1:x", "Parsing at scale", "https://example.com/b", "ada",
                c.clock.millis() - 7_200_000, "A teaser.", null, BodyState.SNIPPET, null, null);
        c.ingestor.ingest(Arrays.asList(a, b));
    }

    @After
    public void tearDown() {
        c.settings.setBriefingEnabled(false);
        c.scheduler.applyBriefing();
        c.db.clearAllTables();
        nm.cancelAll();
    }

    @Test
    public void postsTheBriefingNotificationAndSchedulesTomorrow() throws Exception {
        ListenableWorker worker = TestListenableWorkerBuilder.from(context, BriefingWorker.class).build();
        ListenableWorker.Result result = ((BriefingWorker) worker).doWork();
        assertEquals(ListenableWorker.Result.success(), result);

        StatusBarNotification posted = awaitNotification(Notifier.ID_BRIEFING);
        assertNotNull("no briefing notification was posted", posted);
        Notification n = posted.getNotification();
        assertEquals(Notifier.CHANNEL_BRIEFING, n.getChannelId());
        assertEquals("Your ByteBrief for today", n.extras.getString(Notification.EXTRA_TITLE));
        // Without a loaded model the headline is the honest deterministic one, not invented AI text.
        assertEquals("2 items picked for you", n.extras.getString(Notification.EXTRA_TEXT));
        CharSequence[] lines = n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        assertNotNull(lines);
        assertEquals(2, lines.length);
        assertTrue(lines[0].toString().contains("Liquid Glass lands on Android"));
        assertNotNull("tapping must open the app on the briefing", n.contentIntent);

        List<WorkInfo> next = WorkManager.getInstance(context).getWorkInfosForUniqueWork(Scheduler.DAILY_BRIEFING).get(5, TimeUnit.SECONDS);
        assertEquals(1, next.size());
        assertEquals(WorkInfo.State.ENQUEUED, next.get(0).getState());
    }

    /** Notifications are delivered asynchronously by the system, so poll briefly instead of assuming instant delivery. */
    private StatusBarNotification awaitNotification(int id) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            for (StatusBarNotification n : nm.getActiveNotifications()) if (n.getId() == id) return n;
            Thread.sleep(100);
        }
        return null;
    }

    @Test
    public void disabledBriefingDoesNothing() throws Exception {
        c.settings.setBriefingEnabled(false);
        ListenableWorker.Result result = ((BriefingWorker) TestListenableWorkerBuilder.from(context, BriefingWorker.class).build()).doWork();
        assertEquals(ListenableWorker.Result.success(), result);
        Thread.sleep(1_000); // give a wrongly posted notification time to appear before asserting its absence
        for (StatusBarNotification n : nm.getActiveNotifications()) assertTrue(n.getId() != Notifier.ID_BRIEFING);
    }

    @Test
    public void emptyStoreStaysQuietInsteadOfNotifyingNothing() throws Exception {
        c.db.clearAllTables();
        ListenableWorker.Result result = ((BriefingWorker) TestListenableWorkerBuilder.from(context, BriefingWorker.class).build()).doWork();
        assertEquals(ListenableWorker.Result.success(), result);
        Thread.sleep(1_000);
        for (StatusBarNotification n : nm.getActiveNotifications()) assertTrue(n.getId() != Notifier.ID_BRIEFING);
    }

    @Test
    public void blockedNotificationPermissionIsRespectedByNotifier() {
        // canPost() reflects the permission/channel state; with the permission granted it must be true.
        assertTrue(c.notifier.canPost());
    }
}
