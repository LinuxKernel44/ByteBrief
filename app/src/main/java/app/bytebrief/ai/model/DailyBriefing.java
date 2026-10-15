package app.bytebrief.ai.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A briefing: the deterministic selection, each with the model's one-line reason it matters. */
public final class DailyBriefing {
    public static final class Entry {
        public final long itemId;
        /** Empty if the model gave no usable reason for this item; the UI then shows none rather than inventing one. */
        public final String whyItMatters;

        public Entry(long itemId, String whyItMatters) {
            this.itemId = itemId;
            this.whyItMatters = whyItMatters;
        }
    }

    public static final class Theme {
        public final String name;
        public final List<Long> itemIds;

        public Theme(String name, List<Long> itemIds) {
            this.name = name;
            this.itemIds = itemIds;
        }
    }

    public final String headline;
    public final List<Entry> entries;
    public final List<Theme> themes;

    public DailyBriefing(String headline, List<Entry> entries, List<Theme> themes) {
        this.headline = headline;
        this.entries = entries;
        this.themes = themes;
    }

    public JSONObject toJson() throws JSONException {
        JSONArray es = new JSONArray();
        for (Entry e : entries) es.put(new JSONObject().put("item_id", e.itemId).put("why", e.whyItMatters));
        JSONArray ts = new JSONArray();
        for (Theme t : themes) ts.put(new JSONObject().put("name", t.name).put("item_ids", new JSONArray(t.itemIds)));
        return new JSONObject().put("headline", headline).put("entries", es).put("themes", ts);
    }

    public static DailyBriefing fromJson(JSONObject o) {
        List<Entry> es = new ArrayList<>();
        JSONArray ea = o.optJSONArray("entries");
        if (ea != null) for (int i = 0; i < ea.length(); i++) {
            JSONObject e = ea.optJSONObject(i);
            if (e != null) es.add(new Entry(e.optLong("item_id"), e.optString("why")));
        }
        List<Theme> ts = new ArrayList<>();
        JSONArray ta = o.optJSONArray("themes");
        if (ta != null) for (int i = 0; i < ta.length(); i++) {
            JSONObject t = ta.optJSONObject(i);
            if (t == null) continue;
            List<Long> ids = new ArrayList<>();
            JSONArray ia = t.optJSONArray("item_ids");
            if (ia != null) for (int j = 0; j < ia.length(); j++) ids.add(ia.optLong(j));
            ts.add(new Theme(t.optString("name"), ids));
        }
        return new DailyBriefing(o.optString("headline"), es, ts);
    }
}
