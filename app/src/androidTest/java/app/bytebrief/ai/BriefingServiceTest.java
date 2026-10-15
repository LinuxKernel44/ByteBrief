package app.bytebrief.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import app.bytebrief.ai.model.DailyBriefing;
import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.ranking.Personalizer;
import app.bytebrief.ranking.RankedItem;
import app.bytebrief.repo.Ingestor;

public class BriefingServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
    private static final long NOW = CLOCK.millis();
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);

    private AppDatabase db;
    private StubEngine engine;
    private BriefingService service;
    private ExecutorService io;
    private final AtomicBoolean learning = new AtomicBoolean(true);

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        engine = new StubEngine();
        io = Executors.newFixedThreadPool(2);
        service = new BriefingService(db, new Personalizer(db.affinityDao(), learning::get, CLOCK), engine, io, CLOCK, ZoneId.of("UTC"));
    }

    @After
    public void tearDown() {
        io.shutdownNow();
        db.close();
    }

    private ContentItem add(SourceType type, String id, String title, long ageHours, long score, String topics) {
        ContentItem item = new ItemFactory(CLOCK).create(type, id, title, "https://example.com/" + type.code + "/" + id, "a",
                NOW - ageHours * HOUR, "Description of " + title, null, BodyState.SNIPPET,
                topics == null ? null : Arrays.asList(topics.split(",")), null);
        item.score = score;
        new Ingestor(db, CLOCK).ingest(Collections.singletonList(item));
        return db.contentDao().findBySource(type.code, id);
    }

    private static <T> AiCall.Outcome<T> get(AiCall<T> c) throws Exception {
        return c.future().get(10, TimeUnit.SECONDS);
    }

    private static List<String> titles(List<RankedItem> sel) {
        List<String> t = new ArrayList<>();
        for (RankedItem r : sel) t.add(r.entry.item.title);
        return t;
    }

    @Test
    public void selectionExcludesReadDislikedAndStaleItems() {
        add(SourceType.RSS, "fresh", "Fresh", 2, 0, null);
        ContentItem read = add(SourceType.RSS, "read", "Read already", 2, 0, null);
        ContentItem disliked = add(SourceType.RSS, "bad", "Disliked", 2, 0, null);
        add(SourceType.RSS, "old", "Too old", 80, 0, null);
        add(SourceType.GITHUB_RELEASE, "rel", "Release 3 days ago", 72, 0, "release");
        add(SourceType.GITHUB_RELEASE, "oldrel", "Release 9 days ago", 9 * 24, 0, "release");
        db.contentDao().setRead(read.id, true, 1);
        db.contentDao().setFeedback(disliked.id, -1, 1);
        List<String> t = titles(service.select());
        assertTrue(t.toString(), t.contains("Fresh") && t.contains("Release 3 days ago"));
        assertFalse(t.contains("Read already") || t.contains("Disliked") || t.contains("Too old") || t.contains("Release 9 days ago"));
    }

    @Test
    public void selectionIsDeterministicAndCapped() {
        for (int i = 0; i < 25; i++) add(SourceType.HACKER_NEWS, "h" + i, "HN " + i, i % 6, 10 + i, null);
        for (int i = 0; i < 6; i++) add(SourceType.RSS, "r" + i, "RSS " + i, 3, 0, null);
        List<RankedItem> a = service.select();
        List<RankedItem> b = service.select();
        assertEquals(BriefingService.MAX_ITEMS, a.size());
        assertEquals(titles(a), titles(b));
        int hn = 0;
        int rss = 0;
        for (RankedItem r : a) {
            if (r.entry.item.sourceType.equals("hn")) hn++;
            if (r.entry.item.sourceType.equals("rss")) rss++;
        }
        // Variety is preferred (4 per source first) but empty slots are still filled from the best leftovers.
        assertTrue("both sources are represented: hn=" + hn + " rss=" + rss, hn >= BriefingService.MAX_PER_SOURCE && rss >= BriefingService.MAX_PER_SOURCE);
    }

    @Test
    public void withoutAModelTheBriefingStillListsRankedItemsWithRankerReasons() throws Exception {
        add(SourceType.HACKER_NEWS, "1", "Popular story", 1, 900, null);
        add(SourceType.RSS, "2", "Blog post", 5, 0, null);
        AiCall.Outcome<BriefingService.View> o = get(service.generate(false, AiProgressListener.NONE));
        assertTrue(o.result.toString(), o.result.isSuccess());
        BriefingService.View v = o.result.value;
        assertFalse(v.aiGenerated);
        assertNull(v.headline);
        assertEquals(2, v.rows.size());
        assertNull(v.rows.get(0).why);
        assertFalse("ranker reasons are always available", v.rows.get(0).rankerReasons.isEmpty());
        assertTrue(v.note.contains("Load a language model"));
        assertEquals(0, engine.calls.get());
    }

    @Test
    public void modelWritesReasonsButSelectionAndOrderStayDeterministic() throws Exception {
        ContentItem a = add(SourceType.HACKER_NEWS, "1", "Top story", 1, 900, null);
        ContentItem b = add(SourceType.RSS, "2", "Other post", 6, 0, null);
        engine.ready(ModelCatalog.defaultModel());
        engine.briefing = () -> AiResult.success(new DailyBriefing("Two things today.",
                Arrays.asList(new DailyBriefing.Entry(b.id, "Matters because B."), new DailyBriefing.Entry(a.id, "Matters because A.")),
                Collections.emptyList()));
        List<String> expectedOrder = titles(service.select());
        BriefingService.View v = get(service.generate(false, AiProgressListener.NONE)).result.value;
        assertTrue(v.aiGenerated);
        assertEquals("Two things today.", v.headline);
        List<String> actualOrder = new ArrayList<>();
        for (BriefingService.Row r : v.rows) actualOrder.add(r.item.title);
        assertEquals("the model must not reorder the deterministic selection", expectedOrder, actualOrder);
        assertEquals("Matters because A.", v.rows.get(0).why);
        assertEquals(2, engine.lastBriefingCandidates.size());
        assertEquals(1, engine.lastBriefingCandidates.get(0).number);
    }

    @Test
    public void briefingIsCachedAndInvalidatedWhenTheContentSetChanges() throws Exception {
        ContentItem a = add(SourceType.HACKER_NEWS, "1", "Top story", 1, 900, null);
        engine.ready(ModelCatalog.defaultModel());
        engine.briefing = () -> AiResult.success(new DailyBriefing("Headline.", Arrays.asList(new DailyBriefing.Entry(a.id, "Why A.")), Collections.emptyList()));
        AiCall.Outcome<BriefingService.View> first = get(service.generate(false, AiProgressListener.NONE));
        assertFalse(first.fromCache);
        AiCall.Outcome<BriefingService.View> second = get(service.generate(false, AiProgressListener.NONE));
        assertTrue(second.fromCache);
        assertEquals(1, engine.calls.get());
        assertEquals("Why A.", second.result.value.rows.get(0).why);

        add(SourceType.RSS, "new", "New arrival", 1, 0, null); // content set changes
        ContentItem b = db.contentDao().findBySource("rss", "new");
        engine.briefing = () -> AiResult.success(new DailyBriefing("Headline 2.", Arrays.asList(new DailyBriefing.Entry(b.id, "Why B.")), Collections.emptyList()));
        AiCall.Outcome<BriefingService.View> third = get(service.generate(false, AiProgressListener.NONE));
        assertFalse(third.fromCache);
        assertEquals(2, engine.calls.get());
        assertEquals("Headline 2.", third.result.value.headline);
    }

    @Test
    public void modelFailureStillYieldsAUsableBriefingWithAnHonestNote() throws Exception {
        add(SourceType.HACKER_NEWS, "1", "Top story", 1, 900, null);
        engine.ready(ModelCatalog.defaultModel());
        engine.briefing = () -> AiResult.failure(AiResult.Status.MALFORMED_OUTPUT, "bad");
        AiCall.Outcome<BriefingService.View> o = get(service.generate(false, AiProgressListener.NONE));
        assertTrue(o.result.isSuccess());
        assertFalse(o.result.value.aiGenerated);
        assertTrue(o.result.value.note.contains("could not write the reasons"));
        assertEquals(1, o.result.value.rows.size());
    }

    @Test
    public void emptyStoreIsInsufficientContent() throws Exception {
        AiCall.Outcome<BriefingService.View> o = get(service.generate(false, AiProgressListener.NONE));
        assertEquals(AiResult.Status.INSUFFICIENT_CONTENT, o.result.status);
    }

    @Test
    public void cancelledGenerationReportsCancelled() throws Exception {
        add(SourceType.HACKER_NEWS, "1", "Top story", 1, 900, null);
        engine.ready(ModelCatalog.defaultModel());
        engine.briefing = () -> AiResult.failure(AiResult.Status.CANCELLED, "Cancelled");
        assertEquals(AiResult.Status.CANCELLED, get(service.generate(false, AiProgressListener.NONE)).result.status);
    }

    @Test
    public void dateKeyFollowsTheConfiguredZone() {
        BriefingService paris = new BriefingService(db, new Personalizer(db.affinityDao(), () -> true, CLOCK), engine, io,
                Clock.fixed(Instant.parse("2026-10-15T23:30:00Z"), ZoneOffset.UTC), ZoneId.of("Europe/Paris"));
        assertEquals("2026-10-16", paris.dateKey());
        assertEquals("2026-10-15", service.dateKey());
    }
}
