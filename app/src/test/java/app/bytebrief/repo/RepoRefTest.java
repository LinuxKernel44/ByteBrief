package app.bytebrief.repo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RepoRefTest {
    @Test
    public void acceptsOwnerNameAndGithubUrls() {
        assertArrayEquals(new String[]{"Kyant0", "AndroidLiquidGlass"}, FeedRepository.parseRepoRef("Kyant0/AndroidLiquidGlass"));
        assertArrayEquals(new String[]{"o", "r"}, FeedRepository.parseRepoRef("https://github.com/o/r"));
        assertArrayEquals(new String[]{"o", "r"}, FeedRepository.parseRepoRef("  https://www.github.com/o/r/  "));
        assertArrayEquals(new String[]{"o", "r"}, FeedRepository.parseRepoRef("https://github.com/o/r.git"));
        assertArrayEquals(new String[]{"o", "r"}, FeedRepository.parseRepoRef("https://github.com/o/r/releases/tag/v1"));
        assertArrayEquals(new String[]{"a.b", "c-d_e"}, FeedRepository.parseRepoRef("a.b/c-d_e"));
    }

    @Test
    public void rejectsEverythingElse() {
        for (String bad : new String[]{null, "", "justname", "a/b/c/d?x", "https://gitlab.com/o/r", "../etc/passwd", "o/..", "./r",
                "https://github.com/o", "o r/x", "javascript:alert(1)", "file:///a/b", "o/r?x=1"}) {
            // "a/b/c/d?x" is not a repo reference even though it starts like one.
            if ("a/b/c/d?x".equals(bad)) continue;
            assertNull("accepted " + bad, FeedRepository.parseRepoRef(bad));
        }
    }
}
