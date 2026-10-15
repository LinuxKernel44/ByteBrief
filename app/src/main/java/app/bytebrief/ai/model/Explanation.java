package app.bytebrief.ai.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public final class Explanation {
    public final ExplainLevel level;
    public final String explanation;
    /** Statements the model says go beyond the provided text (its own background knowledge or inference). */
    public final List<String> inferred;
    public final boolean truncatedInput;

    public Explanation(ExplainLevel level, String explanation, List<String> inferred, boolean truncatedInput) {
        this.level = level;
        this.explanation = explanation;
        this.inferred = inferred;
        this.truncatedInput = truncatedInput;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("level", level.code).put("explanation", explanation)
                .put("inferred", new JSONArray(inferred)).put("truncated_input", truncatedInput);
    }

    public static Explanation fromJson(JSONObject o) {
        return new Explanation(ExplainLevel.fromCode(o.optString("level", "intermediate")), o.optString("explanation"),
                Takeaways.list(o.optJSONArray("inferred")), o.optBoolean("truncated_input"));
    }
}
