package app.bytebrief.ui.vm;

import androidx.annotation.Nullable;

import app.bytebrief.ai.AiResult;

/** Render state of one AI result in the UI. */
public final class AiUi<T> {
    public enum State { IDLE, RUNNING, DONE, FAILED }

    public final State state;
    @Nullable public final T value;
    public final boolean fromCache;
    public final boolean stale;
    @Nullable public final AiResult.Status status;
    @Nullable public final String message;
    @Nullable public final String stage;

    private AiUi(State state, @Nullable T value, boolean fromCache, boolean stale, @Nullable AiResult.Status status,
                 @Nullable String message, @Nullable String stage) {
        this.state = state;
        this.value = value;
        this.fromCache = fromCache;
        this.stale = stale;
        this.status = status;
        this.message = message;
        this.stage = stage;
    }

    public static <T> AiUi<T> idle() {
        return new AiUi<>(State.IDLE, null, false, false, null, null, null);
    }

    public static <T> AiUi<T> running(@Nullable String stage) {
        return new AiUi<>(State.RUNNING, null, false, false, null, null, stage);
    }

    public static <T> AiUi<T> done(T value, boolean fromCache, boolean stale) {
        return new AiUi<>(State.DONE, value, fromCache, stale, AiResult.Status.SUCCESS, null, null);
    }

    public static <T> AiUi<T> failed(AiResult.Status status, @Nullable String message) {
        return new AiUi<>(State.FAILED, null, false, false, status, AiMessages.describe(status, message), null);
    }

    public AiUi<T> withStage(String stage) {
        return new AiUi<>(state, value, fromCache, stale, status, message, stage);
    }
}
