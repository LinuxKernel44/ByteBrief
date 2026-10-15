package app.bytebrief.llama;

/** Raised by the native layer. {@link #getCode()} distinguishes recoverable conditions. */
public class LlamaException extends RuntimeException {
    public static final int CODE_GENERIC = 0;
    public static final int CODE_MODEL_LOAD = 1;
    public static final int CODE_OUT_OF_MEMORY = 2;
    public static final int CODE_CONTEXT_OVERFLOW = 3;
    public static final int CODE_DECODE = 4;

    private final int code;

    public LlamaException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
