package app.bytebrief.net;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONTokener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import app.bytebrief.domain.UrlCanonicalizer;
import app.bytebrief.source.forum.ForumPageState;

/**
 * Loads pages in an invisible system WebView, so a site's own scripts (including a human-verification
 * check) run in a real browser engine. Cookies live in the WebView's shared store, which is also used by
 * the visible verification screen: once the user has passed the check there, later loads here succeed.
 *
 * <p>Only https URLs accepted by {@code allowedHost} are loaded, navigation away from them is blocked,
 * nothing is exposed to the page, and images are not downloaded. Loads are serialized and spaced out.
 */
public final class WebViewPageFetcher implements PageFetcher {
    private static final long TIMEOUT_MS = 30_000;
    private static final long POLL_MS = 600;
    private static final long MIN_GAP_MS = 1_500;
    /** Script that returns the document's ready state (one letter) followed by its HTML. */
    public static final String DOM_JS = "(function(){return document.readyState.charAt(0)+document.documentElement.outerHTML;})()";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Predicate<String> allowedHost;
    private long lastLoadEndedAt;

    public WebViewPageFetcher(Context context, Predicate<String> allowedHost) {
        this.context = context.getApplicationContext();
        this.allowedHost = allowedHost;
    }

    @Override
    public Page load(String url) throws SourceException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Blocking page load on the main thread");
        okhttp3.HttpUrl parsed = UrlCanonicalizer.isHttpUrl(url) ? okhttp3.HttpUrl.parse(url) : null;
        if (parsed == null || !parsed.isHttps() || !allowedHost.test(parsed.host())) {
            throw SourceException.invalidUrl("This address is not supported");
        }
        synchronized (this) {
            long wait = lastLoadEndedAt + MIN_GAP_MS - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw SourceException.network("Interrupted", e);
                }
            }
            try {
                return loadOnce(url);
            } finally {
                lastLoadEndedAt = System.currentTimeMillis();
            }
        }
    }

    private Page loadOnce(String url) throws SourceException {
        Load load = new Load(url);
        main.post(load::start);
        try {
            if (!load.done.await(TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS)) load.fail(SourceException.network("Timed out", null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            load.fail(SourceException.network("Interrupted", e));
        } finally {
            main.post(load::destroy);
        }
        SourceException error = load.error.get();
        if (error != null) throw error;
        return load.result.get();
    }

    /** One page load. Everything except the result fields runs on the main thread. */
    private final class Load {
        final String url;
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Page> result = new AtomicReference<>();
        final AtomicReference<SourceException> error = new AtomicReference<>();
        @Nullable WebView web;
        long startedAt;
        int status = 200;
        boolean destroyed;
        ForumPageState.State last = ForumPageState.State.LOADING;
        boolean sawChallenge;

        Load(String url) {
            this.url = url;
        }

        void fail(SourceException e) {
            error.compareAndSet(null, e);
            done.countDown();
        }

        @SuppressLint("SetJavaScriptEnabled")
        void start() {
            try {
                web = new WebView(context);
            } catch (RuntimeException e) { // no WebView provider installed or being updated
                fail(SourceException.network("The system WebView is not available", e));
                return;
            }
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true); // required by the verification check
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            s.setBlockNetworkImage(true);
            s.setSupportMultipleWindows(false);
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true); // the check may run in a frame
            s.setJavaScriptCanOpenWindowsAutomatically(false);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return !isAllowed(request.getUrl().toString());
                }

                @Override
                public void onPageStarted(WebView view, String u, android.graphics.Bitmap favicon) {
                    status = 200;
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                    if (request.isForMainFrame()) status = response.getStatusCode();
                }
            });
            startedAt = System.currentTimeMillis();
            web.loadUrl(url);
            main.postDelayed(this::poll, POLL_MS);
        }

        boolean isAllowed(String target) {
            okhttp3.HttpUrl u = okhttp3.HttpUrl.parse(target);
            return u != null && u.isHttps() && (allowedHost.test(u.host()) || u.host().endsWith("cloudflare.com"));
        }

        void poll() {
            if (destroyed || web == null || done.getCount() == 0) return;
            web.evaluateJavascript(DOM_JS, value -> {
                if (destroyed || done.getCount() == 0) return;
                String raw = decode(value);
                // First character is the document's readyState: l(oading), i(nteractive) or c(omplete).
                boolean parsed = raw != null && !raw.isEmpty() && raw.charAt(0) != 'l';
                String html = raw == null || raw.isEmpty() ? null : raw.substring(1);
                last = ForumPageState.classify(html);
                if (last == ForumPageState.State.READY && !parsed) last = ForumPageState.State.LOADING;
                if (last == ForumPageState.State.VERIFICATION) sawChallenge = true;
                if (last == ForumPageState.State.READY) {
                    if (status >= 400 && !(sawChallenge && status == 403)) {
                        fail(new SourceException(SourceException.Kind.HTTP, "HTTP " + status, status, 0, null));
                    } else {
                        result.set(new Page(html, web.getUrl() == null ? url : web.getUrl()));
                        done.countDown();
                    }
                    return;
                }
                if (System.currentTimeMillis() - startedAt > TIMEOUT_MS) {
                    fail(last == ForumPageState.State.VERIFICATION
                            ? SourceException.verificationRequired("The site is asking you to verify that you are human")
                            : SourceException.network("The page did not finish loading", null));
                    return;
                }
                main.postDelayed(this::poll, POLL_MS);
            });
        }

        void destroy() {
            destroyed = true;
            if (web != null) {
                web.stopLoading();
                web.destroy();
                web = null;
            }
        }
    }

    /** Classifies the value {@code evaluateJavascript(DOM_JS)} produced; LOADING until the document is parsed. */
    public static ForumPageState.State stateOf(@Nullable String jsValue) {
        String raw = decode(jsValue);
        if (raw == null || raw.length() < 2) return ForumPageState.State.LOADING;
        ForumPageState.State st = ForumPageState.classify(raw.substring(1));
        return st == ForumPageState.State.READY && raw.charAt(0) == 'l' ? ForumPageState.State.LOADING : st;
    }

    /** {@code evaluateJavascript} returns the value JSON-encoded; "null" when there is none. */
    @Nullable
    static String decode(@Nullable String json) {
        if (json == null || json.equals("null")) return null;
        try {
            Object v = new JSONTokener(json).nextValue();
            return v instanceof String ? (String) v : null;
        } catch (JSONException e) {
            return null;
        }
    }
}
