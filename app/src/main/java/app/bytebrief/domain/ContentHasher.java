package app.bytebrief.domain;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 helpers for change detection and stable identifiers. */
public final class ContentHasher {
    private ContentHasher() {}

    /** Hash of the text that feeds the model; whitespace differences do not change it. */
    public static String contentHash(@Nullable String title, @Nullable String text) {
        return sha256Hex(normalize(title) + "\u0000" + normalize(text));
    }

    /** Stable internal identifier for an item: first 32 hex chars of sha256(sourceType, sourceId). */
    public static String uid(String sourceType, String sourceId) {
        return sha256Hex(sourceType + "\u0000" + sourceId).substring(0, 32);
    }

    public static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    private static String normalize(@Nullable String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
