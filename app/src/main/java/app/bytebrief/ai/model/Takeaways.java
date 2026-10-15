package app.bytebrief.ai.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Takeaways {
    public final List<String> keyPoints;
    public final String mainContribution;
    public final List<String> technologies;
    public final String audience;
    public final List<String> useCases;
    public final List<String> caveats;
    public final boolean truncatedInput;

    public Takeaways(List<String> keyPoints, String mainContribution, List<String> technologies, String audience,
                     List<String> useCases, List<String> caveats, boolean truncatedInput) {
        this.keyPoints = keyPoints;
        this.mainContribution = mainContribution;
        this.technologies = technologies;
        this.audience = audience;
        this.useCases = useCases;
        this.caveats = caveats;
        this.truncatedInput = truncatedInput;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("key_points", new JSONArray(keyPoints)).put("main_contribution", mainContribution)
                .put("technologies", new JSONArray(technologies)).put("audience", audience)
                .put("use_cases", new JSONArray(useCases)).put("caveats", new JSONArray(caveats))
                .put("truncated_input", truncatedInput);
    }

    public static Takeaways fromJson(JSONObject o) {
        return new Takeaways(list(o.optJSONArray("key_points")), o.optString("main_contribution"),
                list(o.optJSONArray("technologies")), o.optString("audience"), list(o.optJSONArray("use_cases")),
                list(o.optJSONArray("caveats")), o.optBoolean("truncated_input"));
    }

    static List<String> list(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }
}
