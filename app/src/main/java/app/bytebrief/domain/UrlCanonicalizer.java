package app.bytebrief.domain;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.HttpUrl;

/**
 * Deterministic URL normalization used for display ({@link #canonicalize}) and for cross-source
 * deduplication ({@link #dedupKey}). Only http(s) URLs are accepted; everything else yields null so
 * that {@code file:}, {@code content:}, {@code javascript:} and similar schemes can never reach the
 * network layer.
 */
public final class UrlCanonicalizer {
    private static final Set<String> TRACKING_PARAMS = new HashSet<>(Arrays.asList(
            "fbclid", "gclid", "dclid", "msclkid", "yclid", "igshid", "mc_cid", "mc_eid", "mkt_tok",
            "_hsenc", "_hsmi", "ref", "ref_src", "ref_url", "source", "spm", "cmpid", "ncid"));

    /** Path segments after owner/repo that make a github.com URL point at something other than the repository. */
    private static final String GITHUB_HOST = "github.com";

    private UrlCanonicalizer() {}

    /** @return a normalized http(s) URL without tracking parameters or fragment, or null if unusable. */
    @Nullable
    public static String canonicalize(@Nullable String raw) {
        HttpUrl url = parse(raw);
        if (url == null) return null;
        HttpUrl.Builder b = url.newBuilder().fragment(null).username("").password("");
        List<String> names = new ArrayList<>(url.queryParameterNames());
        for (String name : names) {
            if (isTracking(name)) b.removeAllQueryParameters(name);
        }
        HttpUrl cleaned = b.build();
        String s = cleaned.toString();
        // "https://example.com/" -> keep root slash, strip it elsewhere.
        if (s.endsWith("/") && cleaned.pathSize() > 1 && cleaned.query() == null) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Key identifying the subject of a URL regardless of scheme, "www.", tracking parameters,
     * fragments, trailing slashes or query parameter order. GitHub repository URLs collapse to
     * {@code github.com/owner/repo}.
     */
    @Nullable
    public static String dedupKey(@Nullable String raw) {
        HttpUrl url = parse(raw);
        if (url == null) return null;
        String host = url.host().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);

        List<String> segments = new ArrayList<>(url.pathSegments());
        while (!segments.isEmpty() && segments.get(segments.size() - 1).isEmpty()) {
            segments.remove(segments.size() - 1);
        }
        if (GITHUB_HOST.equals(host) && segments.size() == 2) {
            // Repository identity is case-insensitive on GitHub.
            String repo = segments.get(1);
            if (repo.endsWith(".git")) repo = repo.substring(0, repo.length() - 4);
            return "u:" + host + "/" + segments.get(0).toLowerCase(Locale.ROOT) + "/" + repo.toLowerCase(Locale.ROOT);
        }

        StringBuilder sb = new StringBuilder("u:").append(host);
        if (url.port() != HttpUrl.defaultPort(url.scheme())) sb.append(':').append(url.port());
        for (String seg : segments) sb.append('/').append(seg);

        List<String> params = new ArrayList<>();
        for (String name : url.queryParameterNames()) {
            if (isTracking(name)) continue;
            List<String> values = new ArrayList<>(url.queryParameterValues(name));
            Collections.sort(values, (a, c) -> (a == null ? "" : a).compareTo(c == null ? "" : c));
            for (String v : values) params.add(name + "=" + (v == null ? "" : v));
        }
        Collections.sort(params);
        if (!params.isEmpty()) sb.append('?').append(String.join("&", params));
        return sb.toString();
    }

    /** True if the URL is syntactically valid and uses http or https. */
    public static boolean isHttpUrl(@Nullable String raw) {
        return parse(raw) != null;
    }

    /** Upgrades http to https; returns null for anything that is not http(s). */
    @Nullable
    public static String toHttps(@Nullable String raw) {
        HttpUrl url = parse(raw);
        if (url == null) return null;
        return url.isHttps() ? url.toString() : url.newBuilder().scheme("https").build().toString();
    }

    @Nullable
    private static HttpUrl parse(@Nullable String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2048) return null;
        // HttpUrl.parse only accepts http and https, which is exactly the allow-list we want.
        return HttpUrl.parse(trimmed);
    }

    private static boolean isTracking(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("utm_") || TRACKING_PARAMS.contains(n);
    }
}
