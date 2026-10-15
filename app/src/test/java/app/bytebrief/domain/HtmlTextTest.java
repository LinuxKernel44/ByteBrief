package app.bytebrief.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HtmlTextTest {
    @Test
    public void removesScriptsAndKeepsParagraphs() {
        String text = HtmlText.toPlainText("<p>Hello <b>world</b></p><script>alert(1)</script><p>Second</p>");
        assertEquals("Hello world\n\nSecond", text);
    }

    @Test
    public void doesNotLeakAttributesOrStyles() {
        String text = HtmlText.toPlainText("<div onclick=\"x()\" style=\"color:red\">Plain</div><style>p{}</style>");
        assertEquals("Plain", text);
    }

    @Test
    public void nullAndBlankAreEmpty() {
        assertEquals("", HtmlText.toPlainText(null));
        assertEquals("", HtmlText.toPlainText("  "));
    }

    @Test
    public void truncatesOnWordBoundary() {
        String out = HtmlText.truncate("alpha beta gamma delta", 12);
        assertEquals("alpha beta…", out);
        assertFalse(HtmlText.truncate("short", 50).endsWith("…"));
        assertTrue(HtmlText.truncate("x".repeat(100), 10).length() <= 11);
    }
}
