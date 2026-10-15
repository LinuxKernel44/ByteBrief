package app.bytebrief.ai;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Handle to a running or queued AI operation. */
public final class AiTask<T> {
    private final CompletableFuture<AiResult<T>> future = new CompletableFuture<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile Runnable onCancel = () -> { };

    /** Completes with the result; never completes exceptionally. */
    public CompletableFuture<AiResult<T>> future() {
        return future;
    }

    /** Requests cancellation. A queued task completes as CANCELLED without running; a running one stops soon. */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) onCancel.run();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    void setOnCancel(Runnable r) {
        this.onCancel = r;
        if (cancelled.get()) r.run();
    }

    void complete(AiResult<T> result) {
        future.complete(result);
    }
}
