package app.bytebrief.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.junit.Test;

public class TopicsTest {
    @Test
    public void normalizesDedupesAndPreservesOrder() {
        assertEquals("machine-learning,rust", Topics.join(Arrays.asList(" Machine Learning ", "RUST", "rust", "")));
    }

    @Test
    public void commasInTopicsCannotBreakStorageFormat() {
        assertEquals("a-b", Topics.join(Arrays.asList("a,b")));
    }

    @Test
    public void emptyYieldsNull() {
        assertNull(Topics.join(Arrays.asList("", "  ")));
        assertNull(Topics.join(null));
        assertEquals(0, Topics.split(null).length);
    }
}
