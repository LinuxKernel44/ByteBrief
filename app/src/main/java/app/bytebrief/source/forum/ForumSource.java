package app.bytebrief.source.forum;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.Forum;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.net.PageFetcher;
import app.bytebrief.net.SourceException;
import app.bytebrief.source.forum.ForumPageModels.NodeList;
import app.bytebrief.source.forum.ForumPageModels.Post;
import app.bytebrief.source.forum.ForumPageModels.ThreadList;
import app.bytebrief.source.forum.ForumPageModels.ThreadPage;
import app.bytebrief.source.forum.ForumPageModels.ThreadSummary;

/** Reads XDA Forums through a {@link PageFetcher}: thread lists, whole threads, and the forum tree. */
public class ForumSource {
    /** A forum that was loaded and looks followable. */
    public static final class Validated {
        public final String url;
        @Nullable public final String title;
        public final List<ThreadSummary> threads;

        Validated(String url, @Nullable String title, List<ThreadSummary> threads) {
            this.url = url;
            this.title = title;
            this.threads = threads;
        }
    }

    /** Pages of one thread read when opening it; later pages stay on the site. */
    public static final int MAX_THREAD_PAGES = 12;
    private static final int MAX_POSTS = 400;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(ZoneOffset.UTC);

    private final PageFetcher fetcher;
    private final ItemFactory factory;

    public ForumSource(PageFetcher fetcher, ItemFactory factory) {
        this.fetcher = fetcher;
        this.factory = factory;
    }

    /** Loads {@code rawUrl} and checks that it is a forum with threads. */
    public Validated validate(String rawUrl) throws SourceException {
        String url = ForumUrls.normalizeNode(rawUrl);
        if (url == null || url.equals(ForumUrls.HOME)) {
            throw SourceException.invalidUrl("Enter the address of a forum, such as https://xdaforums.com/f/oneplus-15.12971/");
        }
        PageFetcher.Page page = fetcher.load(url);
        failIfErrorPage(page);
        ThreadList list = ForumParser.parseThreadList(page.html, page.finalUrl);
        if (list.threads.isEmpty()) {
            throw SourceException.parse(ForumUrls.isCategory(url)
                    ? "This is a category. Open it and pick one of its forums."
                    : "No threads were found on this page", null);
        }
        return new Validated(url, list.title, list.threads);
    }

    /** First page of a forum, newest activity first. Everything is returned as items without a body. */
    public List<ContentItem> fetchThreads(Forum forum) throws SourceException {
        PageFetcher.Page page = fetcher.load(forum.url);
        failIfErrorPage(page);
        ThreadList list = ForumParser.parseThreadList(page.html, page.finalUrl);
        if (list.threads.isEmpty()) throw SourceException.parse("No threads were found; the page layout may have changed", null);
        return toItems(forum, list.threads);
    }

    public List<ContentItem> toItems(Forum forum, List<ThreadSummary> threads) {
        List<ContentItem> items = new ArrayList<>();
        for (ThreadSummary t : threads) {
            String desc = t.author == null ? null : "Started by " + t.author;
            List<String> topics = new ArrayList<>(Arrays.asList("xda"));
            if (forum.title != null) topics.add(forum.title.toLowerCase(Locale.ROOT));
            if (t.prefix != null) topics.add(t.prefix.toLowerCase(Locale.ROOT));
            // Active old threads should surface again, so the last activity decides the date.
            ContentItem item = factory.create(SourceType.XDA_FORUM, forum.id + ":" + t.threadId, t.title, t.url, t.author,
                    Math.max(t.startedAt, t.lastPostAt), desc, null, BodyState.NONE, topics, "en");
            if (item == null) continue;
            item.feedId = forum.id;
            item.sourceLabel = forum.title;
            item.commentCount = t.replies;
            item.score = t.views;
            try {
                JSONObject meta = new JSONObject().put("replies", t.replies).put("views", t.views)
                        .put("started_at", t.startedAt).put("last_post_at", t.lastPostAt).put("sticky", t.sticky);
                if (t.prefix != null) meta.put("prefix", t.prefix);
                item.metadataJson = meta.toString();
            } catch (JSONException ignored) {
                // Plain values cannot fail to serialize.
            }
            items.add(item);
        }
        return items;
    }

    /**
     * Reads a thread from its first page and returns it as Markdown-like text, one section per post.
     * Stops after {@link #MAX_THREAD_PAGES} pages and says so at the end of the text.
     */
    public String fetchThreadText(String threadUrl) throws SourceException {
        String url = ForumUrls.resolve(ForumUrls.HOME, threadUrl);
        if (url == null) throw SourceException.invalidUrl("Not a forum address");
        StringBuilder out = new StringBuilder();
        Set<String> visited = new HashSet<>();
        int pages = 0;
        int posts = 0;
        boolean more = false;
        while (url != null && visited.add(url)) {
            if (pages >= MAX_THREAD_PAGES || posts >= MAX_POSTS) {
                more = true;
                break;
            }
            PageFetcher.Page page = fetcher.load(url);
            failIfErrorPage(page);
            ThreadPage tp = ForumParser.parseThreadPage(page.html, page.finalUrl);
            pages++;
            if (tp.posts.isEmpty() && pages == 1) throw SourceException.parse("No posts were found; the page layout may have changed", null);
            for (Post p : tp.posts) {
                if (posts >= MAX_POSTS) {
                    more = true;
                    break;
                }
                posts++;
                out.append(format(posts, p));
            }
            url = tp.nextUrl;
        }
        if (more) out.append("\n_Only the first ").append(posts).append(" posts are shown. Open the original to read the rest of the thread._\n");
        return out.toString().trim();
    }

    static String format(int number, Post p) {
        StringBuilder sb = new StringBuilder("## #").append(number).append(" · ").append(p.author == null ? "Unknown" : p.author);
        if (p.postedAt > 0) sb.append(" · ").append(DAY.format(Instant.ofEpochMilli(p.postedAt)));
        sb.append("\n\n");
        if (!p.text.isEmpty()) sb.append(p.text).append("\n\n");
        if (!p.attachments.isEmpty()) sb.append("Attachments: ").append(String.join(", ", p.attachments)).append("\n\n");
        return sb.toString();
    }

    /** Lists the sub-forums and categories shown on a page; the root is the forum home. */
    public NodeList browse(@Nullable String rawUrl) throws SourceException {
        String url = rawUrl == null ? ForumUrls.HOME : ForumUrls.normalizeNode(rawUrl);
        if (url == null) throw SourceException.invalidUrl("Not a forum address");
        PageFetcher.Page page = fetcher.load(url);
        failIfErrorPage(page);
        return ForumParser.parseNodes(page.html, page.finalUrl);
    }

    private static void failIfErrorPage(PageFetcher.Page page) throws SourceException {
        if (ForumParser.isErrorPage(page.html)) throw new SourceException(SourceException.Kind.HTTP, "The forum page was not found", 404, 0, null);
    }
}
