package app.bytebrief.ai;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads one model file with resume support and verifies it before it can be used. The network
 * policy (Wi-Fi only unless the user opted in) is enforced by the caller through WorkManager
 * constraints; this class enforces storage and integrity.
 */
public final class ModelDownloader {
    public enum Status {
        COMPLETE,
        /** Cancelled by the caller; the partial file is kept so the download can resume. */
        PAUSED,
        INSUFFICIENT_SPACE,
        /** Size or SHA-256 mismatch; the bad data was deleted. */
        CORRUPT,
        NETWORK_ERROR,
        HTTP_ERROR
    }

    public static final class Result {
        public final Status status;
        public final String message;

        Result(Status status, String message) {
            this.status = status;
            this.message = message;
        }

        public boolean isSuccess() {
            return status == Status.COMPLETE;
        }
    }

    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);

        /** Integrity check of the finished file started; bytes already downloaded, none left to fetch. */
        void onVerifying();
    }

    private static final long MIN_FREE_MARGIN = 256L * 1024 * 1024;
    private static final int BUFFER = 256 * 1024;

    private final OkHttpClient client;
    private final ModelStore store;
    private final LongSupplier freeSpace;

    public ModelDownloader(OkHttpClient client, ModelStore store, LongSupplier freeSpaceBytes) {
        this.client = client;
        this.store = store;
        this.freeSpace = freeSpaceBytes;
    }

    public Result download(ModelDescriptor model, @Nullable ProgressListener listener, BooleanSupplier cancelled) {
        if (store.stateOf(model) == ModelStore.State.INSTALLED) return new Result(Status.COMPLETE, "Already installed");
        File dir = store.dir();
        if (!dir.isDirectory() && !dir.mkdirs()) return new Result(Status.NETWORK_ERROR, "Cannot create the model directory");

        File part = store.partFor(model);
        long have = part.isFile() ? part.length() : 0;
        if (have > model.sizeBytes) {
            part.delete();
            have = 0;
        }
        long needed = model.sizeBytes - have;
        long margin = Math.max(MIN_FREE_MARGIN, model.sizeBytes / 20);
        if (freeSpace.getAsLong() < needed + margin) {
            return new Result(Status.INSUFFICIENT_SPACE, "Not enough free storage: " + mib(needed + margin) + " MiB needed, "
                    + mib(freeSpace.getAsLong()) + " MiB available");
        }

        if (have < model.sizeBytes) {
            Result fetched = fetch(model, part, have, listener, cancelled);
            if (fetched != null) return fetched;
        }
        return verifyAndInstall(model, part, listener);
    }

    /** @return null when the file is fully downloaded; otherwise the terminal result. */
    @Nullable
    private Result fetch(ModelDescriptor model, File part, long have, @Nullable ProgressListener listener, BooleanSupplier cancelled) {
        Request.Builder rb = new Request.Builder().url(model.downloadUrl).header("User-Agent", "ByteBrief/0.1");
        String etag = readEtag(model);
        if (have > 0) {
            rb.header("Range", "bytes=" + have + "-");
            if (etag != null) rb.header("If-Range", etag);
        }
        try (Response response = client.newCall(rb.build()).execute()) {
            int code = response.code();
            if (code == 416) { // our offset is outside the file: start over
                part.delete();
                store.etagFor(model).delete();
                return new Result(Status.NETWORK_ERROR, "The server rejected the resume position; try again");
            }
            if (code != 200 && code != 206) return new Result(Status.HTTP_ERROR, "HTTP " + code);
            ResponseBody body = response.body();
            if (body == null) return new Result(Status.NETWORK_ERROR, "Empty response");

            boolean resumed = code == 206;
            if (!resumed && have > 0) have = 0; // server ignored Range (or the file changed): restart from zero
            String newEtag = response.header("ETag");
            if (newEtag != null) Files.write(store.etagFor(model).toPath(), newEtag.getBytes(StandardCharsets.UTF_8));

            try (RandomAccessFile out = new RandomAccessFile(part, "rw"); InputStream in = body.byteStream()) {
                out.setLength(have);
                out.seek(have);
                byte[] buf = new byte[BUFFER];
                long done = have;
                long lastReport = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (cancelled.getAsBoolean()) return new Result(Status.PAUSED, "Paused at " + mib(done) + " MiB");
                    out.write(buf, 0, n);
                    done += n;
                    if (done > model.sizeBytes) {
                        part.delete();
                        return new Result(Status.CORRUPT, "The server sent more data than expected");
                    }
                    long now = System.nanoTime();
                    if (listener != null && now - lastReport > 250_000_000L) {
                        listener.onProgress(done, model.sizeBytes);
                        lastReport = now;
                    }
                }
                if (listener != null) listener.onProgress(done, model.sizeBytes);
                // A cancel that arrives after the last byte is ignored: the file is complete, so verify and install it.
            }
            return null;
        } catch (IOException e) {
            // Keep the partial file: a dropped connection is expected on phones and the download can resume.
            return new Result(Status.NETWORK_ERROR, e.getClass().getSimpleName() + " while downloading");
        }
    }

    private Result verifyAndInstall(ModelDescriptor model, File part, @Nullable ProgressListener listener) {
        if (part.length() != model.sizeBytes) {
            return new Result(Status.NETWORK_ERROR, "The download ended early (" + mib(part.length()) + " of " + mib(model.sizeBytes) + " MiB)");
        }
        if (listener != null) listener.onVerifying();
        String actual;
        try {
            actual = sha256(part);
        } catch (IOException e) {
            return new Result(Status.NETWORK_ERROR, "Could not read the downloaded file to verify it");
        }
        if (!actual.equalsIgnoreCase(model.sha256)) {
            part.delete();
            store.etagFor(model).delete();
            return new Result(Status.CORRUPT, "Integrity check failed (SHA-256 mismatch); the file was deleted");
        }
        try {
            Files.move(part.toPath(), store.fileFor(model).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            store.markVerified(model);
            store.etagFor(model).delete();
        } catch (IOException e) {
            return new Result(Status.NETWORK_ERROR, "Could not finalize the model file");
        }
        return new Result(Status.COMPLETE, "Downloaded and verified");
    }

    @Nullable
    private String readEtag(ModelDescriptor model) {
        try {
            File f = store.etagFor(model);
            return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static String sha256(File f) throws IOException {
        try (InputStream in = Files.newInputStream(f.toPath())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long mib(long bytes) {
        return bytes / (1024 * 1024);
    }
}
