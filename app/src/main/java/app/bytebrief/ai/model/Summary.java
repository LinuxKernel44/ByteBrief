package app.bytebrief.ai.model;

import org.json.JSONException;
import org.json.JSONObject;

/** A short factual summary. {@code interpretation} holds anything beyond what the text states. */
public final class Summary {
    public final String summary;
    public final String interpretation;
    /** The input was cut to fit the model's context window. */
    public final boolean truncatedInput;
    /** The input was long and went through a condensing pass first. */
    public final boolean chunked;

    public Summary(String summary, String interpretation, boolean truncatedInput, boolean chunked) {
        this.summary = summary;
        this.interpretation = interpretation;
        this.truncatedInput = truncatedInput;
        this.chunked = chunked;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("summary", summary).put("interpretation", interpretation)
                .put("truncated_input", truncatedInput).put("chunked", chunked);
    }

    public static Summary fromJson(JSONObject o) {
        return new Summary(o.optString("summary"), o.optString("interpretation"),
                o.optBoolean("truncated_input"), o.optBoolean("chunked"));
    }
}
