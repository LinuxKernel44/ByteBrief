package app.bytebrief.source.github;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import app.bytebrief.net.FetchResult;
import app.bytebrief.net.HttpFetcher;
import app.bytebrief.net.SourceException;

/**
 * Minimal GitHub REST v3 client. Works unauthenticated (60 requests/hour); a token supplied by
 * {@code tokenSupplier} is optional and only raises the quota.
 */
public class GithubApi {
    public static final String DEFAULT_BASE_URL = "https://api.github.com";
    public static final long README_MAX_BYTES = 256 * 1024;
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9_.-]{1,100}");

    /** Repository facts we use; everything is as reported by GitHub. */
    public static final class RepoInfo {
        public String description;
        public List<String> topics = new ArrayList<>();
        @Nullable
        public String licenseSpdx;
        @Nullable
        public String pushedAt;
        public long stars;
        public long forks;
        @Nullable
        public String homepage;
        public boolean archived;
    }

    public static final class ReleasesResult {
        public final boolean notModified;
        public final List<JSONObject> releases;
        @Nullable
        public final String etag;
        public final int rateLimitRemaining;

        ReleasesResult(boolean notModified, List<JSONObject> releases, @Nullable String etag, int rateLimitRemaining) {
            this.notModified = notModified;
            this.releases = releases;
            this.etag = etag;
            this.rateLimitRemaining = rateLimitRemaining;
        }
    }

    private final HttpFetcher http;
    private final String baseUrl;
    private final Supplier<String> tokenSupplier;

    public GithubApi(HttpFetcher http, String baseUrl, @Nullable Supplier<String> tokenSupplier) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.tokenSupplier = tokenSupplier == null ? () -> null : tokenSupplier;
    }

    public static boolean isValidSegment(@Nullable String s) {
        return s != null && SAFE_SEGMENT.matcher(s).matches() && !s.equals(".") && !s.equals("..");
    }

    public RepoInfo fetchRepo(String owner, String name) throws SourceException {
        FetchResult r = http.get(repoUrl(owner, name), headers("application/vnd.github+json", null));
        try {
            JSONObject o = new JSONObject(r.body);
            RepoInfo info = new RepoInfo();
            info.description = o.isNull("description") ? null : o.optString("description", null);
            JSONArray topics = o.optJSONArray("topics");
            if (topics != null) for (int i = 0; i < topics.length(); i++) info.topics.add(topics.optString(i));
            JSONObject license = o.optJSONObject("license");
            if (license != null && !license.isNull("spdx_id")) info.licenseSpdx = license.optString("spdx_id");
            info.pushedAt = o.isNull("pushed_at") ? null : o.optString("pushed_at", null);
            info.stars = o.optLong("stargazers_count");
            info.forks = o.optLong("forks_count");
            info.homepage = o.isNull("homepage") ? null : o.optString("homepage", null);
            info.archived = o.optBoolean("archived");
            return info;
        } catch (JSONException e) {
            throw SourceException.parse("Unexpected GitHub repository response", e);
        }
    }

    /** @return README text, or null when the repository has none (HTTP 404). */
    @Nullable
    public String fetchReadme(String owner, String name) throws SourceException {
        try {
            return http.get(repoUrl(owner, name) + "/readme", headers("application/vnd.github.raw+json", null),
                    README_MAX_BYTES).body;
        } catch (SourceException e) {
            if (e.kind == SourceException.Kind.HTTP && e.httpCode == 404) return null;
            throw e;
        }
    }

    public ReleasesResult fetchReleases(String owner, String name, int perPage, @Nullable String etag)
            throws SourceException {
        FetchResult r = http.get(repoUrl(owner, name) + "/releases?per_page=" + Math.min(Math.max(perPage, 1), 30),
                headers("application/vnd.github+json", etag));
        if (r.notModified()) return new ReleasesResult(true, new ArrayList<>(), etag, r.rateLimitRemaining);
        try {
            JSONArray arr = new JSONArray(r.body);
            List<JSONObject> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
            return new ReleasesResult(false, out, r.etag, r.rateLimitRemaining);
        } catch (JSONException e) {
            throw SourceException.parse("Unexpected GitHub releases response", e);
        }
    }

    private String repoUrl(String owner, String name) throws SourceException {
        if (!isValidSegment(owner) || !isValidSegment(name)) {
            throw SourceException.invalidUrl("Invalid GitHub repository name");
        }
        return baseUrl + "/repos/" + owner + "/" + name;
    }

    private Map<String, String> headers(String accept, @Nullable String etag) {
        Map<String, String> h = new HashMap<>();
        h.put("Accept", accept);
        h.put("X-GitHub-Api-Version", "2022-11-28");
        if (etag != null) h.put("If-None-Match", etag);
        String token = tokenSupplier.get();
        if (token != null && !token.isEmpty()) h.put("Authorization", "Bearer " + token);
        return h;
    }
}
