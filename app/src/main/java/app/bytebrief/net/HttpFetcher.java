package app.bytebrief.net;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import app.bytebrief.domain.UrlCanonicalizer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

/**
 * The only component that talks to the network. It enforces an http(s) allow-list, a response size
 * cap, no https-to-http redirect downgrades, and maps failures to {@link SourceException}.
 */
public class HttpFetcher {
    public static final String USER_AGENT = "ByteBrief/0.1 (Android; +https://github.com/LinuxKernel44/ByteBrief)";
    public static final long DEFAULT_MAX_BYTES = 4L * 1024 * 1024;

    private final OkHttpClient client;
    private final Clock clock;

    public HttpFetcher(OkHttpClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(40, TimeUnit.SECONDS)
                // A secure page must never silently redirect us to cleartext.
                .followSslRedirects(false)
                .build();
    }

    public FetchResult get(String url, @Nullable Map<String, String> headers, long maxBytes) throws SourceException {
        if (!UrlCanonicalizer.isHttpUrl(url)) {
            throw SourceException.invalidUrl("Only http(s) URLs are allowed");
        }
        Request.Builder rb = new Request.Builder().url(url).header("User-Agent", USER_AGENT);
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) rb.header(h.getKey(), h.getValue());
        }
        try (Response response = client.newCall(rb.build()).execute()) {
            int code = response.code();
            int remaining = parseInt(response.header("x-ratelimit-remaining"), -1);
            if (code == 304) {
                return new FetchResult(304, null, response.header("ETag"), response.header("Last-Modified"),
                        response.request().url().toString(), remaining);
            }
            if (code == 429 || (code == 403 && remaining == 0)) {
                throw new SourceException(SourceException.Kind.RATE_LIMITED, "Rate limited by server (HTTP " + code + ")",
                        code, retryAfterMillis(response), null);
            }
            if (code < 200 || code >= 300) {
                throw new SourceException(SourceException.Kind.HTTP, "HTTP " + code, code, retryAfterMillis(response), null);
            }
            ResponseBody body = response.body();
            String text = readLimited(body, maxBytes);
            return new FetchResult(code, text, response.header("ETag"), response.header("Last-Modified"),
                    response.request().url().toString(), remaining);
        } catch (SourceException e) {
            throw e;
        } catch (IOException e) {
            throw SourceException.network(e.getClass().getSimpleName() + " while fetching", e);
        }
    }

    public FetchResult get(String url, @Nullable Map<String, String> headers) throws SourceException {
        return get(url, headers, DEFAULT_MAX_BYTES);
    }

    private long retryAfterMillis(Response response) {
        long now = clock.millis();
        int seconds = parseInt(response.header("Retry-After"), -1);
        if (seconds >= 0) return now + seconds * 1000L;
        long reset = parseLong(response.header("x-ratelimit-reset"), -1);
        if (reset > 0) return reset * 1000L;
        return 0;
    }

    private static String readLimited(@Nullable ResponseBody body, long maxBytes) throws IOException, SourceException {
        if (body == null) return "";
        long declared = body.contentLength();
        if (declared > maxBytes) {
            throw new SourceException(SourceException.Kind.TOO_LARGE, "Response larger than " + maxBytes + " bytes", 0, 0, null);
        }
        BufferedSource source = body.source();
        Buffer buffer = new Buffer();
        while (buffer.size() <= maxBytes) {
            long read = source.read(buffer, 8192);
            if (read == -1) break;
        }
        if (buffer.size() > maxBytes) {
            throw new SourceException(SourceException.Kind.TOO_LARGE, "Response larger than " + maxBytes + " bytes", 0, 0, null);
        }
        MediaType type = body.contentType();
        Charset charset = type != null && type.charset(StandardCharsets.UTF_8) != null
                ? type.charset(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
        return buffer.readString(charset);
    }

    private static int parseInt(@Nullable String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(@Nullable String s, long fallback) {
        try {
            return s == null ? fallback : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
