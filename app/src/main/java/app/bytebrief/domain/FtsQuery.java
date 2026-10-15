package app.bytebrief.domain;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Turns free-form user input into a safe FTS4 MATCH expression (prefix match, implicit AND). */
public final class FtsQuery {
    private static final int MAX_TERMS = 8;

    private FtsQuery() {}

    /** @return the MATCH expression, or null when the input has no searchable term. */
    @Nullable
    public static String build(@Nullable String input) {
        if (input == null) return null;
        List<String> terms = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) {
                cur.appendCodePoint(cp);
            } else if (cur.length() > 0) {
                terms.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) terms.add(cur.toString());
        if (terms.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(terms.size(), MAX_TERMS); i++) {
            if (i > 0) sb.append(' ');
            // Only letters and digits survive, so quoting cannot be broken out of; operators become literals.
            sb.append('"').append(terms.get(i)).append("\"*");
        }
        return sb.toString();
    }
}
