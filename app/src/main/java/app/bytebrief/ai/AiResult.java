package app.bytebrief.ai;

import androidx.annotation.Nullable;

/**
 * Typed outcome of an AI operation. A failure never carries invented content: callers must show the
 * status to the user rather than substitute text.
 */
public final class AiResult<T> {
    public enum Status {
        SUCCESS,
        /** The caller cancelled the task. */
        CANCELLED,
        /** No model is downloaded, loaded or ready. */
        MODEL_UNAVAILABLE,
        /** The loaded model or runtime cannot perform this operation. */
        UNSUPPORTED,
        /** The model output could not be validated, even after the bounded retry. */
        MALFORMED_OUTPUT,
        /** Not enough memory to load the model or run the context. */
        OUT_OF_MEMORY,
        /** The source text is too short to analyze; the model was not asked. */
        INSUFFICIENT_CONTENT,
        /** A cache-only lookup found nothing stored; no inference was attempted. */
        NOT_CACHED,
        /** Any other runtime failure. */
        ERROR
    }

    public final Status status;
    @Nullable
    public final T value;
    @Nullable
    public final String message;

    private AiResult(Status status, @Nullable T value, @Nullable String message) {
        this.status = status;
        this.value = value;
        this.message = message;
    }

    public static <T> AiResult<T> success(T value) {
        return new AiResult<>(Status.SUCCESS, value, null);
    }

    public static <T> AiResult<T> failure(Status status, String message) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("SUCCESS needs a value");
        return new AiResult<>(status, null, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /** Converts the failure to another value type; only valid for failures. */
    public <R> AiResult<R> asFailure() {
        if (isSuccess()) throw new IllegalStateException("not a failure");
        return new AiResult<>(status, null, message);
    }

    @Override
    public String toString() {
        return status + (message != null ? ": " + message : "");
    }
}
