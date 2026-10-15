package app.bytebrief.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;

import app.bytebrief.ByteBriefApp;
import app.bytebrief.notify.Notifier;

/** Entry point. Application logic stays in Java; only the glass UI shell is Kotlin. */
public class MainActivity extends ComponentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The app is dark-only, so system bar icons must always be light, whatever the system theme is.
        EdgeToEdge.enable(this, SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));
        ByteBriefUi.setContent(this, ByteBriefApp.container(this), routeFrom(getIntent()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        ByteBriefUi.openRoute(routeFrom(intent));
    }

    private static String routeFrom(Intent intent) {
        return intent == null ? null : intent.getStringExtra(Notifier.EXTRA_ROUTE);
    }
}
