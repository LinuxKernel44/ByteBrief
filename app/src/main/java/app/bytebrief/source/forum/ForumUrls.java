package app.bytebrief.source.forum;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

/** Address rules for XDA Forums (a XenForo site). Only https pages on the site's own host are ever loaded. */
public final class ForumUrls {
    public static final String HOME = "https://xdaforums.com/";
    private static final String HOST = "xdaforums.com";
    private static final Pattern NODE_PATH = Pattern.compile("^/(?:f|c|forums)/[^/]+/?(?:page-\\d+)?/?$");
    private static final Pattern THREAD_ID = Pattern.compile("/(?:t|threads)/(?:[^/]*\\.)?(\\d+)(?:/|$)");

    private ForumUrls() {}

    public static boolean isForumHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals(HOST) || h.endsWith("." + HOST);
    }

    /**
     * Normalizes a forum or category address typed or pasted by the user: https, canonical host, no
     * query, fragment or page number, trailing slash. Returns null for anything that is not one.
     */
    @Nullable
    public static String normalizeNode(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("/")) s = "https://" + HOST + s;
        else if (!s.contains("://")) s = "https://" + s;
        HttpUrl u = HttpUrl.parse(s);
        if (u == null || !isForumHost(u.host())) return null;
        if (!u.isHttps()) u = u.newBuilder().scheme("https").build();
        String path = u.encodedPath();
        if (path.equals("/")) return HOME;
        if (!NODE_PATH.matcher(path).matches()) return null;
        path = path.replaceAll("/page-\\d+/?$", "/");
        if (!path.endsWith("/")) path += "/";
        return "https://" + HOST + path;
    }

    /** Resolves {@code href} against a forum page; null unless it stays on the forum host over https. */
    @Nullable
    public static String resolve(String baseUrl, @Nullable String href) {
        if (href == null || href.trim().isEmpty()) return null;
        HttpUrl base = HttpUrl.parse(baseUrl);
        HttpUrl u = base == null ? null : base.resolve(href.trim());
        if (u == null || !u.isHttps() || !isForumHost(u.host())) return null;
        return u.newBuilder().fragment(null).build().toString();
    }

    public static boolean isCategory(String url) {
        return url.contains("/c/");
    }

    /** Numeric thread id from a thread address such as /t/some-title.123456/, or -1. */
    public static long threadId(@Nullable String url) {
        if (url == null) return -1;
        Matcher m = THREAD_ID.matcher(url);
        if (!m.find()) return -1;
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
