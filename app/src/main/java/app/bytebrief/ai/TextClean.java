package app.bytebrief.ai;

import java.util.regex.Pattern;

/** Normalization applied to every string that comes back from the model. */
final class TextClean {
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?|ftp|file|content|javascript):[^\\s)\\]>\"']+|\\bwww\\.[^\\s)\\]>\"']+");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");

    private TextClean() {}

    /** Removes control characters and links (the app shows source links itself) and collapses whitespace. */
    static String clean(String s) {
        String t = CONTROL.matcher(s).replaceAll(" ");
        t = URL.matcher(t).replaceAll("");
        return t.replaceAll("[ \\t]+", " ").replaceAll("\\s*\\n\\s*", " ").replaceAll("\\s{2,}", " ").trim();
    }

    /** Lower-case, alphanumeric-only form used to check that quoted evidence exists in the source. */
    static String fold(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
