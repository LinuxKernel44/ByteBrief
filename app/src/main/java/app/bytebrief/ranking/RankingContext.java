package app.bytebrief.ranking;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Everything the ranker knows about the user. Built locally from stored affinities; never leaves the device. */
public final class RankingContext {
    public final long nowMillis;
    /** Topic weights in [-1, 1]: explicit interests are 1.0, learned ones move with behaviour. */
    public final Map<String, Double> topicWeights;
    /** Lower-case interests the user chose; also matched against titles. */
    public final Set<String> explicitInterests;
    /** Source preference in [-1, 1], keyed by {@link Ranker#sourceKey}. */
    public final Map<String, Double> sourceWeights;

    public RankingContext(long nowMillis, Map<String, Double> topicWeights, Set<String> explicitInterests,
                          Map<String, Double> sourceWeights) {
        this.nowMillis = nowMillis;
        this.topicWeights = topicWeights;
        this.explicitInterests = explicitInterests;
        this.sourceWeights = sourceWeights;
    }

    public static RankingContext empty(long nowMillis) {
        return new RankingContext(nowMillis, Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap());
    }

    public static RankingContext of(long nowMillis, Map<String, Double> topics, Set<String> explicit,
                                    Map<String, Double> sources) {
        return new RankingContext(nowMillis, new HashMap<>(topics), new HashSet<>(explicit), new HashMap<>(sources));
    }
}
