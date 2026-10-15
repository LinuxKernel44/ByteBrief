package app.bytebrief.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import app.bytebrief.AppContainer;
import app.bytebrief.ByteBriefApp;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;

/**
 * Drives the real app on a real (emulated) display with the real Liquid Glass rendering. A native
 * crash in the render thread (as happens if a glass surface samples its own ancestor layer) kills
 * the process, which these tests detect.
 */
public class UiSmokeTest {
    private static final long TIMEOUT = 8_000;
    /** On failure, leaves a screenshot and the view hierarchy in /data/local/tmp for post-mortem (adb pull). */
    @Rule
    public TestWatcher artifacts = new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description d) {
            try {
                String base = "/data/local/tmp/fail-" + d.getMethodName();
                device.executeShellCommand("screencap -p " + base + ".png");
                device.executeShellCommand("uiautomator dump " + base + ".xml");
            } catch (Exception ignored) {
                // Diagnostics must never mask the real failure.
            }
        }
    };

    private UiDevice device;
    private AppContainer container;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        container = ByteBriefApp.container(context);
        container.settings.setOnboardingDone(true);
        container.settings.setOpaqueSurfaces(false);
        container.db.clearAllTables();
        ItemFactory factory = new ItemFactory(container.clock);
        ContentItem a = factory.create(SourceType.RSS, "1:a", "Smoke test article about parsers", "https://example.com/a", "Ada",
                container.clock.millis() - 3_600_000, "A teaser sentence for the smoke test.",
                ("Parsers turn text into structure. This is stored article text used by the UI smoke test. ").repeat(8),
                BodyState.FULL, Arrays.asList("parsers"), "en");
        ContentItem b = factory.create(SourceType.HACKER_NEWS, "7", "Another story on Hacker News", "https://example.com/b", "pg",
                container.clock.millis() - 7_200_000, null, null, BodyState.NONE, Collections.singletonList("hacker-news"), null);
        b.score = 120;
        container.ingestor.ingest(Arrays.asList(a, b));
    }

    @After
    public void tearDown() {
        container.db.clearAllTables();
    }

    private void launch() {
        Intent i = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
        assertTrue("app window did not appear", device.wait(Until.hasObject(By.pkg(context.getPackageName()).depth(0)), TIMEOUT));
    }

    private UiObject2 desc(String d) {
        UiObject2 o = device.wait(Until.findObject(By.desc(d)), TIMEOUT);
        assertNotNull("not found on screen: " + d, o);
        return o;
    }

    /** Finds and clicks in one step, retrying when the node was replaced by a recomposition in between. */
    private void clickDesc(String d) {
        clickWhere(By.desc(d), d);
    }

    private void clickWhere(androidx.test.uiautomator.BySelector selector, String what) {
        for (int attempt = 0; attempt < 5; attempt++) {
            device.waitForIdle();
            UiObject2 o = device.wait(Until.findObject(selector), TIMEOUT);
            assertNotNull("not found on screen: " + what, o);
            try {
                o.click();
                return;
            } catch (androidx.test.uiautomator.StaleObjectException e) {
                // The UI changed between find and click; look again.
            }
        }
        throw new AssertionError("could not click " + what);
    }

    private void assertAlive() {
        assertTrue("the app process died (native crash?)", device.wait(Until.hasObject(By.pkg(context.getPackageName())), 2_000));
    }

    @Test
    public void launchesWithGlassAndShowsEveryTab() {
        launch();
        for (String tab : new String[]{"For You", "Browse", "Briefing", "Saved", "More"}) {
            clickDesc(tab);
            device.waitForIdle();
            assertAlive();
        }
    }

    @Test
    public void showsSeededItemsAndOpensDetail() {
        launch();
        clickDesc("Browse");
        clickWhere(By.descContains("Smoke test article about parsers"), "seeded item in Browse");
        // Detail shows the stored text and the offline indicator.
        assertNotNull(device.wait(Until.findObject(By.descContains("Offline: Full text saved")), TIMEOUT));
        assertAlive();
        device.pressBack();
        assertNotNull(device.wait(Until.findObject(By.desc("Browse")), TIMEOUT));
    }

    @Test
    public void everySecondaryScreenOpensAndGoesBack() {
        launch();
        clickDesc("More");
        for (String row : new String[]{"Language models", "Sources", "Settings", "About"}) {
            clickWhere(By.descStartsWith(row), "More row " + row);
            device.waitForIdle();
            assertAlive();
            device.pressBack();
            assertNotNull(device.wait(Until.findObject(By.desc("More")), TIMEOUT));
        }
    }

    @Test
    public void searchFindsStoredText() {
        launch();
        clickDesc("For You");
        clickDesc("Search");
        clickWhere(By.desc("Search titles, text and topics"), "search field");
        // setText does not always reach a Compose text field; type through the input service like a user would.
        try {
            device.executeShellCommand("input text parsers");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        assertNotNull("search result missing", device.wait(Until.findObject(By.descContains("Smoke test article about parsers")), TIMEOUT));
        assertAlive();
    }

    @Test
    public void solidSurfaceModeAlsoRenders() {
        container.settings.setOpaqueSurfaces(true);
        launch();
        clickDesc("Browse");
        assertNotNull(device.wait(Until.findObject(By.descContains("Smoke test article about parsers")), TIMEOUT));
        assertAlive();
        container.settings.setOpaqueSurfaces(false);
    }

    @Test
    public void briefingWorksWithoutAModel() {
        launch();
        clickDesc("Briefing");
        assertNotNull("briefing rows missing", device.wait(Until.findObject(By.descContains("Smoke test article about parsers")), TIMEOUT));
        assertEquals(false, container.engine.isReady());
    }
}
