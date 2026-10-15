package app.bytebrief.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import app.bytebrief.ai.model.Answer;
import app.bytebrief.ai.model.Summary;
import app.bytebrief.data.db.AiOutput;
import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.AiStatus;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.repo.Ingestor;

public class AiServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private AppDatabase db;
    private StubEngine engine;
    private AiService service;
    private ExecutorService io;
    private long itemId;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        engine = new StubEngine();
        io = Executors.newFixedThreadPool(2);
        service = new AiService(db, engine, io, CLOCK, item -> Arrays.asList("alice: nice", "bob: hmm"));
        itemId = ingest("A long enough body of text that describes the item in some detail for the model to read. ".repeat(5));
    }

    @After
    public void tearDown() {
        io.shutdownNow();
        db.close();
    }

    private long ingest(String body) {
        ContentItem item = new ItemFactory(CLOCK).create(SourceType.RSS, "1:a", "Title", "https://a.example/x", "au",
                1000, null, body, BodyState.FULL, Collections.singletonList("java"), null);
        item.metadataJson = "{\"hn_id\":5}";
        new Ingestor(db, CLOCK).ingest(Collections.singletonList(item));
        return db.contentDao().findBySource("rss", "1:a").id;
    }

    private static <T> AiCall.Outcome<T> get(AiCall<T> c) throws Exception {
        return c.future().get(10, TimeUnit.SECONDS);
    }

    @Test
    public void noModelAndNoCacheReportsUnavailableHonestly() throws Exception {
        AiCall.Outcome<Summary> o = get(service.summarize(itemId, false, AiProgressListener.NONE));
        assertEquals(AiResult.Status.MODEL_UNAVAILABLE, o.result.status);
        assertFalse(o.fromCache);
        assertEquals(0, engine.calls.get());
    }

    @Test
    public void resultIsStoredThenServedFromCacheEvenWithoutAModel() throws Exception {
        engine.ready(ModelCatalog.defaultModel());
        AiCall.Outcome<Summary> first = get(service.summarize(itemId, false, AiProgressListener.NONE));
        assertTrue(first.result.toString(), first.result.isSuccess());
        assertFalse(first.fromCache);
        assertEquals(AiStatus.DONE, db.contentDao().getById(itemId).aiStatus);
        AiOutput stored = db.aiOutputDao().find(itemId, AiOutput.KIND_SUMMARY);
        assertNotNull(stored);
        assertEquals(ModelCatalog.DEFAULT_ID, stored.modelId);

        engine.releaseResources().get(); // unload the model: cached text must still be readable
        AiCall.Outcome<Summary> second = get(service.summarize(itemId, false, AiProgressListener.NONE));
        assertTrue(second.result.isSuccess());
        assertTrue(second.fromCache);
        assertEquals("A summary.", second.result.value.summary);
        assertEquals(1, engine.calls.get());
    }

    @Test
    public void changedContentInvalidatesTheCache() throws Exception {
        engine.ready(ModelCatalog.defaultModel());
        get(service.summarize(itemId, false, AiProgressListener.NONE));
        ingest("The article was edited and now says something quite different from before, in other words. ".repeat(5));
        engine.summary = () -> AiResult.success(new Summary("A new summary.", "", false, false));
        AiCall.Outcome<Summary> o = get(service.summarize(itemId, false, AiProgressListener.NONE));
        assertFalse("content changed, so the stored summary is stale", o.fromCache);
        assertEquals("A new summary.", o.result.value.summary);
        assertEquals(2, engine.calls.get());
    }

    @Test
    public void forceRegeneratesEvenWhenCached() throws Exception {
        engine.ready(ModelCatalog.defaultModel());
        get(service.summarize(itemId, false, AiProgressListener.NONE));
        get(service.summarize(itemId, true, AiProgressListener.NONE));
        assertEquals(2, engine.calls.get());
    }

    @Test
    public void outputFromAnotherModelIsFlaggedStale() throws Exception {
        engine.ready(ModelCatalog.defaultModel());
        get(service.summarize(itemId, false, AiProgressListener.NONE));
        engine.ready(ModelCatalog.find(ModelCatalog.LIGHT_ID));
        AiCall.Outcome<Summary> o = get(service.summarize(itemId, false, AiProgressListener.NONE));
        assertTrue(o.fromCache);
        assertTrue("generated by a different model than the active one", o.stale);
        assertEquals(ModelCatalog.DEFAULT_ID, o.modelId);
    }

    @Test
    public void failuresAreRecordedAndNeverCached() throws Exception {
        engine.ready(ModelCatalog.defaultModel());
        engine.summary = () -> AiResult.failure(AiResult.Status.MALFORMED_OUTPUT, "bad");
        AiCall.Outcome<Summary> o = get(service.summarize(itemId, false, AiProgressListener.NONE));
        assertEquals(AiResult.Status.MALFORMED_OUTPUT, o.result.status);
        assertEquals(AiStatus.FAILED, db.contentDao().getById(itemId).aiStatus);
        assertEquals(null, db.aiOutputDao().find(itemId, AiOutput.KIND_SUMMARY));
    }

    @Test
    public void cancellingDoesNotMarkTheItemAsFailed() throws Exception {
        engine.ready(ModelCatalog.defaultModel());
        engine.held = new java.util.concurrent.CompletableFuture<>();
        AiCall<Summary> call = service.summarize(itemId, false, AiProgressListener.NONE);
        Thread.sleep(300);
        call.cancel();
        AiCall.Outcome<Summary> o = get(call);
        assertEquals(AiResult.Status.CANCELLED, o.result.status);
        assertEquals(AiStatus.NONE, db.contentDao().getById(itemId).aiStatus);
    }

    @Test
    public void inputIncludesSourceFactsAndFlagsPartialText() throws Exception {
        engine.ready(ModelCatalog.defaultModel());
        db.contentDao().setBody(itemId, null, BodyState.SNIPPET, "h", 1);
        ContentItem teaser = db.contentDao().getById(itemId);
        teaser.description = "Only a teaser sentence here.";
        db.contentDao().update(teaser);
        get(service.summarize(itemId, true, AiProgressListener.NONE));
        assertTrue("partial text is disclosed to the model", engine.lastInput.textIsPartial);
        assertEquals("Only a teaser sentence here.", engine.lastInput.text);
    }

    @Test
    public void discussionIsLoadedOnlyWhenAsked() throws Exception {
        engine.ready(ModelCatalog.defaultModel());
        AiCall.Outcome<Answer> without = get(service.ask(itemId, "What is it?", false, AiProgressListener.NONE));
        assertTrue(without.result.isSuccess());
        assertTrue(engine.lastInput.discussion.isEmpty());
        get(service.ask(itemId, "Summarize the discussion", true, AiProgressListener.NONE));
        assertEquals(Arrays.asList("alice: nice", "bob: hmm"), engine.lastInput.discussion);
    }

    @Test
    public void discussionFailureDegradesToStoredText() throws Exception {
        AiService offline = new AiService(db, engine, io, CLOCK, item -> { throw app.bytebrief.net.SourceException.network("offline", null); });
        engine.ready(ModelCatalog.defaultModel());
        AiCall.Outcome<Answer> o = get(offline.ask(itemId, "q?", true, AiProgressListener.NONE));
        assertTrue(o.result.isSuccess());
        assertTrue(engine.lastInput.discussion.isEmpty());
    }

    @Test
    public void unknownItemIsAnError() throws Exception {
        engine.ready(ModelCatalog.defaultModel());
        assertEquals(AiResult.Status.ERROR, get(service.summarize(99_999, false, AiProgressListener.NONE)).result.status);
    }
}
