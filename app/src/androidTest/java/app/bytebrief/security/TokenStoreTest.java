package app.bytebrief.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TokenStoreTest {
    private TokenStore store;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        store = new TokenStore(context);
        store.clear();
    }

    @After
    public void tearDown() {
        store.clear();
    }

    @Test
    public void roundTripsAndNeverStoresPlaintext() throws Exception {
        assertFalse(store.hasToken());
        store.set("ghp_ExampleToken_123-abc");
        assertTrue(store.hasToken());
        assertEquals("ghp_ExampleToken_123-abc", store.get());
        String raw = context.getSharedPreferences("bytebrief_secure", Context.MODE_PRIVATE).getString("github_token", "");
        assertFalse("plaintext leaked into preferences", raw.contains("ExampleToken"));
        assertTrue(raw.contains(":"));
    }

    @Test
    public void clearRemovesTheTokenAndKey() throws Exception {
        store.set("ghp_abc");
        store.clear();
        assertNull(store.get());
        assertFalse(store.hasToken());
    }

    @Test
    public void rejectsMalformedTokens() throws Exception {
        for (String bad : new String[]{"", "   ", null, "has space", "semi;colon", "new\nline", "x".repeat(600)}) {
            try {
                store.set(bad);
                fail("accepted: " + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        assertFalse(store.hasToken());
    }

    @Test
    public void tamperedCiphertextIsDiscardedNotTrusted() throws Exception {
        store.set("ghp_abc");
        android.content.SharedPreferences p = context.getSharedPreferences("bytebrief_secure", Context.MODE_PRIVATE);
        String raw = p.getString("github_token", "");
        String[] parts = raw.split(":");
        byte[] data = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP);
        data[0] ^= 1;
        p.edit().putString("github_token", parts[0] + ":" + android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)).commit();
        assertNull("GCM authentication must reject modified data", store.get());
        assertFalse("a bad entry is forgotten", store.hasToken());
    }

    @Test
    public void garbageStorageDoesNotCrash() {
        context.getSharedPreferences("bytebrief_secure", Context.MODE_PRIVATE).edit().putString("github_token", "not-base64").commit();
        assertNull(store.get());
    }

    @Test
    public void tokenIsTrimmed() throws Exception {
        store.set("  ghp_abc  ");
        assertEquals("ghp_abc", store.get());
    }
}
