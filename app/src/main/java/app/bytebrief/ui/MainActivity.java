package app.bytebrief.ui;

import android.os.Bundle;

import androidx.activity.ComponentActivity;

import app.bytebrief.ui.glass.GlassSpikeBridge;

/** Entry point. Application logic stays in Java; only the glass UI shell is Kotlin. */
public class MainActivity extends ComponentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GlassSpikeBridge.setContent(this);
    }
}
