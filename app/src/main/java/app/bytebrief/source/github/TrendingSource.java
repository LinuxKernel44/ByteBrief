package app.bytebrief.source.github;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.HtmlText;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.net.HttpFetcher;
import app.bytebrief.net.SourceException;

/** GitHub Trending: scrapes the public page via {@link TrendingParser}, then enriches via the REST API. */
public class TrendingSource {
    public static final String DEFAULT_PAGE_URL = "https://github.com/trending";
    private static final Pattern LANGUAGE_SLUG = Pattern.compile("[a-z0-9+#._-]{1,40}");
    /** The README is capped when mapped to an item; the cap is recorded in metadata. */
    private static final int README_STORE_CHARS = 60_000;
    /** Stop enriching when fewer than this many API requests remain, leaving headroom for releases. */
    private static final int RATE_LIMIT_FLOOR = 15;

    public enum Period {
        DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly");

        final String param;

        Period(String param) {
            this.param = param;
        }
    }

    private final HttpFetcher http;
    private final GithubApi api;
    private final ItemFactory factory;
    private final String pageUrl;
    private final java.time.Clock clock;

    public TrendingSource(HttpFetcher http, GithubApi api, ItemFactory factory, String pageUrl, java.time.Clock clock) {
        this.clock = clock;
        this.http = http;
        this.api = api;
        this.factory = factory;
        this.pageUrl = pageUrl;
    }

    /** @param language optional GitHub language slug such as "java" or "c++"; null for all languages */
    public List<ContentItem> fetch(Period period, @Nullable String language) throws SourceException {
        String url = pageUrl;
        if (language != null && !language.isEmpty()) {
            String slug = language.toLowerCase(Locale.ROOT);
            if (!LANGUAGE_SLUG.matcher(slug).matches()) throw SourceException.invalidUrl("Invalid language filter");
            url += "/" + slug.replace("#", "%23").replace("+", "%2B");
        }
        url += "?since=" + period.param;
        String html = http.get(url, null, 2L * 1024 * 1024).body;
        List<TrendingParser.Repo> repos = TrendingParser.parse(html);
        if (repos.isEmpty()) {
            // An empty list means the markup changed or the page was a block/consent page; do not pretend success.
            throw SourceException.parse("GitHub Trending page contained no repositories (markup may have changed)", null);
        }
        List<ContentItem> items = new ArrayList<>();
        for (TrendingParser.Repo r : repos) {
            ContentItem item = map(r, period);
            if (item != null) items.add(item);
        }
        return items;
    }

    @Nullable
    ContentItem map(TrendingParser.Repo r, Period period) {
        String full = r.owner + "/" + r.name;
        List<String> topics = new ArrayList<>();
        if (r.language != null) topics.add(r.language);
        ContentItem item = factory.create(SourceType.GITHUB_TRENDING, full.toLowerCase(Locale.ROOT), full,
                "https://github.com/" + full, r.owner, 0, r.description, null, BodyState.NONE, topics, null);
        if (item == null) return null;
        item.sourceLabel = SourceType.GITHUB_TRENDING.label;
        item.score = r.stars;
        try {
            JSONObject meta = new JSONObject();
            meta.put("owner", r.owner);
            meta.put("repo", r.name);
            meta.put("forks", r.forks);
            if (r.language != null) meta.put("programming_language", r.language);
            if (r.starsInPeriod >= 0) {
                meta.put("stars_in_period", r.starsInPeriod);
                meta.put("period", period.param);
            }
            item.metadataJson = meta.toString();
        } catch (JSONException ignored) {
            // Only thrown for non-finite numbers.
        }
        return item;
    }

    /**
     * Adds topics, license, last-push date and README text from the REST API to items that need it.
     * Stops quietly when the API quota runs low; unenriched items stay valid.
     *
     * @return number of items enriched
     */
    public int enrich(List<ContentItem> items, int maxRepos, Predicate<ContentItem> needsEnrichment) {
        int done = 0;
        for (ContentItem item : items) {
            if (done >= maxRepos) break;
            if (!needsEnrichment.test(item)) continue;
            try {
                JSONObject meta = new JSONObject(item.metadataJson == null ? "{}" : item.metadataJson);
                String owner = meta.optString("owner");
                String repo = meta.optString("repo");
                GithubApi.RepoInfo info = api.fetchRepo(owner, repo);
                List<String> topics = new ArrayList<>(info.topics);
                if (meta.has("programming_language")) topics.add(meta.getString("programming_language"));
                item.topics = app.bytebrief.domain.Topics.join(topics);
                if (info.licenseSpdx != null) meta.put("license", info.licenseSpdx);
                if (info.pushedAt != null) meta.put("pushed_at", info.pushedAt);
                if (info.archived) meta.put("archived", true);
                if (info.description != null && item.description == null) item.description = info.description;

                String readme = api.fetchReadme(owner, repo);
                if (readme != null && !readme.trim().isEmpty()) {
                    boolean truncated = readme.length() > README_STORE_CHARS;
                    item.bodyText = truncated ? readme.substring(0, README_STORE_CHARS) : readme;
                    item.bodyState = truncated ? BodyState.SNIPPET : BodyState.FULL;
                    meta.put("readme_truncated", truncated);
                }
                // Recorded even when no README exists so the item is not re-enriched on every sync.
                meta.put("enriched_at", clock.millis());
                item.metadataJson = meta.toString();
                ItemFactory.refreshHash(item);
                done++;
            } catch (SourceException e) {
                if (e.kind == SourceException.Kind.RATE_LIMITED) break;
                // Per-repo failures (e.g. 404, parse) must not stop the rest.
            } catch (JSONException ignored) {
                // Malformed local metadata: skip this item.
            }
        }
        return done;
    }
}
