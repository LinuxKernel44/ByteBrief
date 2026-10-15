package app.bytebrief.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class ModelCatalogTest {
    @Test
    public void everyModelIsCompleteAndSafe() {
        Set<String> ids = new HashSet<>();
        Set<String> files = new HashSet<>();
        for (ModelDescriptor m : ModelCatalog.all()) {
            assertTrue("unique id " + m.id, ids.add(m.id));
            assertTrue("unique file " + m.fileName, files.add(m.fileName));
            assertTrue(m.sha256, m.sha256.matches("[0-9a-f]{64}"));
            assertTrue(m.sizeBytes > 100_000_000L);
            assertTrue("https only: " + m.downloadUrl, m.downloadUrl.startsWith("https://huggingface.co/"));
            assertTrue(m.fileName.endsWith(".gguf") && !m.fileName.contains("/") && !m.fileName.contains(".."));
            assertTrue(m.licenseUrl.startsWith("https://") && m.sourceUrl.startsWith("https://"));
            assertTrue(m.contextTokens >= 2048);
            assertTrue(!m.license.isEmpty() && !m.publisher.isEmpty() && !m.notes.isEmpty());
        }
    }

    @Test
    public void defaultIsTheRecommendedLargerModelAndLightIsSmaller() {
        assertEquals(ModelCatalog.DEFAULT_ID, ModelCatalog.defaultModel().id);
        assertTrue(ModelCatalog.find(ModelCatalog.LIGHT_ID).sizeBytes < ModelCatalog.defaultModel().sizeBytes);
    }

    @Test
    public void lookupByIdHandlesUnknownAndNull() {
        assertNotNull(ModelCatalog.find(ModelCatalog.LIGHT_ID));
        assertNull(ModelCatalog.find("nope"));
        assertNull(ModelCatalog.find(null));
    }

    @Test
    public void knownHashesMatchTheVerifiedValues() {
        assertEquals("e0ba675d86ab277c61701c6793659b2ae801d95e3be791464c321e6fbf613be2", ModelCatalog.defaultModel().sha256);
        assertEquals(2_375_773_280L, ModelCatalog.defaultModel().sizeBytes);
        assertEquals("dcd819ff094852c38faba6873d8ff0c9d51eadb2844539e52042ae5d647bbfdb", ModelCatalog.find(ModelCatalog.LIGHT_ID).sha256);
    }
}
