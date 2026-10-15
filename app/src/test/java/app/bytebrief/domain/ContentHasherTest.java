package app.bytebrief.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class ContentHasherTest {
    @Test
    public void whitespaceDifferencesDoNotChangeHash() {
        assertEquals(ContentHasher.contentHash("A  title", "body\n text"),
                ContentHasher.contentHash(" A title ", "body text"));
    }

    @Test
    public void contentChangesChangeHash() {
        assertNotEquals(ContentHasher.contentHash("t", "one"), ContentHasher.contentHash("t", "two"));
    }

    @Test
    public void titleAndTextBoundaryIsNotAmbiguous() {
        assertNotEquals(ContentHasher.contentHash("ab", "c"), ContentHasher.contentHash("a", "bc"));
    }

    @Test
    public void uidIsStableAndSourceScoped() {
        assertEquals(ContentHasher.uid("hn", "1"), ContentHasher.uid("hn", "1"));
        assertNotEquals(ContentHasher.uid("hn", "1"), ContentHasher.uid("rss", "1"));
        assertEquals(32, ContentHasher.uid("hn", "1").length());
    }

    @Test
    public void knownSha256Vector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ContentHasher.sha256Hex("abc"));
    }
}
