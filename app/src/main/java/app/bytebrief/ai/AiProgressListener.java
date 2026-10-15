package app.bytebrief.ai;

/** Receives progress of one AI operation. Called on the engine thread; post to the UI thread before touching views. */
public interface AiProgressListener {
    /** A new phase began, e.g. "Reading part 2 of 3". */
    void onStage(String description);

    /** Raw streamed model output (a growing JSON fragment); useful only for a "working" indicator. */
    void onText(String piece);

    AiProgressListener NONE = new AiProgressListener() {
        @Override public void onStage(String description) { }
        @Override public void onText(String piece) { }
    };
}
