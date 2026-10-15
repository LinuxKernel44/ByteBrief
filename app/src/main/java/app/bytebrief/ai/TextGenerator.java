package app.bytebrief.ai;

import androidx.annotation.Nullable;

/** The only thing the AI pipeline needs from an inference runtime. Production uses llama.cpp. */
interface TextGenerator {
    /** Receives output text as it is produced; return false to stop. */
    interface Sink {
        boolean onText(String piece);
    }

    final class Generation {
        final String text;
        final int tokens;
        /** True when generation stopped because it reached the token limit, not because the model finished. */
        final boolean hitLimit;
        final boolean cancelled;

        Generation(String text, int tokens, boolean hitLimit, boolean cancelled) {
            this.text = text;
            this.tokens = tokens;
            this.hitLimit = hitLimit;
            this.cancelled = cancelled;
        }
    }

    class GenerationException extends Exception {
        enum Kind { OUT_OF_MEMORY, CONTEXT_OVERFLOW, OTHER }

        final Kind kind;

        GenerationException(Kind kind, String message, @Nullable Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }
    }

    int contextTokens();

    int countTokens(String text);

    /** Applies the model's chat template to a system and a user message, ending at the assistant turn. */
    String formatChat(String system, String user) throws GenerationException;

    Generation generate(String prompt, int maxTokens, float temperature, @Nullable String grammar, @Nullable Sink sink)
            throws GenerationException;

    /** Stops a running {@link #generate}; safe from any thread. */
    void cancel();
}
