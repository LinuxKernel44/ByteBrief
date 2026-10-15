package app.bytebrief.ai;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

public class ModelDownloaderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private MockWebServer server;
    private byte[] data;
    private byte[] served; // what the server actually sends (may differ from data to simulate tampering)
    private ModelDescriptor model;
    private ModelStore store;
    private ModelDownloader downloader;
    private long freeSpace = Long.MAX_VALUE / 2;
    private boolean honorRange = true;
    private boolean sendEtag = true;
    private int forcedStatus = 0;
    private SocketPolicy policy = SocketPolicy.KEEP_OPEN;
    private final List<RecordedRequest> requests = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        data = new byte[3 * 1024 * 1024 + 123];
        new Random(42).nextBytes(data);
        served = data;
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                requests.add(r);
                if (forcedStatus != 0) return new MockResponse().setResponseCode(forcedStatus);
                String range = r.getHeader("Range");
                MockResponse resp = new MockResponse().setSocketPolicy(policy);
                if (sendEtag) resp.setHeader("ETag", "\"v1\"");
                if (range != null && honorRange) {
                    long start = Long.parseLong(range.substring("bytes=".length(), range.length() - 1));
                    if (start >= served.length) return new MockResponse().setResponseCode(416);
                    Buffer b = new Buffer().write(served, (int) start, served.length - (int) start);
                    return resp.setResponseCode(206).setHeader("Content-Range", "bytes " + start + "-" + (served.length - 1) + "/" + served.length).setBody(b);
                }
                return resp.setResponseCode(200).setBody(new Buffer().write(served));
            }
        });
        server.start();
        model = new ModelDescriptor("test", "Test", "T", "1B", "Q4", "test.gguf", data.length, ModelDownloader.sha256(write(data)),
                server.url("/test.gguf").toString(), "MIT", "https://x", "https://x", 2048, 1024, "");
        store = new ModelStore(new File(tmp.getRoot(), "models"));
        downloader = new ModelDownloader(new OkHttpClient(), store, () -> freeSpace);
    }

    private File write(byte[] bytes) throws Exception {
        File f = tmp.newFile();
        Files.write(f.toPath(), bytes);
        return f;
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private ModelDownloader.Result run(ModelDownloader.ProgressListener l, java.util.function.BooleanSupplier cancel) {
        return downloader.download(model, l, cancel);
    }

    @Test
    public void downloadsVerifiesAndInstallsAtomically() throws Exception {
        List<Long> progress = new ArrayList<>();
        AtomicInteger verifying = new AtomicInteger();
        ModelDownloader.Result r = run(new ModelDownloader.ProgressListener() {
            @Override public void onProgress(long d, long t) { progress.add(d); assertEquals(data.length, t); }
            @Override public void onVerifying() { verifying.incrementAndGet(); }
        }, () -> false);
        assertTrue(r.message, r.isSuccess());
        assertEquals(ModelStore.State.INSTALLED, store.stateOf(model));
        assertArrayEquals(data, Files.readAllBytes(store.fileFor(model).toPath()));
        assertFalse(store.partFor(model).exists());
        assertFalse(store.etagFor(model).exists());
        assertEquals(1, verifying.get());
        assertEquals(Long.valueOf(data.length), progress.get(progress.size() - 1));
        for (int i = 1; i < progress.size(); i++) assertTrue(progress.get(i) >= progress.get(i - 1));
    }

    @Test
    public void resumesFromPartialFileUsingRangeAndIfRange() throws Exception {
        store.dir().mkdirs();
        Files.write(store.partFor(model).toPath(), java.util.Arrays.copyOf(data, 1024 * 1024));
        Files.write(store.etagFor(model).toPath(), "\"v1\"".getBytes());
        assertEquals(ModelStore.State.PARTIAL, store.stateOf(model));
        ModelDownloader.Result r = run(null, () -> false);
        assertTrue(r.message, r.isSuccess());
        RecordedRequest req = requests.get(0);
        assertEquals("bytes=1048576-", req.getHeader("Range"));
        assertEquals("\"v1\"", req.getHeader("If-Range"));
        assertArrayEquals(data, Files.readAllBytes(store.fileFor(model).toPath()));
    }

    @Test
    public void restartsFromZeroWhenServerIgnoresRange() throws Exception {
        honorRange = false;
        store.dir().mkdirs();
        Files.write(store.partFor(model).toPath(), java.util.Arrays.copyOf(data, 1024 * 1024));
        ModelDownloader.Result r = run(null, () -> false);
        assertTrue(r.message, r.isSuccess());
        assertArrayEquals(data, Files.readAllBytes(store.fileFor(model).toPath()));
    }

    @Test
    public void pauseKeepsPartialDataAndALaterRunCompletes() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        // The supplier is polled once per chunk read, so this pauses after a few chunks, deterministically.
        ModelDownloader.Result paused = run(null, () -> reads.incrementAndGet() > 5);
        assertEquals(paused.message, ModelDownloader.Status.PAUSED, paused.status);
        long kept = store.partialBytes(model);
        assertTrue(kept > 0 && kept < data.length);
        assertEquals(ModelStore.State.PARTIAL, store.stateOf(model));

        ModelDownloader.Result done = run(null, () -> false);
        assertTrue(done.message, done.isSuccess());
        assertArrayEquals(data, Files.readAllBytes(store.fileFor(model).toPath()));
        assertEquals("bytes=" + kept + "-", requests.get(requests.size() - 1).getHeader("Range"));
    }

    @Test
    public void tamperedDataIsRejectedAndDeleted() throws Exception {
        served = data.clone();
        served[1000] ^= 0x5A;
        ModelDownloader.Result r = run(null, () -> false);
        assertEquals(r.message, ModelDownloader.Status.CORRUPT, r.status);
        assertFalse(store.fileFor(model).exists());
        assertFalse(store.partFor(model).exists());
        assertEquals(ModelStore.State.NOT_DOWNLOADED, store.stateOf(model));
    }

    @Test
    public void serverSendingTooMuchIsRejected() throws Exception {
        served = java.util.Arrays.copyOf(data, data.length + 4096);
        ModelDownloader.Result r = run(null, () -> false);
        assertEquals(r.message, ModelDownloader.Status.CORRUPT, r.status);
        assertFalse(store.partFor(model).exists());
        assertFalse(store.fileFor(model).exists());
    }

    @Test
    public void insufficientSpaceStopsBeforeAnyRequest() {
        freeSpace = 10L * 1024 * 1024;
        ModelDownloader.Result r = run(null, () -> false);
        assertEquals(ModelDownloader.Status.INSUFFICIENT_SPACE, r.status);
        assertEquals(0, server.getRequestCount());
        assertTrue(r.message.contains("MiB"));
    }

    @Test
    public void spaceCheckAccountsForAlreadyDownloadedBytes() throws Exception {
        store.dir().mkdirs();
        Files.write(store.partFor(model).toPath(), java.util.Arrays.copyOf(data, 3 * 1024 * 1024));
        freeSpace = 256L * 1024 * 1024 + 200 * 1024; // enough only for the remaining ~123 bytes plus margin
        ModelDownloader.Result r = run(null, () -> false);
        assertTrue(r.message, r.isSuccess());
    }

    @Test
    public void httpErrorsAreReportedWithoutLeavingFiles() {
        forcedStatus = 404;
        ModelDownloader.Result r = run(null, () -> false);
        assertEquals(ModelDownloader.Status.HTTP_ERROR, r.status);
        assertEquals(ModelStore.State.NOT_DOWNLOADED, store.stateOf(model));
        forcedStatus = 500;
        assertEquals(ModelDownloader.Status.HTTP_ERROR, run(null, () -> false).status);
    }

    @Test
    public void droppedConnectionKeepsPartialDataForResume() throws Exception {
        policy = SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY;
        ModelDownloader.Result r = run(null, () -> false);
        assertEquals(r.message, ModelDownloader.Status.NETWORK_ERROR, r.status);
        assertNotEquals(ModelStore.State.INSTALLED, store.stateOf(model));
        policy = SocketPolicy.KEEP_OPEN;
        ModelDownloader.Result again = run(null, () -> false);
        assertTrue(again.message, again.isSuccess());
        assertArrayEquals(data, Files.readAllBytes(store.fileFor(model).toPath()));
    }

    private static void assertNotEquals(Object a, Object b) {
        assertFalse(a.equals(b));
    }

    @Test
    public void rejectedResumePositionRestartsCleanlyOnNextTry() throws Exception {
        store.dir().mkdirs();
        // A stale partial file that is longer than the real file makes the server answer 416.
        Files.write(store.partFor(model).toPath(), java.util.Arrays.copyOf(data, data.length - 1));
        served = java.util.Arrays.copyOf(data, 100);
        ModelDownloader.Result r = run(null, () -> false);
        assertEquals(r.message, ModelDownloader.Status.NETWORK_ERROR, r.status);
        assertFalse("bad partial data is discarded", store.partFor(model).exists());
    }

    @Test
    public void alreadyInstalledModelIsNotDownloadedAgain() throws Exception {
        assertTrue(run(null, () -> false).isSuccess());
        int before = server.getRequestCount();
        ModelDownloader.Result r = run(null, () -> false);
        assertTrue(r.isSuccess());
        assertEquals(before, server.getRequestCount());
    }

    @Test
    public void fileWithoutVerificationMarkerIsNeverTrusted() throws Exception {
        store.dir().mkdirs();
        Files.write(store.fileFor(model).toPath(), data); // right size, but nobody verified it
        assertNotEquals(ModelStore.State.INSTALLED, store.stateOf(model));
    }

    @Test
    public void deleteRemovesEverythingForTheModel() throws Exception {
        assertTrue(run(null, () -> false).isSuccess());
        store.delete(model);
        assertEquals(ModelStore.State.NOT_DOWNLOADED, store.stateOf(model));
        String[] left = store.dir().list();
        assertNotNull(left);
        assertEquals(0, left.length);
    }
}
