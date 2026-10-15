package app.bytebrief.source.hn;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.HtmlText;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.domain.UrlCanonicalizer;
import app.bytebrief.net.FetchResult;
import app.bytebrief.net.HttpFetcher;
import app.bytebrief.net.SourceException;

/** Collects stories from the official Hacker News Firebase API (https://github.com/HackerNews/API). */
public class HackerNewsSource {
    public static final String DEFAULT_BASE_URL = "https://hacker-news.firebaseio.com/v0";
    public static final String ITEM_PAGE = "https://news.ycombinator.com/item?id=";
    private static final int FETCH_THREADS = 6;
    private static final int MAX_LIST = 100;

    public enum Feed {
        TOP("topstories"), NEW("newstories"), BEST("beststories"), SHOW("showstories"), ASK("askstories");

        final String endpoint;

        Feed(String endpoint) {
            this.endpoint = endpoint;
        }
    }

    /** One comment of a story discussion. */
    public static final class Comment {
        public final String author;
        public final String text;

        Comment(String author, String text) {
            this.author = author;
            this.text = text;
        }
    }

    public static final class Result {
        public final List<ContentItem> items = new ArrayList<>();
        /** Items that could not be fetched or parsed (the rest of the batch is still usable). */
        public int failures;
        public int skipped;
    }

    private final HttpFetcher http;
    private final ItemFactory factory;
    private final String baseUrl;

    public HackerNewsSource(HttpFetcher http, ItemFactory factory, String baseUrl) {
        this.http = http;
        this.factory = factory;
        this.baseUrl = baseUrl;
    }

    /**
     * @param limit max stories to consider (capped at 100 to keep request counts low)
     * @param skipId ids for which a recent copy already exists; they are not requested again
     * @throws SourceException when the id list itself cannot be retrieved
     */
    public Result fetch(Feed feed, int limit, Predicate<String> skipId) throws SourceException {
        List<Long> ids = fetchIds(feed, Math.min(Math.max(limit, 1), MAX_LIST));
        Result result = new Result();
        ExecutorService pool = Executors.newFixedThreadPool(FETCH_THREADS);
        try {
            List<Future<ContentItem>> futures = new ArrayList<>();
            for (long id : ids) {
                if (skipId != null && skipId.test(Long.toString(id))) {
                    result.skipped++;
                    continue;
                }
                futures.add(pool.submit(() -> fetchStory(id, feed)));
            }
            for (Future<ContentItem> f : futures) {
                try {
                    ContentItem item = f.get();
                    if (item != null) result.items.add(item);
                } catch (ExecutionException e) {
                    result.failures++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw SourceException.network("Interrupted", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return result;
    }

    /** Fetches up to {@code max} top-level comments, skipping deleted and dead ones. */
    public List<Comment> fetchTopComments(long storyId, int max) throws SourceException {
        JSONObject story = fetchItem(storyId);
        List<Comment> out = new ArrayList<>();
        if (story == null) return out;
        JSONArray kids = story.optJSONArray("kids");
        if (kids == null) return out;
        for (int i = 0; i < kids.length() && out.size() < max; i++) {
            JSONObject c = fetchItem(kids.optLong(i));
            if (c == null || c.optBoolean("deleted") || c.optBoolean("dead")) continue;
            String text = HtmlText.toPlainText(c.optString("text", ""));
            if (!text.isEmpty()) out.add(new Comment(c.optString("by", "unknown"), text));
        }
        return out;
    }

    private List<Long> fetchIds(Feed feed, int limit) throws SourceException {
        FetchResult r = http.get(baseUrl + "/" + feed.endpoint + ".json", null);
        try {
            JSONArray arr = new JSONArray(r.body);
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < arr.length() && ids.size() < limit; i++) ids.add(arr.getLong(i));
            return ids;
        } catch (JSONException e) {
            throw SourceException.parse("Unexpected Hacker News id list", e);
        }
    }

    @Nullable
    private JSONObject fetchItem(long id) throws SourceException {
        FetchResult r = http.get(baseUrl + "/item/" + id + ".json", null, 512 * 1024);
        if (r.body == null || r.body.trim().equals("null")) return null;
        try {
            return new JSONObject(r.body);
        } catch (JSONException e) {
            throw SourceException.parse("Unexpected Hacker News item", e);
        }
    }

    @Nullable
    private ContentItem fetchStory(long id, Feed feed) throws SourceException {
        JSONObject o = fetchItem(id);
        return o == null ? null : toItem(o, feed);
    }

    /** Maps one HN item. Returns null for jobs, polls, comments, and deleted or dead items. */
    @Nullable
    ContentItem toItem(JSONObject o, Feed feed) {
        if (!"story".equals(o.optString("type")) || o.optBoolean("deleted") || o.optBoolean("dead")) return null;
        long id = o.optLong("id", -1);
        String title = o.optString("title", "");
        if (id < 0 || title.isEmpty()) return null;

        String hnUrl = ITEM_PAGE + id;
        String url = o.optString("url", "");
        // A missing or non-http(s) link (e.g. "javascript:") falls back to the discussion page.
        boolean hasLink = UrlCanonicalizer.isHttpUrl(url);
        String text = HtmlText.toPlainText(o.optString("text", ""));

        List<String> topics = new ArrayList<>(Arrays.asList("hacker-news"));
        if (title.regionMatches(true, 0, "Show HN", 0, 7)) topics.add("show-hn");
        if (title.regionMatches(true, 0, "Ask HN", 0, 6)) topics.add("ask-hn");

        ContentItem item = factory.create(SourceType.HACKER_NEWS, Long.toString(id), title, hasLink ? url : hnUrl,
                o.optString("by", null), o.optLong("time", 0) * 1000L,
                text.isEmpty() ? null : HtmlText.truncate(text, 500),
                text.isEmpty() ? null : text, BodyState.FULL, topics, null);
        if (item == null) return null;
        item.sourceLabel = SourceType.HACKER_NEWS.label;
        item.score = o.optLong("score", 0);
        item.commentCount = o.optInt("descendants", 0);
        try {
            JSONObject meta = new JSONObject();
            meta.put("hn_id", id);
            meta.put("discussion_url", hnUrl);
            meta.put("list", feed.name().toLowerCase());
            meta.put("has_external_link", hasLink);
            item.metadataJson = meta.toString();
        } catch (JSONException ignored) {
            // JSONObject.put only throws for non-finite numbers; none are used here.
        }
        return item;
    }
}
