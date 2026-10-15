package app.bytebrief.ui.images;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import app.bytebrief.domain.UrlCanonicalizer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Minimal image loader for article thumbnails. It requests https URLs only, sends no cookies or
 * referrer, rejects non-image and oversized responses, and decodes downsampled bitmaps. When images
 * are switched off in settings it performs no network request at all.
 */
public final class ImageLoader {
    private static final long MAX_BYTES = 3L * 1024 * 1024;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final LruCache<String, Bitmap> cache;
    private final BooleanSupplier enabled;

    public ImageLoader(BooleanSupplier enabled) {
        this.enabled = enabled;
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 16);
        this.cache = new LruCache<String, Bitmap>(Math.max(4 * 1024, maxKb)) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    @Nullable
    public Bitmap cached(String url, int maxWidthPx) {
        return cache.get(key(url, maxWidthPx));
    }

    /** Blocking; call from a background thread. Returns null when disabled, unsafe, or on any failure. */
    @Nullable
    public Bitmap load(@Nullable String url, int maxWidthPx) {
        if (url == null || !enabled.getAsBoolean()) return null;
        HttpsOnly guard = HttpsOnly.check(url);
        if (guard == null) return null;
        String k = key(url, maxWidthPx);
        Bitmap hit = cache.get(k);
        if (hit != null) return hit;
        Request req = new Request.Builder().url(guard.url).header("User-Agent", "ByteBrief").header("Accept", "image/*").build();
        try (Response r = client.newCall(req).execute()) {
            ResponseBody body = r.body();
            if (!r.isSuccessful() || body == null) return null;
            String type = r.header("Content-Type", "");
            if (type == null || !type.toLowerCase().startsWith("image/") || type.toLowerCase().contains("svg")) return null;
            if (body.contentLength() > MAX_BYTES) return null;
            byte[] bytes = readLimited(body.byteStream());
            if (bytes == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long) bounds.outWidth * bounds.outHeight > 40_000_000L) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(1, bounds.outWidth / Math.max(1, maxWidthPx))));
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            if (bmp != null) cache.put(k, bmp);
            return bmp;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) return null;
        }
        return out.toByteArray();
    }

    private static String key(String url, int width) {
        return url + "@" + width;
    }

    /** Accepts only syntactically valid https URLs. */
    private static final class HttpsOnly {
        final String url;

        private HttpsOnly(String url) {
            this.url = url;
        }

        @Nullable
        static HttpsOnly check(String raw) {
            String canonical = UrlCanonicalizer.canonicalize(raw);
            return canonical != null && canonical.startsWith("https://") ? new HttpsOnly(canonical) : null;
        }
    }
}
