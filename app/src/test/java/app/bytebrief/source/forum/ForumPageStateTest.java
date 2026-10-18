package app.bytebrief.source.forum;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import app.bytebrief.source.forum.ForumPageState.State;

public class ForumPageStateTest {
    @Test
    public void aForumPageIsReady() {
        assertEquals(State.READY, ForumPageState.classify(ForumFixtures.forumPage("OnePlus 15", "", null)));
        assertEquals(State.READY, ForumPageState.classify(ForumFixtures.ERROR_PAGE.replace("Not found", "x".repeat(200))));
    }

    @Test
    public void aVerificationPageIsRecognized() {
        assertEquals(State.VERIFICATION, ForumPageState.classify(ForumFixtures.CHALLENGE));
        String bunny = "<html><head><title>Please wait</title></head><body><p>Establishing a secure connection ...</p>" + "<!-- -->".repeat(40) + "</body></html>";
        assertEquals(State.VERIFICATION, ForumPageState.classify(bunny));
    }

    @Test
    public void aPostThatMentionsTheWordsDoesNotTriggerVerification() {
        String html = ForumFixtures.threadPage("Cloudflare says just a moment", null,
                ForumFixtures.post(1, "a", 1, "It showed 'Verifying you are human' then 'just a moment'", ""), null);
        assertEquals(State.READY, ForumPageState.classify(html));
    }

    @Test
    public void emptyOrTinyDocumentsAreStillLoading() {
        assertEquals(State.LOADING, ForumPageState.classify(null));
        assertEquals(State.LOADING, ForumPageState.classify("<html></html>"));
        assertEquals(State.LOADING, ForumPageState.classify("<html><body>" + "a".repeat(300) + "</body></html>"));
    }
}
