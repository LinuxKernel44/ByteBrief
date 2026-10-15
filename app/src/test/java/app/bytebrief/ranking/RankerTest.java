package app.bytebrief.ranking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.ContentListItem;
import app.bytebrief.domain.SourceType;
import app.bytebrief.testutil.Fixtures;

public class RankerTest {
    private static final long NOW = Fixtures.CLOCK.millis();
    private static long nextId = 1;

    private static ContentListItem entry(SourceType type, String title, long ageHours, String topics, long score, int group) {
        ContentItem i = new ContentItem();
        i.id = nextId++;
        i.sourceType = type.code;
        i.sourceId = "s" + i.id;
        i.title = title;
        i.publishedAt = NOW - TimeUnit.HOURS.toMillis(ageHours);
        i.topics = topics;
        i.score = score;
        ContentListItem c = new ContentListItem();
        c.item = i;
        c.groupSize = group;
        return c;
    }

    private static ContentListItem rss(String title, long ageHours) {
        return entry(SourceType.RSS, title, ageHours, null, 0, 1);
    }

    private static RankingContext ctx() {
        return RankingContext.empty(NOW);
    }

    private static double scoreOf(ContentListItem e, RankingContext c) {
        return Ranker.score(e, c).score;
    }

    @Test
    public void newerBeatsOlderWhenNothingElseDiffers() {
        assertTrue(scoreOf(rss("a", 1), ctx()) > scoreOf(rss("a", 30), ctx()));
    }

    @Test
    public void recencyHalvesAtTheSourceHalfLife() {
        double fresh = scoreOf(rss("a", 0), ctx());
        double halfLife = scoreOf(rss("a", 36), ctx());
        assertEquals(Ranker.W_RECENCY, fresh, 1e-9);
        assertEquals(Ranker.W_RECENCY / 2, halfLife, 1e-9);
    }

    @Test
    public void hnPointsHelpWithDiminishingReturns() {
        double low = scoreOf(entry(SourceType.HACKER_NEWS, "t", 2, null, 10, 1), ctx());
        double mid = scoreOf(entry(SourceType.HACKER_NEWS, "t", 2, null, 100, 1), ctx());
        double high = scoreOf(entry(SourceType.HACKER_NEWS, "t", 2, null, 1000, 1), ctx());
        double huge = scoreOf(entry(SourceType.HACKER_NEWS, "t", 2, null, 1_000_000, 1), ctx());
        assertTrue(mid > low && high > mid);
        assertTrue("capped", huge - high < mid - low);
        assertTrue(huge - high < Ranker.W_POPULARITY);
    }

    @Test
    public void totalGithubStarsDoNotInfluenceRanking() {
        ContentListItem few = entry(SourceType.GITHUB_TRENDING, "a/b", 5, null, 12, 1);
        ContentListItem many = entry(SourceType.GITHUB_TRENDING, "c/d", 5, null, 900_000, 1);
        few.item.metadataJson = "{\"stars_in_period\":100}";
        many.item.metadataJson = "{\"stars_in_period\":100}";
        assertEquals(scoreOf(few, ctx()), scoreOf(many, ctx()), 1e-12);
    }

    @Test
    public void trendingPopularityUsesStarsGainedAndIgnoresMissingData() {
        ContentListItem gained = entry(SourceType.GITHUB_TRENDING, "a/b", 5, null, 10, 1);
        gained.item.metadataJson = "{\"stars_in_period\":500}";
        ContentListItem unknown = entry(SourceType.GITHUB_TRENDING, "c/d", 5, null, 10, 1);
        ContentListItem broken = entry(SourceType.GITHUB_TRENDING, "e/f", 5, null, 10, 1);
        broken.item.metadataJson = "{not json";
        assertTrue(scoreOf(gained, ctx()) > scoreOf(unknown, ctx()));
        assertEquals(scoreOf(unknown, ctx()), scoreOf(broken, ctx()), 1e-12);
    }

    @Test
    public void explicitInterestsBoostTopicAndTitleMatches() {
        RankingContext c = RankingContext.of(NOW, Collections.singletonMap("rust", 1.0),
                new HashSet<>(Arrays.asList("rust", "llm")), Collections.emptyMap());
        double byTopic = scoreOf(entry(SourceType.RSS, "x", 5, "rust", 0, 1), c);
        double byTitle = scoreOf(entry(SourceType.RSS, "Running an LLM on a phone", 5, null, 0, 1), c);
        double none = scoreOf(entry(SourceType.RSS, "x", 5, "gardening", 0, 1), c);
        assertEquals(none + Ranker.W_INTEREST, byTopic, 1e-9);
        assertTrue(byTitle > none);
    }

    @Test
    public void titleMatchRespectsWordBoundaries() {
        RankingContext c = RankingContext.of(NOW, Collections.emptyMap(), Collections.singleton("go"),
                Collections.emptyMap());
        double word = scoreOf(entry(SourceType.RSS, "Why Go is fast", 5, null, 0, 1), c);
        double inside = scoreOf(entry(SourceType.RSS, "Google announces things", 5, null, 0, 1), c);
        assertTrue(word > inside);
    }

    @Test
    public void negativeTopicWeightDemotes() {
        RankingContext c = RankingContext.of(NOW, Collections.singletonMap("crypto", -0.8), Collections.emptySet(),
                Collections.emptyMap());
        assertTrue(scoreOf(entry(SourceType.RSS, "x", 5, "crypto", 0, 1), c) < scoreOf(rss("x", 5), c));
    }

    @Test
    public void feedbackBookmarkAndReadAdjustTheScore() {
        ContentListItem base = rss("x", 5);
        double plain = scoreOf(base, ctx());
        base.item.feedback = 1;
        assertEquals(plain + Ranker.FEEDBACK_UP, scoreOf(base, ctx()), 1e-9);
        base.item.feedback = -1;
        assertEquals(plain + Ranker.FEEDBACK_DOWN, scoreOf(base, ctx()), 1e-9);
        base.item.feedback = 0;
        base.item.isBookmarked = true;
        assertEquals(plain + Ranker.BOOKMARK, scoreOf(base, ctx()), 1e-9);
        base.item.isBookmarked = false;
        base.item.isRead = true;
        assertEquals(plain + Ranker.READ_PENALTY, scoreOf(base, ctx()), 1e-9);
    }

    @Test
    public void corroborationByMultipleSourcesHelpsUpToACap() {
        double one = scoreOf(entry(SourceType.RSS, "x", 5, null, 0, 1), ctx());
        double two = scoreOf(entry(SourceType.RSS, "x", 5, null, 0, 2), ctx());
        double four = scoreOf(entry(SourceType.RSS, "x", 5, null, 0, 4), ctx());
        double nine = scoreOf(entry(SourceType.RSS, "x", 5, null, 0, 9), ctx());
        assertTrue(two > one && four > two);
        assertEquals(four, nine, 1e-12);
    }

    @Test
    public void sourcePreferenceUsesFeedKeyWhenPresent() {
        ContentListItem fromFeed = rss("x", 5);
        fromFeed.item.feedId = 12L;
        Map<String, Double> sources = new HashMap<>();
        sources.put("rss:12", 1.0);
        double boosted = scoreOf(fromFeed, RankingContext.of(NOW, Collections.emptyMap(), Collections.emptySet(), sources));
        assertEquals(scoreOf(fromFeed, ctx()) + Ranker.W_SOURCE, boosted, 1e-9);
    }

    @Test
    public void reasonsExplainTheScoreSortedByMagnitude() {
        ContentListItem e = entry(SourceType.HACKER_NEWS, "Rust async", 1, "rust", 500, 3);
        e.item.isRead = true;
        RankingContext c = RankingContext.of(NOW, Collections.singletonMap("rust", 1.0), Collections.singleton("rust"),
                Collections.emptyMap());
        RankedItem r = Ranker.score(e, c);
        double sum = 0;
        for (RankedItem.Reason reason : r.reasons) sum += reason.contribution;
        assertEquals(r.score, sum, 1e-9);
        for (int i = 1; i < r.reasons.size(); i++) {
            assertTrue(Math.abs(r.reasons.get(i - 1).contribution) >= Math.abs(r.reasons.get(i).contribution));
        }
        List<String> labels = new ArrayList<>();
        for (RankedItem.Reason reason : r.reasons) labels.add(reason.label);
        assertTrue(labels.contains("Matches your interest: rust"));
        assertTrue(labels.contains("Covered by 3 sources"));
        assertTrue(labels.contains("Already read"));
        assertTrue(labels.contains("Popular on Hacker News (500 points)"));
    }

    @Test
    public void diversityPreventsOneSourceFillingTheList() {
        List<ContentListItem> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) items.add(entry(SourceType.HACKER_NEWS, "hn " + i, 2, null, 400, 1));
        items.add(entry(SourceType.RSS, "rss", 6, null, 0, 1));
        List<RankedItem> top = Ranker.rank(items, ctx(), 5);
        boolean rssPresent = false;
        for (RankedItem r : top) rssPresent |= r.entry.item.sourceType.equals("rss");
        assertTrue("an RSS item should surface within the top 5", rssPresent);
        assertEquals(5, top.size());
    }

    @Test
    public void diversityDoesNotReorderWhenSourcesAlreadyDiffer() {
        List<ContentListItem> items = Arrays.asList(rss("rss", 1),
                entry(SourceType.HACKER_NEWS, "hn", 1, null, 0, 1),
                entry(SourceType.GITHUB_TRENDING, "gh", 1, null, 0, 1));
        List<RankedItem> top = Ranker.rank(items, ctx(), 10);
        assertEquals(3, top.size());
    }

    @Test
    public void rankingIsDeterministicAndDoesNotMutateInput() {
        List<ContentListItem> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) items.add(entry(SourceType.RSS, "t" + i, i % 5, null, 0, 1));
        List<ContentListItem> copy = new ArrayList<>(items);
        List<RankedItem> a = Ranker.rank(items, ctx(), 20);
        List<RankedItem> b = Ranker.rank(items, ctx(), 20);
        assertEquals(copy, items);
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).entry.item.id, b.get(i).entry.item.id);
    }

    @Test
    public void tiesBreakByNewestThenId() {
        ContentListItem older = rss("same", 3);
        ContentListItem newer = rss("same", 3);
        newer.item.publishedAt = older.item.publishedAt + 1000;
        List<RankedItem> r = Ranker.rank(Arrays.asList(older, newer), ctx(), 2);
        assertEquals(newer.item.id, r.get(0).entry.item.id);
    }

    @Test
    public void limitAndEmptyInputs() {
        assertTrue(Ranker.rank(Collections.emptyList(), ctx(), 5).isEmpty());
        assertEquals(2, Ranker.rank(Arrays.asList(rss("a", 1), rss("b", 2), rss("c", 3)), ctx(), 2).size());
    }

    @Test
    public void futureDatedItemsDoNotGetARecencyBonusAboveTheMaximum() {
        ContentListItem future = rss("x", -48);
        assertEquals(Ranker.W_RECENCY, scoreOf(future, ctx()), 1e-9);
        assertFalse(Double.isNaN(scoreOf(future, ctx())));
    }
}
