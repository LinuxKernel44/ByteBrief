package app.bytebrief.ai;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Models ByteBrief knows how to download. Sizes and SHA-256 values were taken from the publishers'
 * Hugging Face file metadata; downloads are rejected if the hash does not match.
 */
public final class ModelCatalog {
    public static final String DEFAULT_ID = "qwen3-4b-instruct-2507-q4_0";
    public static final String LIGHT_ID = "qwen2.5-1.5b-instruct-q4_0";

    private static final List<ModelDescriptor> MODELS = Collections.unmodifiableList(Arrays.asList(
            new ModelDescriptor(
                    DEFAULT_ID, "Qwen3 4B Instruct (2507)", "Qwen Team (Alibaba Cloud), GGUF by Unsloth",
                    "4.0B", "Q4_0", "Qwen3-4B-Instruct-2507-Q4_0.gguf", 2_375_773_280L,
                    "e0ba675d86ab277c61701c6793659b2ae801d95e3be791464c321e6fbf613be2",
                    "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_0.gguf",
                    "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0",
                    "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF", 4096, 8192,
                    "Recommended for phones with 8 GB of RAM or more. Best quality of the built-in models; "
                            + "slower than the light model. Non-thinking instruct variant."),
            new ModelDescriptor(
                    LIGHT_ID, "Qwen2.5 1.5B Instruct", "Qwen Team (Alibaba Cloud)",
                    "1.5B", "Q4_0", "qwen2.5-1.5b-instruct-q4_0.gguf", 1_066_227_232L,
                    "dcd819ff094852c38faba6873d8ff0c9d51eadb2844539e52042ae5d647bbfdb",
                    "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_0.gguf",
                    "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0",
                    "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF", 4096, 4096,
                    "Fast and small; works on most recent phones. Summaries are shorter and less nuanced.")));

    private ModelCatalog() {}

    public static List<ModelDescriptor> all() {
        return MODELS;
    }

    @Nullable
    public static ModelDescriptor find(@Nullable String id) {
        if (id == null) return null;
        for (ModelDescriptor m : MODELS) if (m.id.equals(id)) return m;
        return null;
    }

    public static ModelDescriptor defaultModel() {
        return MODELS.get(0);
    }
}
