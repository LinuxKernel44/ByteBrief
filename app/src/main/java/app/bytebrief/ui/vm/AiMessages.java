package app.bytebrief.ui.vm;

import androidx.annotation.Nullable;

import app.bytebrief.ai.AiResult;

/** User-facing wording for each AI failure status. */
public final class AiMessages {
    private AiMessages() {}

    public static String describe(AiResult.Status status, @Nullable String detail) {
        switch (status) {
            case MODEL_UNAVAILABLE:
                return "No language model is ready. Open Models to download and load one. Reading, search and saved summaries work without it.";
            case UNSUPPORTED:
                return "This model or device can't do that.";
            case MALFORMED_OUTPUT:
                return "The model's answer could not be validated, so nothing was saved. Try again, or use a larger model.";
            case OUT_OF_MEMORY:
                return "Not enough memory for this model. Close other apps or choose the light model.";
            case INSUFFICIENT_CONTENT:
                return detail != null && !detail.isEmpty() ? detail : "Not enough text is stored to do this reliably.";
            case CANCELLED:
                return "Cancelled.";
            case NOT_CACHED:
                return "Nothing generated yet.";
            default:
                return detail != null && !detail.isEmpty() ? "Something went wrong: " + detail : "Something went wrong.";
        }
    }
}
