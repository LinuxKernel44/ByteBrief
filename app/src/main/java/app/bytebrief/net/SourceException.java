package app.bytebrief.net;

/** Failure while collecting content from a remote source. Never carries credentials or response bodies. */
public class SourceException extends Exception {
    public enum Kind { INVALID_URL, NETWORK, HTTP, RATE_LIMITED, PARSE, TOO_LARGE }

    public final Kind kind;
    public final int httpCode;
    /** Earliest time (epoch millis) a retry makes sense, or 0 when unknown. */
    public final long retryAfterEpochMillis;

    public SourceException(Kind kind, String message, int httpCode, long retryAfterEpochMillis, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpCode = httpCode;
        this.retryAfterEpochMillis = retryAfterEpochMillis;
    }

    public static SourceException network(String message, Throwable cause) {
        return new SourceException(Kind.NETWORK, message, 0, 0, cause);
    }

    public static SourceException parse(String message, Throwable cause) {
        return new SourceException(Kind.PARSE, message, 0, 0, cause);
    }

    public static SourceException invalidUrl(String message) {
        return new SourceException(Kind.INVALID_URL, message, 0, 0, null);
    }

    /** Transient failures worth retrying with backoff. */
    public boolean isRetryable() {
        switch (kind) {
            case NETWORK:
            case RATE_LIMITED:
                return true;
            case HTTP:
                return httpCode >= 500 || httpCode == 408;
            default:
                return false;
        }
    }
}
