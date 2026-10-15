package app.bytebrief.net;

import androidx.annotation.Nullable;

/** Outcome of a successful HTTP GET (2xx or 304). */
public final class FetchResult {
    public final int code;
    /** Null when {@link #notModified()}. */
    @Nullable
    public final String body;
    @Nullable
    public final String etag;
    @Nullable
    public final String lastModified;
    /** URL after redirects. */
    public final String finalUrl;
    /** GitHub-style quota, or -1 when the server did not report one. */
    public final int rateLimitRemaining;
    /** Media type without parameters, lower-case (e.g. "text/html"), or null when absent. */
    @Nullable
    public final String contentType;

    public FetchResult(int code, @Nullable String body, @Nullable String etag, @Nullable String lastModified,
                       String finalUrl, int rateLimitRemaining, @Nullable String contentType) {
        this.contentType = contentType;
        this.code = code;
        this.body = body;
        this.etag = etag;
        this.lastModified = lastModified;
        this.finalUrl = finalUrl;
        this.rateLimitRemaining = rateLimitRemaining;
    }

    public boolean notModified() {
        return code == 304;
    }
}
