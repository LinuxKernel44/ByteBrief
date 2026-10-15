package app.bytebrief.ranking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import app.bytebrief.data.db.Affinity;
import app.bytebrief.data.db.AffinityDao;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.testutil.Fixtures;

public class PersonalizerTest {
    /** In-memory stand-in for the Room DAO. */
    static class FakeDao implements AffinityDao {
        final Map<String, Affinity> rows = new LinkedHashMap<>();
        int writes;

        private static String k(String kind, String key) {
            return kind + "|" + key;
        }

        @Override public void upsert(Affinity a) { rows.put(k(a.kind, a.key), a); writes++; }
        @Override public Affinity find(String kind, String key) { return rows.get(k(kind, key)); }
        @Override public List<Affinity> getAll() { return new ArrayList<>(rows.values()); }
        @Override public LiveData<List<Affinity>> observeAll() { return null; }
        @Override public LiveData<List<Affinity>> observeExplicitTopics() { return null; }
        @Override public void deleteExplicit(String kind, String key) {
            Affinity a = rows.get(k(kind, key));
            if (a != null && a.explicit) rows.remove(k(kind, key));
        }
        @Override public void deleteLearned() { rows.values().removeIf(a -> !a.explicit); }
        @Override public void deleteAll() { rows.clear(); }
    }

    private final FakeDao dao = new FakeDao();
    private final AtomicBoolean learning = new AtomicBoolean(true);
    private final Personalizer p = new Personalizer(dao, learning::get, Fixtures.CLOCK);

    private static ContentItem item(String topics) {
        ContentItem i = new ContentItem();
        i.sourceType = "rss";
        i.feedId = 3L;
        i.topics = topics;
        return i;
    }

    @Test
    public void openingAnItemRaisesItsTopicsAndSource() {
        p.onOpened(item("rust,async"));
        assertEquals(Personalizer.DELTA_OPEN, dao.find("topic", "rust").weight, 1e-12);
        assertEquals(Personalizer.DELTA_OPEN, dao.find("topic", "async").weight, 1e-12);
        assertEquals(Personalizer.DELTA_OPEN * 0.5, dao.find("source", "rss:3").weight, 1e-12);
    }

    @Test
    public void learningDisabledWritesNothing() {
        learning.set(false);
        p.onOpened(item("rust"));
        p.onBookmarked(item("rust"), true);
        p.onFeedback(item("rust"), 1);
        assertEquals(0, dao.writes);
        assertTrue(dao.getAll().isEmpty());
    }

    @Test
    public void feedbackMovesWeightsInBothDirectionsAndClamps() {
        for (int i = 0; i < 20; i++) p.onFeedback(item("good"), 1);
        assertEquals(1.0, dao.find("topic", "good").weight, 1e-12);
        for (int i = 0; i < 20; i++) p.onFeedback(item("bad"), -1);
        assertEquals(-1.0, dao.find("topic", "bad").weight, 1e-12);
    }

    @Test
    public void unbookmarkingAndClearingFeedbackDoNotUnlearn() {
        p.onBookmarked(item("x"), false);
        p.onFeedback(item("x"), 0);
        assertEquals(0, dao.writes);
    }

    @Test
    public void weightsDecayTowardsZeroWithoutReinforcement() {
        assertEquals(1.0 * Personalizer.DECAY, Personalizer.next(1.0, 0), 1e-12);
        assertEquals(0.5 * Personalizer.DECAY + 0.2, Personalizer.next(0.5, 0.2), 1e-12);
    }

    @Test
    public void explicitInterestsAreFixedAndSurviveLearnedReset() {
        p.addInterest("  Machine Learning ");
        p.onFeedback(item("machine-learning"), -1);
        assertEquals(1.0, dao.find("topic", "machine-learning").weight, 1e-12);
        p.onOpened(item("rust"));
        p.resetLearned();
        assertNotNull(dao.find("topic", "machine-learning"));
        assertNull(dao.find("topic", "rust"));
        assertNull(dao.find("source", "rss:3"));
        p.resetAll();
        assertTrue(dao.getAll().isEmpty());
    }

    @Test
    public void removeInterestOnlyRemovesExplicitOnes() {
        p.onOpened(item("rust"));
        p.removeInterest("rust");
        assertNotNull("learned weight is not an explicit interest", dao.find("topic", "rust"));
        p.addInterest("kotlin");
        p.removeInterest("Kotlin");
        assertNull(dao.find("topic", "kotlin"));
    }

    @Test
    public void blankInterestIsIgnored() {
        p.addInterest("   ");
        assertTrue(dao.getAll().isEmpty());
    }

    @Test
    public void buildContextSeparatesTopicsSourcesAndExplicitInterests() {
        p.addInterest("rust");
        p.onOpened(item("go"));
        RankingContext c = p.buildContext();
        assertEquals(1.0, c.topicWeights.get("rust"), 1e-12);
        assertTrue(c.explicitInterests.contains("rust"));
        assertFalse(c.explicitInterests.contains("go"));
        assertTrue(c.topicWeights.containsKey("go"));
        assertTrue(c.sourceWeights.containsKey("rss:3"));
        assertEquals(Fixtures.CLOCK.millis(), c.nowMillis);
    }
}
