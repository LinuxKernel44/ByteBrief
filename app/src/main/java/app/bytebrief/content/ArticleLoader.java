package app.bytebrief.content;

import androidx.annotation.Nullable;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.ContentHasher;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.net.FetchResult;
import app.bytebrief.net.HttpFetcher;
import app.bytebrief.net.SourceException;
import org.json.JSONException;
import org.json.JSONObject;

import app.bytebrief.source.forum.ForumSource;
import app.bytebrief.source.github.GithubApi;

/**
 * Downloads and stores the full text of an item for offline reading. It runs only when the user asks
 * (or bookmarks an item); it never executes anything from the page and stores plain text only.
 */
public class ArticleLoader {
    public enum Outcome {
        /** Full text is now stored. */
        STORED,
        /** The item already had its full text. */
        ALREADY_FULL,
        /** The page loaded but contains no extractable article (paywall, app shell, login page). */
        NO_ARTICLE,
        /** The link is not an HTML page (PDF, video, image...). */
        UNSUPPORTED_TYPE,
        NOT_FOUND
    }

    private static final long MAX_PAGE_BYTES = 3L * 1024 * 1024;

    private final AppDatabase db;
    private final HttpFetcher http;
    private final GithubApi github;
    private final Clock clock;
    @Nullable private ForumSource forums;

    /** Lets {@link #load} read forum threads; without it they are reported as unsupported. */
    public void setForumSource(ForumSource forums) {
        this.forums = forums;
    }

    public ArticleLoader(AppDatabase db, HttpFetcher http, GithubApi github, Clock clock) {
        this.db = db;
        this.http = http;
        this.github = github;
        this.clock = clock;
    }

    public Outcome load(long itemId) throws SourceException {
        ContentItem item = db.contentDao().getById(itemId);
        if (item == null) return Outcome.NOT_FOUND;
        if (item.bodyState == BodyState.FULL) return Outcome.ALREADY_FULL;

        String text;
        if (SourceType.GITHUB_TRENDING.code.equals(item.sourceType)) {
            text = readme(item);
        } else if (SourceType.XDA_FORUM.code.equals(item.sourceType)) {
            if (forums == null) return Outcome.UNSUPPORTED_TYPE;
            text = forums.fetchThreadText(item.canonicalUrl);
        } else {
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1");
            FetchResult r = http.get(item.canonicalUrl, headers, MAX_PAGE_BYTES);
            if (r.contentType != null && !(r.contentType.equals("text/html") || r.contentType.equals("application/xhtml+xml")
                    || r.contentType.equals("text/plain"))) {
                return Outcome.UNSUPPORTED_TYPE;
            }
            text = r.contentType != null && r.contentType.equals("text/plain") ? r.body : ArticleExtractor.extract(r.body, r.finalUrl);
        }
        if (text == null || text.trim().length() < ArticleExtractor.MIN_ARTICLE_CHARS) return Outcome.NO_ARTICLE;

        ContentItem scratch = new ContentItem();
        scratch.title = item.title;
        scratch.bodyText = text;
        ItemFactory.refreshHash(scratch);
        db.contentDao().setBody(item.id, text, BodyState.FULL, scratch.contentHash, clock.millis());
        return Outcome.STORED;
    }

    @Nullable
    private String readme(ContentItem item) throws SourceException {
        try {
            JSONObject meta = new JSONObject(item.metadataJson == null ? "{}" : item.metadataJson);
            return github.fetchReadme(meta.optString("owner"), meta.optString("repo"));
        } catch (JSONException e) {
            return null;
        }
    }

    /** Hash helper exposed for tests and callers that need to predict {@link ContentItem#contentHash}. */
    static String hashOf(String title, String text) {
        return ContentHasher.contentHash(title, text);
    }
}
