package app.bytebrief.llama;

/**
 * Thin JNI surface over llama.cpp. All handles are opaque native pointers and must be released
 * in reverse order of creation: session, then model. Methods are not thread-safe per handle;
 * {@link #cancel(long)} is the only call that may be made from another thread while
 * {@link #generate} is running.
 */
public final class LlamaNative {
    /** Receives generated text as complete UTF-8 byte sequences. */
    public interface PieceListener {
        /** @return false to stop generation early. */
        boolean onPiece(byte[] utf8);
    }

    static {
        System.loadLibrary("bytebrief_llama");
    }

    private LlamaNative() {}

    /** Initializes the backend and loads CPU variants from {@code nativeLibDir}. Idempotent. */
    public static native void init(String nativeLibDir);

    public static native String systemInfo();

    /** @throws LlamaException with CODE_MODEL_LOAD (or CODE_OUT_OF_MEMORY) on failure. */
    public static native long loadModel(String path, int gpuLayers);

    public static native void freeModel(long model);

    public static native long modelParams(long model);

    public static native long modelSizeBytes(long model);

    public static native int modelContextTrain(long model);

    /** Applies the model's embedded chat template; falls back to ChatML when absent. */
    public static native byte[] formatChat(long model, String[] roles, byte[][] contents, boolean addAssistant);

    /** @throws LlamaException when the context (KV cache) cannot be allocated. */
    public static native long createSession(long model, int contextSize, int threads);

    public static native void freeSession(long session);

    /**
     * Runs one stateless completion: clears the KV cache, decodes {@code prompt}, then samples
     * until EOS, {@code maxTokens}, listener refusal or {@link #cancel}.
     *
     * @return number of generated tokens
     * @throws LlamaException CODE_CONTEXT_OVERFLOW if the prompt plus {@code maxTokens} exceeds the context
     */
    public static native int generate(long session, byte[] prompt, int maxTokens, float temperature,
                                      float topP, int seed, PieceListener listener);

    /** Requests the running {@link #generate} to stop. Safe to call from any thread. */
    public static native void cancel(long session);

    /** @return the number of tokens the prompt occupies, or -1 on error. */
    public static native int countTokens(long model, byte[] text);
}
