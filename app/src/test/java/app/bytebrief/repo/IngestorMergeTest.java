package app.bytebrief.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.AiStatus;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.testutil.Fixtures;

public class IngestorMergeTest {
    private ContentItem item(String desc, String body, int state) {
        ContentItem i = Fixtures.factory().create(SourceType.RSS, "1:a", "Title", "https://x.example/a", "au",
                1_000, desc, body, state, java.util.Arrays.asList("java"), null);
        i.id = 0;
        return i;
    }

    @Test
    public void userStateSurvivesReFetch() {
        ContentItem old = item("d", null, BodyState.SNIPPET);
        old.id = 9;
        old.isRead = true;
        old.isBookmarked = true;
        old.feedback = 1;
        old.retrievedAt = 111;
        ContentItem incoming = item("d", null, BodyState.SNIPPET);
        Ingestor.merge(old, incoming);
        assertEquals(9, incoming.id);
        assertTrue(incoming.isRead);
        assertTrue(incoming.isBookmarked);
        assertEquals(1, incoming.feedback);
        assertEquals(111, incoming.retrievedAt);
    }

    @Test
    public void richerStoredBodyIsNotReplacedByPoorerOrMissingOne() {
        ContentItem old = item("d", "full readme text", BodyState.FULL);
        ContentItem incoming = item("d", null, BodyState.SNIPPET);
        Ingestor.merge(old, incoming);
        assertEquals("full readme text", incoming.bodyText);
        assertEquals(BodyState.FULL, incoming.bodyState);

        ContentItem truncatedOld = item("d", "partial readme", BodyState.SNIPPET);
        ContentItem bare = item("d", null, BodyState.SNIPPET);
        Ingestor.merge(truncatedOld, bare);
        assertEquals("partial readme", bare.bodyText);
    }

    @Test
    public void betterIncomingBodyReplacesStoredOne() {
        ContentItem old = item("d", null, BodyState.SNIPPET);
        ContentItem incoming = item("d", "now the full article", BodyState.FULL);
        Ingestor.merge(old, incoming);
        assertEquals("now the full article", incoming.bodyText);
        assertEquals(BodyState.FULL, incoming.bodyState);
    }

    @Test
    public void aiStatusIsInvalidatedOnlyWhenTextChanges() {
        ContentItem old = item("same", null, BodyState.SNIPPET);
        old.aiStatus = AiStatus.DONE;
        ContentItem unchanged = item("same", null, BodyState.SNIPPET);
        Ingestor.merge(old, unchanged);
        assertEquals(AiStatus.DONE, unchanged.aiStatus);

        ContentItem changed = item("edited text", null, BodyState.SNIPPET);
        Ingestor.merge(old, changed);
        assertEquals(AiStatus.NONE, changed.aiStatus);
    }

    @Test
    public void topicsAndMetadataAreUnionedSoEnrichmentSurvives() throws Exception {
        ContentItem old = item("d", null, BodyState.SNIPPET);
        old.topics = "agents,reverse-engineering,java";
        old.metadataJson = "{\"license\":\"MIT\",\"enriched_at\":5,\"forks\":1}";
        ContentItem incoming = item("d", null, BodyState.SNIPPET);
        incoming.topics = "java";
        incoming.metadataJson = "{\"forks\":2}";
        Ingestor.merge(old, incoming);
        assertEquals("agents,reverse-engineering,java", incoming.topics);
        JSONObject m = new JSONObject(incoming.metadataJson);
        assertEquals("MIT", m.getString("license"));
        assertEquals(5, m.getLong("enriched_at"));
        assertEquals(2, m.getInt("forks"));
    }

    @Test
    public void earliestPublishDateWins() {
        ContentItem old = item("d", null, BodyState.SNIPPET);
        old.publishedAt = 500;
        ContentItem incoming = item("d", null, BodyState.SNIPPET);
        incoming.publishedAt = 9_000;
        Ingestor.merge(old, incoming);
        assertEquals(500, incoming.publishedAt);
    }

    @Test
    public void identicalContentIsDetectedAsUnchanged() {
        ContentItem old = item("d", null, BodyState.SNIPPET);
        ContentItem incoming = item("d", null, BodyState.SNIPPET);
        Ingestor.merge(old, incoming);
        assertTrue(Ingestor.sameContent(old, incoming));
        incoming.score = 50;
        assertFalse(Ingestor.sameContent(old, incoming));
    }

    @Test
    public void malformedMetadataFallsBackToIncoming() {
        assertEquals("{\"a\":1}", Ingestor.mergeJson("{broken", "{\"a\":1}"));
    }
}
