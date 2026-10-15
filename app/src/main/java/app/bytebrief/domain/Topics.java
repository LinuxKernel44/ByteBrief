package app.bytebrief.domain;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Normalizes topic labels so storage, matching and ranking agree on one form. */
public final class Topics {
    private static final int MAX_TOPICS = 12;

    private Topics() {}

    public static String normalizeOne(@Nullable String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s,]+", "-").replaceAll("^-+|-+$", "");
    }

    /** @return comma-joined, de-duplicated, order-preserving topics; null when empty. */
    @Nullable
    public static String join(@Nullable Collection<String> raw) {
        if (raw == null) return null;
        Set<String> out = new LinkedHashSet<>();
        for (String t : raw) {
            String n = normalizeOne(t);
            if (!n.isEmpty() && n.length() <= 40) out.add(n);
            if (out.size() >= MAX_TOPICS) break;
        }
        return out.isEmpty() ? null : String.join(",", out);
    }

    public static String[] split(@Nullable String joined) {
        if (joined == null || joined.isEmpty()) return new String[0];
        return joined.split(",");
    }
}
