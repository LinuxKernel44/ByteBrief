package app.bytebrief.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

import app.bytebrief.R;
import app.bytebrief.ui.MainActivity;

/** Posts notifications. Every method is a silent no-op when the user has not granted notification permission. */
public final class Notifier {
    public static final String CHANNEL_BRIEFING = "briefing";
    public static final String CHANNEL_RELEASES = "releases";
    public static final String CHANNEL_MODELS = "models";
    public static final String CHANNEL_FORUMS = "forums";
    public static final String EXTRA_ROUTE = "route";
    public static final String ROUTE_BRIEFING = "briefing";
    public static final String ROUTE_RELEASES = "releases";
    public static final String ROUTE_MODELS = "models";
    public static final String ROUTE_FORUMS = "forums";

    public static final int ID_BRIEFING = 1001;
    public static final int ID_RELEASES = 1002;
    public static final int ID_MODEL_DOWNLOAD = 1003;
    public static final int ID_FORUMS = 1004;

    private final Context context;

    public Notifier(Context context) {
        this.context = context.getApplicationContext();
    }

    public void ensureChannels() {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_BRIEFING, "Daily briefing", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_RELEASES, "Release alerts", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_FORUMS, "Forum threads", NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel models = new NotificationChannel(CHANNEL_MODELS, "Model downloads", NotificationManager.IMPORTANCE_LOW);
        models.setShowBadge(false);
        nm.createNotificationChannel(models);
    }

    public boolean canPost() {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public void postBriefing(String headline, List<String> topTitles) {
        if (!canPost()) return;
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (String t : topTitles) style.addLine(t);
        post(ID_BRIEFING, new NotificationCompat.Builder(context, CHANNEL_BRIEFING)
                .setSmallIcon(R.drawable.ic_stat_bytebrief)
                .setContentTitle("Your ByteBrief for today")
                .setContentText(headline)
                .setStyle(style)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setContentIntent(open(ROUTE_BRIEFING))
                .build());
    }

    public void postReleases(int count, List<String> titles) {
        if (!canPost() || count <= 0) return;
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (String t : titles) style.addLine(t);
        post(ID_RELEASES, new NotificationCompat.Builder(context, CHANNEL_RELEASES)
                .setSmallIcon(R.drawable.ic_stat_bytebrief)
                .setContentTitle(count == 1 ? "New release" : count + " new releases")
                .setContentText(titles.isEmpty() ? "" : titles.get(0))
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(open(ROUTE_RELEASES))
                .build());
    }

    public void postForumThreads(List<String> titles) {
        if (!canPost() || titles.isEmpty()) return;
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (String t : titles.subList(0, Math.min(5, titles.size()))) style.addLine(t);
        post(ID_FORUMS, new NotificationCompat.Builder(context, CHANNEL_FORUMS)
                .setSmallIcon(R.drawable.ic_stat_bytebrief)
                .setContentTitle(titles.size() == 1 ? "New forum thread" : titles.size() + " new forum threads")
                .setContentText(titles.get(0))
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(open(ROUTE_FORUMS))
                .build());
    }

    /** Ongoing notification for the foreground model download. */
    public Notification modelDownload(String title, int percent, boolean indeterminate, PendingIntent cancel) {
        return new NotificationCompat.Builder(context, CHANNEL_MODELS)
                .setSmallIcon(R.drawable.ic_stat_bytebrief)
                .setContentTitle(title)
                .setContentText(indeterminate ? "Preparing…" : percent + "%")
                .setProgress(100, percent, indeterminate)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open(ROUTE_MODELS))
                .addAction(0, "Pause", cancel)
                .build();
    }

    private void post(int id, Notification n) {
        try {
            NotificationManagerCompat.from(context).notify(id, n);
        } catch (SecurityException ignored) {
            // Permission revoked between the check and the call.
        }
    }

    private PendingIntent open(String route) {
        Intent i = new Intent(context, MainActivity.class).putExtra(EXTRA_ROUTE, route)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, route.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
