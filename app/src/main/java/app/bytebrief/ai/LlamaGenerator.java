package app.bytebrief.ai;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import app.bytebrief.llama.LlamaException;
import app.bytebrief.llama.LlamaNative;

/** {@link TextGenerator} backed by llama.cpp through {@link LlamaNative}. Not thread-safe except {@link #cancel()}. */
final class LlamaGenerator implements TextGenerator {
    private final long model;
    private final long session;
    private final int contextTokens;
    private volatile boolean cancelRequested;

    LlamaGenerator(long model, long session, int contextTokens) {
        this.model = model;
        this.session = session;
        this.contextTokens = contextTokens;
    }

    @Override
    public int contextTokens() {
        return contextTokens;
    }

    @Override
    public int countTokens(String text) {
        int n = LlamaNative.countTokens(model, text.getBytes(StandardCharsets.UTF_8));
        // Fall back to a conservative estimate rather than under-counting.
        return n >= 0 ? n : text.length() / 2 + 1;
    }

    @Override
    public String formatChat(String system, String user) throws GenerationException {
        try {
            byte[] out = LlamaNative.formatChat(model, new String[]{"system", "user"},
                    new byte[][]{system.getBytes(StandardCharsets.UTF_8), user.getBytes(StandardCharsets.UTF_8)}, true);
            return new String(out, StandardCharsets.UTF_8);
        } catch (LlamaException e) {
            throw map(e);
        }
    }

    @Override
    public Generation generate(String prompt, int maxTokens, float temperature, @Nullable String grammar, @Nullable Sink sink)
            throws GenerationException {
        cancelRequested = false;
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        StringBuilder text = new StringBuilder();
        try {
            int n = LlamaNative.generate(session, prompt.getBytes(StandardCharsets.UTF_8), maxTokens, temperature, 0.95f, -1,
                    grammar == null ? null : grammar.getBytes(StandardCharsets.UTF_8), piece -> {
                        raw.write(piece, 0, piece.length);
                        String s = new String(piece, StandardCharsets.UTF_8);
                        text.append(s);
                        return sink == null || sink.onText(s);
                    });
            boolean cancelled = cancelRequested;
            return new Generation(text.toString(), n, !cancelled && n >= maxTokens, cancelled);
        } catch (LlamaException e) {
            throw map(e);
        }
    }

    @Override
    public void cancel() {
        cancelRequested = true;
        LlamaNative.cancel(session);
    }

    private static GenerationException map(LlamaException e) {
        switch (e.getCode()) {
            case LlamaException.CODE_OUT_OF_MEMORY:
                return new GenerationException(GenerationException.Kind.OUT_OF_MEMORY, e.getMessage(), e);
            case LlamaException.CODE_CONTEXT_OVERFLOW:
                return new GenerationException(GenerationException.Kind.CONTEXT_OVERFLOW, e.getMessage(), e);
            default:
                return new GenerationException(GenerationException.Kind.OTHER, e.getMessage(), e);
        }
    }

    void close() {
        LlamaNative.freeSession(session);
        LlamaNative.freeModel(model);
    }
}
