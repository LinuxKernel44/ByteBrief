package app.bytebrief.source.forum;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Tells a real forum page apart from a human-verification interstitial. Both arrive with a 200 or 403
 * and a lot of markup, so the decision is made from what the document actually contains.
 */
public final class ForumPageState {
    public enum State { READY, VERIFICATION, LOADING }

    private static final String[] CHALLENGE_MARKERS = {
            "establishing a secure connection", "verifying you are human", "performing security verification",
            "just a moment", "checking your browser", "checking if the site connection is secure",
            "cf-challenge", "challenge-platform", "cf-turnstile", "cf_chl_opt", "enable javascript and cookies to continue",
    };

    private ForumPageState() {}

    public static State classify(@Nullable String html) {
        if (html == null || html.length() < 200) return State.LOADING;
        String lower = html.toLowerCase(Locale.ROOT);
        // A forum page can mention these words in a post, so only trust markers when no forum chrome is present.
        if (hasForumChrome(lower)) return State.READY;
        for (String m : CHALLENGE_MARKERS) {
            if (lower.contains(m)) return State.VERIFICATION;
        }
        return State.LOADING;
    }

    /** True for the "XF" page skeleton XenForo renders on every public page. */
    static boolean hasForumChrome(String lowerHtml) {
        return lowerHtml.contains("data-app=\"public\"") || lowerHtml.contains("class=\"p-body")
                || lowerHtml.contains("p-body-inner") || lowerHtml.contains("data-template=\"");
    }
}
