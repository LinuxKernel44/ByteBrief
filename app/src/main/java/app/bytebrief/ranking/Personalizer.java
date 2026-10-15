package app.bytebrief.ranking;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import app.bytebrief.data.db.Affinity;
import app.bytebrief.data.db.AffinityDao;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.Topics;

/**
 * Learns topic and source preferences from the user's own actions and builds a {@link RankingContext}.
 * Learning can be switched off (events are then ignored) and reset at any time. Everything is stored
 * in the local database; nothing is transmitted.
 */
public class Personalizer {
    static final double DELTA_OPEN = 0.04;
    static final double DELTA_BOOKMARK = 0.12;
    static final double DELTA_LIKE = 0.20;
    static final double DELTA_DISLIKE = -0.25;
    /** Old weights fade a little on every update so tastes can change. */
    static final double DECAY = 0.99;
    private static final double SOURCE_FACTOR = 0.5;
    private static final int MAX_TOPICS_PER_EVENT = 6;

    private final AffinityDao dao;
    private final BooleanSupplier learningEnabled;
    private final Clock clock;

    public Personalizer(AffinityDao dao, BooleanSupplier learningEnabled, Clock clock) {
        this.dao = dao;
        this.learningEnabled = learningEnabled;
        this.clock = clock;
    }

    public void onOpened(ContentItem item) {
        learn(item, DELTA_OPEN);
    }

    /** @param bookmarked the new state; removing a bookmark does not unlearn. */
    public void onBookmarked(ContentItem item, boolean bookmarked) {
        if (bookmarked) learn(item, DELTA_BOOKMARK);
    }

    /** @param feedback -1, 0 or 1; 0 (clearing) does not unlearn. */
    public void onFeedback(ContentItem item, int feedback) {
        if (feedback > 0) learn(item, DELTA_LIKE);
        else if (feedback < 0) learn(item, DELTA_DISLIKE);
    }

    public void addInterest(String topic) {
        String t = Topics.normalizeOne(topic);
        if (t.isEmpty()) return;
        Affinity a = new Affinity();
        a.kind = Affinity.KIND_TOPIC;
        a.key = t;
        a.weight = 1.0;
        a.explicit = true;
        a.updatedAt = clock.millis();
        dao.upsert(a);
    }

    public void removeInterest(String topic) {
        dao.deleteExplicit(Affinity.KIND_TOPIC, Topics.normalizeOne(topic));
    }

    /** Forgets everything learned from behaviour; interests the user chose explicitly are kept. */
    public void resetLearned() {
        dao.deleteLearned();
    }

    /** Forgets everything, including explicit interests. */
    public void resetAll() {
        dao.deleteAll();
    }

    public RankingContext buildContext() {
        Map<String, Double> topics = new HashMap<>();
        Map<String, Double> sources = new HashMap<>();
        Set<String> explicit = new HashSet<>();
        for (Affinity a : dao.getAll()) {
            if (Affinity.KIND_TOPIC.equals(a.kind)) {
                topics.put(a.key, a.weight);
                if (a.explicit) explicit.add(a.key);
            } else if (Affinity.KIND_SOURCE.equals(a.kind)) {
                sources.put(a.key, a.weight);
            }
        }
        return RankingContext.of(clock.millis(), topics, explicit, sources);
    }

    private void learn(ContentItem item, double delta) {
        if (!learningEnabled.getAsBoolean()) return;
        String[] topics = Topics.split(item.topics);
        for (int i = 0; i < Math.min(topics.length, MAX_TOPICS_PER_EVENT); i++) {
            update(Affinity.KIND_TOPIC, topics[i], delta);
        }
        update(Affinity.KIND_SOURCE, Ranker.sourceKey(item), delta * SOURCE_FACTOR);
    }

    private void update(String kind, String key, double delta) {
        Affinity a = dao.find(kind, key);
        if (a == null) {
            a = new Affinity();
            a.kind = kind;
            a.key = key;
        }
        if (a.explicit) return; // explicit interests are fixed at full weight
        a.weight = next(a.weight, delta);
        a.updatedAt = clock.millis();
        dao.upsert(a);
    }

    static double next(double current, double delta) {
        return Math.max(-1, Math.min(1, current * DECAY + delta));
    }
}
