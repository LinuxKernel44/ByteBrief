package app.bytebrief;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.bytebrief.ai.AiService;
import app.bytebrief.ai.BriefingService;
import app.bytebrief.ai.LlamaAiEngine;
import app.bytebrief.ai.ModelManager;
import app.bytebrief.ai.ModelStore;
import app.bytebrief.content.ArticleLoader;
import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.net.HttpFetcher;
import app.bytebrief.net.PageFetcher;
import app.bytebrief.net.WebViewPageFetcher;
import app.bytebrief.source.forum.ForumSource;
import app.bytebrief.source.forum.ForumUrls;
import app.bytebrief.net.SourceException;
import app.bytebrief.notify.Notifier;
import app.bytebrief.ranking.Personalizer;
import app.bytebrief.repo.ContentRepository;
import app.bytebrief.repo.FeedRepository;
import app.bytebrief.repo.ForumRepository;
import app.bytebrief.repo.Ingestor;
import app.bytebrief.repo.SyncManager;
import app.bytebrief.security.TokenStore;
import app.bytebrief.settings.AppSettings;
import app.bytebrief.source.github.GithubApi;
import app.bytebrief.source.github.ReleaseSource;
import app.bytebrief.source.github.TrendingSource;
import app.bytebrief.source.hn.HackerNewsSource;
import app.bytebrief.source.rss.FeedSource;
import app.bytebrief.work.Scheduler;

/** Manual dependency wiring: one instance per process, created by {@link ByteBriefApp}. */
public final class AppContainer {
    public final Context context;
    public final Clock clock = Clock.systemUTC();
    public final ZoneId zone = ZoneId.systemDefault();
    public final ExecutorService io = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "bytebrief-io");
        t.setDaemon(true);
        return t;
    });

    public final AppDatabase db;
    public final AppSettings settings;
    public final TokenStore tokens;
    public final HttpFetcher http;
    public final ItemFactory factory;
    public final Ingestor ingestor;
    public final GithubApi github;
    public final HackerNewsSource hackerNews;
    public final FeedSource feedSource;
    public final SyncManager sync;
    public final Personalizer personalizer;
    public final ModelStore modelStore;
    public final LlamaAiEngine engine;
    public final ModelManager models;
    public final ArticleLoader articles;
    public final AiService ai;
    public final BriefingService briefing;
    public final ContentRepository content;
    public final FeedRepository feeds;
    public final PageFetcher pages;
    public final ForumSource forumSource;
    public final ForumRepository forums;
    public final Notifier notifier;
    public final Scheduler scheduler;
    public final app.bytebrief.ui.images.ImageLoader images;

    AppContainer(Context app) {
        this.context = app;
        db = AppDatabase.get(app);
        settings = new AppSettings(app);
        tokens = new TokenStore(app);
        http = new HttpFetcher(HttpFetcher.defaultClient(), clock);
        factory = new ItemFactory(clock);
        ingestor = new Ingestor(db, clock);
        github = new GithubApi(http, GithubApi.DEFAULT_BASE_URL, tokens::get);
        hackerNews = new HackerNewsSource(http, factory, HackerNewsSource.DEFAULT_BASE_URL);
        feedSource = new FeedSource(http, factory);
        TrendingSource trending = new TrendingSource(http, github, factory, TrendingSource.DEFAULT_PAGE_URL, clock);
        ReleaseSource releases = new ReleaseSource(github, factory);
        sync = new SyncManager(db, ingestor, hackerNews, trending, feedSource, releases, settings, clock);
        personalizer = new Personalizer(db.affinityDao(), settings::learningEnabled, clock);
        modelStore = new ModelStore(new File(app.getFilesDir(), "models"));
        engine = new LlamaAiEngine(app);
        models = new ModelManager(app, modelStore, engine, settings);
        articles = new ArticleLoader(db, http, github, clock);
        scheduler = new Scheduler(app, settings);
        notifier = new Notifier(app);
        images = new app.bytebrief.ui.images.ImageLoader(settings::showImages);
        ai = new AiService(db, engine, io, clock, item -> {
            // Only Hacker News items have a discussion we can fetch.
            List<String> out = new ArrayList<>();
            if (!"hn".equals(item.sourceType) || item.metadataJson == null) return out;
            try {
                long id = new JSONObject(item.metadataJson).optLong("hn_id", -1);
                if (id < 0) return out;
                for (HackerNewsSource.Comment c : hackerNews.fetchTopComments(id, 8)) out.add(c.author + ": " + c.text);
            } catch (org.json.JSONException e) {
                throw SourceException.parse("Bad item metadata", e);
            }
            return out;
        });
        briefing = new BriefingService(db, personalizer, engine, io, clock, zone);
        content = new ContentRepository(db, sync, personalizer, settings, io, clock, id -> {
            if (settings.cacheBookmarkedText()) scheduler.cacheArticle(id);
        });
        feeds = new FeedRepository(db, feedSource, github, ingestor, io, clock);
        pages = new WebViewPageFetcher(app, ForumUrls::isForumHost);
        forumSource = new ForumSource(pages, factory);
        forums = new ForumRepository(db, forumSource, ingestor, io, clock);
        articles.setForumSource(forumSource);
        // Once no forum is followed there is no reason to keep the site's cookies (including its verification pass).
        forums.setOnNoForumsLeft(() -> new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            android.webkit.WebStorage.getInstance().deleteAllData();
        }));
        android.net.ConnectivityManager cm = app.getSystemService(android.net.ConnectivityManager.class);
        sync.setForumSupport(forumSource, () -> cm != null && cm.isActiveNetworkMetered());
    }
}
