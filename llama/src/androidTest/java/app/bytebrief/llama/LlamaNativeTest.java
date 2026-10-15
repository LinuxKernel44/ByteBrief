package app.bytebrief.llama;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Runs real inference. Requires a GGUF model, supplied with
 * {@code -e modelPath /absolute/path/model.gguf}; otherwise the tests are skipped (not passed).
 */
public class LlamaNativeTest {
    private long model;
    private long session;

    @Before
    public void setUp() {
        Bundle args = InstrumentationRegistry.getArguments();
        String path = args.getString("modelPath");
        assumeTrue("modelPath instrumentation argument not provided", path != null);
        assumeTrue("model file not readable: " + path, new File(path).canRead());
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LlamaNative.init(context.getApplicationInfo().nativeLibraryDir);
        model = LlamaNative.loadModel(path, 0);
        session = LlamaNative.createSession(model, 2048, 4);
    }

    @After
    public void tearDown() {
        if (session != 0) LlamaNative.freeSession(session);
        if (model != 0) LlamaNative.freeModel(model);
    }

    private byte[] chatPrompt(String user) {
        return LlamaNative.formatChat(model, new String[]{"system", "user"},
                new byte[][]{"You are a concise assistant.".getBytes(StandardCharsets.UTF_8),
                        user.getBytes(StandardCharsets.UTF_8)}, true);
    }

    @Test
    public void generatesTextForSimplePrompt() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long start = System.nanoTime();
        int n = LlamaNative.generate(session, chatPrompt("Reply with exactly: Paris is the capital of France."),
                48, 0f, 1f, 1, piece -> {
                    out.write(piece, 0, piece.length);
                    return true;
                });
        double seconds = (System.nanoTime() - start) / 1e9;
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        android.util.Log.i("LlamaNativeTest", "tokens=" + n + " seconds=" + seconds + " text=" + text);
        assertTrue("expected tokens", n > 0);
        assertFalse("expected non-empty text", text.trim().isEmpty());
    }

    @Test
    public void cancelStopsGeneration() throws Exception {
        final int[] pieces = {0};
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            LlamaNative.cancel(session);
        });
        canceller.start();
        int n = LlamaNative.generate(session, chatPrompt("Write a very long story about a dragon."),
                1000, 0.8f, 0.95f, 7, piece -> {
                    pieces[0]++;
                    return true;
                });
        canceller.join();
        assertTrue("generation should have stopped early, got " + n, n < 1000);
    }

    @Test
    public void rejectsPromptThatOverflowsContext() {
        try {
            LlamaNative.generate(session, chatPrompt("hi"), 5000, 0f, 1f, 1, piece -> true);
            throw new AssertionError("expected LlamaException");
        } catch (LlamaException e) {
            assertEquals(LlamaException.CODE_CONTEXT_OVERFLOW, e.getCode());
        }
    }

    @Test
    public void streamsMultibyteTextWithoutCorruption() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LlamaNative.generate(session, chatPrompt("Say hello in Japanese and French using native scripts."),
                64, 0f, 1f, 1, piece -> {
                    // Every chunk must itself be valid UTF-8 (no split sequences).
                    String s = new String(piece, StandardCharsets.UTF_8);
                    assertFalse("chunk has replacement char", s.contains("�"));
                    out.write(piece, 0, piece.length);
                    return true;
                });
    }
}
