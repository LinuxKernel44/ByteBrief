package app.bytebrief.ai;

import androidx.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Handle to an item-level AI request, with its result and cancellation. */
public final class AiCall<T> {
    public static final class Outcome<T> {
        public final AiResult<T> result;
        /** The value came from local storage; no inference ran. */
        public final boolean fromCache;
        /** The cached value was produced by a different model than the one currently loaded. */
        public final boolean stale;
        @Nullable
        public final String modelId;

        Outcome(AiResult<T> result, boolean fromCache, boolean stale, @Nullable String modelId) {
            this.result = result;
            this.fromCache = fromCache;
            this.stale = stale;
            this.modelId = modelId;
        }
    }

    private final CompletableFuture<Outcome<T>> future = new CompletableFuture<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile Runnable onCancel = () -> { };

    public CompletableFuture<Outcome<T>> future() {
        return future;
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) onCancel.run();
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    void setOnCancel(Runnable r) {
        onCancel = r;
        if (cancelled.get()) r.run();
    }

    void complete(Outcome<T> o) {
        future.complete(o);
    }

    static <T> AiCall<T> completed(AiResult<T> r) {
        AiCall<T> c = new AiCall<>();
        c.complete(new Outcome<>(r, false, false, null));
        return c;
    }
}
