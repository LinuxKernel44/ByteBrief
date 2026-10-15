package app.bytebrief.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the optional GitHub personal access token encrypted with an AES-256-GCM key that lives in
 * the Android Keystore (non-exportable). The token is only used to raise the GitHub API quota and is
 * never required. It is never logged, backed up or shown again after it is saved.
 */
public final class TokenStore {
    private static final String ALIAS = "bytebrief_github_token_key";
    private static final String PREFS = "bytebrief_secure";
    private static final String KEY = "github_token";
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences prefs;

    public TokenStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasToken() {
        return prefs.contains(KEY);
    }

    /** @return the token, or null if none is stored or it can no longer be decrypted. */
    @Nullable
    public String get() {
        String stored = prefs.getString(KEY, null);
        if (stored == null) return null;
        try {
            String[] parts = stored.split(":", 2);
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] data = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(c.doFinal(data), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            // Corrupted data or a key lost after a restore: forget the token rather than fail.
            clear();
            return null;
        }
    }

    public void set(String token) throws GeneralSecurityException {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty() || t.length() > 512 || !t.matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalArgumentException("That does not look like a GitHub token");
        }
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(true));
        byte[] enc = c.doFinal(t.getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString(KEY, Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)).apply();
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignored) {
            // Nothing sensitive remains in preferences either way.
        }
    }

    private SecretKey key(boolean create) throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
            if (!create) throw new GeneralSecurityException("No key");
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
    }
}
