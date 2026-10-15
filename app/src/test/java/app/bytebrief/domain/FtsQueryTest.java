package app.bytebrief.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FtsQueryTest {
    @Test
    public void buildsQuotedPrefixTerms() {
        // Case is preserved; the unicode61 tokenizer matches case-insensitively.
        assertEquals("\"Rust*\" \"async*\"", FtsQuery.build("Rust  async"));
    }

    @Test
    public void neutralizesFtsOperatorsAndQuotes() {
        assertEquals("\"a*\" \"OR*\" \"b*\"", FtsQuery.build("a OR b"));
        assertEquals("\"x*\" \"NEAR*\" \"3*\" \"y*\"", FtsQuery.build("x NEAR/3 y"));
        // Punctuation, including quotes and semicolons, is dropped; underscores split terms.
        assertEquals("\"DROP*\" \"TABLE*\" \"content*\" \"items*\"",
                FtsQuery.build("\"; DROP TABLE content_items; --"));
    }

    @Test
    public void emptyOrSymbolOnlyInputYieldsNull() {
        assertNull(FtsQuery.build(null));
        assertNull(FtsQuery.build("   "));
        assertNull(FtsQuery.build("*-\"()"));
    }

    @Test
    public void supportsUnicodeLetters() {
        assertEquals("\"café*\" \"日本語*\"", FtsQuery.build("café 日本語"));
    }

    @Test
    public void capsTermCount() {
        String q = FtsQuery.build("a b c d e f g h i j k l");
        assertEquals(8, q.split(" ").length);
        assertTrue(q.endsWith("\"h*\""));
    }
}
