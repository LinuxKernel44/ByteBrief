package app.bytebrief.ranking;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.ContentListItem;
import app.bytebrief.domain.SourceType;
import app.bytebrief.domain.Topics;

/**
 * Transparent, deterministic, local ranking. The score is a weighted sum of a few named signals,
 * and every non-zero signal is returned as a {@link RankedItem.Reason} so the UI can answer
 * "why am I seeing this?". The score is a relative ordering aid, not a measure of quality, and is
 * not meant to be shown as a percentage.
 *
 * <pre>
 * score = 0.45 * recency            0..1, exponential decay with a per-source half-life
 *       + 0.12 * popularity         0..1, log-scaled HN points / stars gained in the period (never total stars)
 *       + 0.30 * interest           -1..1, topic weights plus explicit interests found in the title
 *       + 0.08 * source preference  -1..1
 *       + 0.10 * corroboration      0..1, the same subject appears in several sources
 *       + feedback (+0.35 / -0.60), bookmark (+0.10), already read (-0.25), followed repository (+0.10)
 * </pre>
 *
 * After scoring, selection is greedy with a diversity penalty so one source cannot fill the list.
 */
public final class Ranker {
    static final double W_RECENCY = 0.45;
    static final double W_POPULARITY = 0.12;
    static final double W_INTEREST = 0.30;
    static final double W_SOURCE = 0.08;
    static final double W_CORROBORATION = 0.10;
    static final double FEEDBACK_UP = 0.35;
    static final double FEEDBACK_DOWN = -0.60;
    static final double BOOKMARK = 0.10;
    static final double READ_PENALTY = -0.25;
    static final double FOLLOWED_REPO = 0.10;
    /** Each already-selected item from the same source multiplies the next one's score by this. */
    static final double DIVERSITY_DECAY = 0.90;
    private static final double TITLE_MATCH_WEIGHT = 0.6;

    private Ranker() {}

    /** Stable key for source-level preferences and diversity: a feed, or the source type. */
    public static String sourceKey(ContentItem item) {
        return item.feedId != null ? "rss:" + item.feedId : item.sourceType;
    }

    public static List<RankedItem> rank(List<ContentListItem> candidates, RankingContext ctx, int limit) {
        List<RankedItem> scored = new ArrayList<>(candidates.size());
        for (ContentListItem c : candidates) scored.add(score(c, ctx));
        // Deterministic order: score, then newest, then id.
        scored.sort(Comparator.<RankedItem>comparingDouble(r -> -r.score)
                .thenComparingLong(r -> -r.entry.item.publishedAt)
                .thenComparingLong(r -> r.entry.item.id));
        return diversify(scored, limit);
    }

    public static RankedItem score(ContentListItem entry, RankingContext ctx) {
        ContentItem item = entry.item;
        List<RankedItem.Reason> reasons = new ArrayList<>();
        double total = 0;

        double recency = Math.pow(0.5, ageHours(item, ctx) / halfLifeHours(item));
        total += add(reasons, "Recent", W_RECENCY * recency);

        double popularity = popularity(item);
        if (popularity > 0) total += add(reasons, popularityLabel(item), W_POPULARITY * popularity);

        InterestMatch interest = interest(item, ctx);
        if (interest.value != 0) {
            total += add(reasons, interest.label, W_INTEREST * interest.value);
        }

        Double sourcePref = ctx.sourceWeights.get(sourceKey(item));
        if (sourcePref != null && sourcePref != 0) {
            total += add(reasons, sourcePref > 0 ? "From a source you engage with" : "From a source you often skip",
                    W_SOURCE * clamp(sourcePref));
        }

        if (entry.groupSize > 1) {
            double corroboration = Math.min(entry.groupSize - 1, 3) / 3.0;
            total += add(reasons, "Covered by " + entry.groupSize + " sources", W_CORROBORATION * corroboration);
        }
        if (SourceType.GITHUB_RELEASE.code.equals(item.sourceType)) {
            total += add(reasons, "Release of a repository you follow", FOLLOWED_REPO);
        }
        if (item.feedback > 0) total += add(reasons, "You marked this as relevant", FEEDBACK_UP);
        if (item.feedback < 0) total += add(reasons, "You marked this as not relevant", FEEDBACK_DOWN);
        if (item.isBookmarked) total += add(reasons, "Bookmarked", BOOKMARK);
        if (item.isRead) total += add(reasons, "Already read", READ_PENALTY);

        reasons.sort((a, b) -> Double.compare(Math.abs(b.contribution), Math.abs(a.contribution)));
        return new RankedItem(entry, total, reasons);
    }

    // ---- signals ---------------------------------------------------------------------------

    static double ageHours(ContentItem item, RankingContext ctx) {
        return Math.max(0, (ctx.nowMillis - item.publishedAt) / 3_600_000.0);
    }

    static double halfLifeHours(ContentItem item) {
        switch (SourceType.fromCode(item.sourceType)) {
            case HACKER_NEWS: return 18;
            case GITHUB_TRENDING: return 48;
            case GITHUB_RELEASE: return 96;
            default: return 36;
        }
    }

    /** Engagement the source itself reports, on a log scale. Total GitHub stars are deliberately ignored. */
    static double popularity(ContentItem item) {
        switch (SourceType.fromCode(item.sourceType)) {
            case HACKER_NEWS:
                return clamp01(Math.log10(1 + Math.max(0, item.score)) / 3.0);
            case GITHUB_TRENDING: {
                long gained = starsInPeriod(item);
                return gained > 0 ? clamp01(Math.log10(1 + gained) / 3.5) : 0;
            }
            default:
                return 0;
        }
    }

    private static String popularityLabel(ContentItem item) {
        return SourceType.HACKER_NEWS.code.equals(item.sourceType)
                ? "Popular on Hacker News (" + item.score + " points)"
                : "Gaining stars on GitHub (+" + starsInPeriod(item) + ")";
    }

    private static long starsInPeriod(ContentItem item) {
        if (item.metadataJson == null) return 0;
        try {
            return new JSONObject(item.metadataJson).optLong("stars_in_period", 0);
        } catch (org.json.JSONException e) {
            return 0;
        }
    }

    private static final class InterestMatch {
        final double value;
        final String label;

        InterestMatch(double value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    private static InterestMatch interest(ContentItem item, RankingContext ctx) {
        double sum = 0;
        String best = null;
        double bestAbs = 0;
        for (String topic : Topics.split(item.topics)) {
            Double w = ctx.topicWeights.get(topic);
            if (w == null || w == 0) continue;
            sum += w;
            if (Math.abs(w) > bestAbs) {
                bestAbs = Math.abs(w);
                best = topic;
            }
        }
        String title = item.title.toLowerCase(Locale.ROOT);
        for (String interest : ctx.explicitInterests) {
            if (containsWord(title, interest) && !contains(item.topics, interest)) {
                sum += TITLE_MATCH_WEIGHT;
                if (best == null) best = interest;
            }
        }
        double v = clamp(sum);
        if (v == 0) return new InterestMatch(0, "");
        return new InterestMatch(v, v > 0 ? "Matches your interest: " + best : "Topic you tend to skip: " + best);
    }

    private static boolean contains(String joinedTopics, String topic) {
        for (String t : Topics.split(joinedTopics)) if (t.equals(topic)) return true;
        return false;
    }

    private static final Map<String, Pattern> WORD_PATTERNS = new HashMap<>();

    private static boolean containsWord(String haystackLower, String needleLower) {
        if (needleLower.isEmpty()) return false;
        Pattern p;
        synchronized (WORD_PATTERNS) {
            p = WORD_PATTERNS.get(needleLower);
            if (p == null) {
                p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(needleLower) + "(?![\\p{L}\\p{N}])");
                WORD_PATTERNS.put(needleLower, p);
            }
        }
        return p.matcher(haystackLower).find();
    }

    // ---- selection -------------------------------------------------------------------------

    private static List<RankedItem> diversify(List<RankedItem> sorted, int limit) {
        List<RankedItem> remaining = new ArrayList<>(sorted);
        List<RankedItem> out = new ArrayList<>();
        Map<String, Integer> taken = new HashMap<>();
        while (!remaining.isEmpty() && out.size() < limit) {
            int bestIdx = 0;
            double bestAdjusted = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < remaining.size(); i++) {
                RankedItem r = remaining.get(i);
                int n = taken.getOrDefault(sourceKey(r.entry.item), 0);
                double adjusted = r.score > 0 ? r.score * Math.pow(DIVERSITY_DECAY, n) : r.score;
                // Strict ">" keeps the earlier (higher base-ranked) item on ties.
                if (adjusted > bestAdjusted) {
                    bestAdjusted = adjusted;
                    bestIdx = i;
                }
            }
            RankedItem pick = remaining.remove(bestIdx);
            out.add(pick);
            taken.merge(sourceKey(pick.entry.item), 1, Integer::sum);
        }
        return out;
    }

    private static double add(List<RankedItem.Reason> reasons, String label, double contribution) {
        if (contribution != 0) reasons.add(new RankedItem.Reason(label, contribution));
        return contribution;
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
