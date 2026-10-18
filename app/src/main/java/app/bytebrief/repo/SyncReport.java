package app.bytebrief.repo;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Outcome of one synchronization run, per source. A failing source never aborts the others. */
public final class SyncReport {
    public enum Status { OK, PARTIAL, FAILED, SKIPPED }

    public static final class SourceOutcome {
        public Status status = Status.OK;
        public final Ingestor.Stats stats = new Ingestor.Stats();
        @Nullable public String error;
        public boolean retryable;
        /** Epoch millis before which a retry is pointless (rate limits), or 0. */
        public long retryAfter;

        void fail(String message, boolean retryable, long retryAfter) {
            this.status = stats.inserted + stats.updated + stats.unchanged > 0 ? Status.PARTIAL : Status.FAILED;
            this.error = message;
            this.retryable = retryable;
            this.retryAfter = Math.max(this.retryAfter, retryAfter);
        }
    }

    public final Map<String, SourceOutcome> outcomes = new LinkedHashMap<>();
    /** Releases inserted during this run (drives release notifications). */
    public int newReleases;
    /** Titles of new threads in forums that have alerts on (drives forum notifications). */
    public final java.util.List<String> newForumThreads = new java.util.ArrayList<>();
    public int pruned;
    public long finishedAt;

    SourceOutcome outcome(String key) {
        SourceOutcome o = outcomes.get(key);
        if (o == null) {
            o = new SourceOutcome();
            outcomes.put(key, o);
        }
        return o;
    }

    public int totalNew() {
        int n = 0;
        for (SourceOutcome o : outcomes.values()) n += o.stats.inserted;
        return n;
    }

    /** True when something failed in a way that retrying later could fix and nothing succeeded. */
    public boolean shouldRetry() {
        boolean anyOk = false;
        boolean retryableFailure = false;
        for (SourceOutcome o : outcomes.values()) {
            if (o.status == Status.OK || o.status == Status.PARTIAL) anyOk = true;
            if ((o.status == Status.FAILED || o.status == Status.PARTIAL) && o.retryable) retryableFailure = true;
        }
        return retryableFailure && !anyOk;
    }

    public boolean allFailed() {
        boolean any = false;
        for (SourceOutcome o : outcomes.values()) {
            if (o.status == Status.SKIPPED) continue;
            any = true;
            if (o.status != Status.FAILED) return false;
        }
        return any;
    }
}
