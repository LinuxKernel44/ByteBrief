package app.bytebrief.source.github;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import app.bytebrief.testutil.Fixtures;

public class TrendingParserTest {
    @Test
    public void parsesRealTrendingPageFixture() {
        List<TrendingParser.Repo> repos = TrendingParser.parse(Fixtures.resource("fixtures/github_trending_daily.html"));
        assertEquals(12, repos.size());
        TrendingParser.Repo first = repos.get(0);
        assertEquals("morluto", first.owner);
        assertEquals("rea", first.name);
        assertEquals("TypeScript", first.language);
        assertEquals(255_645, first.stars);
        assertEquals(64_229, first.forks);
        assertEquals(41_034, first.starsInPeriod);
        assertTrue(first.description.startsWith("Reverse engineer anything"));
    }

    @Test
    public void everyParsedRowHasUsableIdentityAndStats() {
        for (TrendingParser.Repo r : TrendingParser.parse(Fixtures.resource("fixtures/github_trending_daily.html"))) {
            assertTrue(r.owner + "/" + r.name, GithubApi.isValidSegment(r.owner) && GithubApi.isValidSegment(r.name));
            assertTrue(r.stars > 0);
            assertTrue(r.starsInPeriod >= 0);
        }
    }

    @Test
    public void dropsRowsWithoutValidRepositoryPath() {
        String html = "<article class='Box-row'><h2><a href='/login?return_to=%2Fx'>login</a></h2></article>"
                + "<article class='Box-row'><h2><a href='//evil.example/a/b'>x</a></h2></article>"
                + "<article class='Box-row'><h2><a href='/a/b/c'>x</a></h2></article>"
                + "<article class='Box-row'><h2><a href='/ok/repo'>ok / repo</a></h2></article>";
        List<TrendingParser.Repo> repos = TrendingParser.parse(html);
        assertEquals(1, repos.size());
        assertEquals("ok", repos.get(0).owner);
    }

    @Test
    public void missingFieldsStayEmptyInsteadOfInvented() {
        List<TrendingParser.Repo> repos = TrendingParser.parse(
                "<article class='Box-row'><h2><a href='/o/r'>o / r</a></h2></article>");
        TrendingParser.Repo r = repos.get(0);
        assertNull(r.description);
        assertNull(r.language);
        assertEquals(0, r.stars);
        assertEquals(0, r.forks);
        assertEquals(-1, r.starsInPeriod);
    }

    @Test
    public void parsesAbbreviatedPeriodStars() {
        List<TrendingParser.Repo> repos = TrendingParser.parse("<article class='Box-row'><h2><a href='/o/r'>x</a></h2>"
                + "<span>1.2k stars this week</span></article>");
        assertEquals(1200, repos.get(0).starsInPeriod);
    }

    @Test
    public void nonTrendingHtmlYieldsNoRepos() {
        assertTrue(TrendingParser.parse("<html><body>Please accept cookies</body></html>").isEmpty());
        assertTrue(TrendingParser.parse("").isEmpty());
    }
}
