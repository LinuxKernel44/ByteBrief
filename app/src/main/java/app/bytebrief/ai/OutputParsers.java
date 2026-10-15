package app.bytebrief.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.bytebrief.ai.model.Answer;
import app.bytebrief.ai.model.BriefingCandidate;
import app.bytebrief.ai.model.DailyBriefing;
import app.bytebrief.ai.model.ExplainLevel;
import app.bytebrief.ai.model.Explanation;
import app.bytebrief.ai.model.Summary;
import app.bytebrief.ai.model.Takeaways;

/**
 * Strict validation of model output. The grammar guarantees the JSON shape; these checks enforce the
 * content rules (non-empty, bounded, grounded) and clean the strings. A thrown
 * {@link ValidationException} message is shown to the model on retry, so it must be specific.
 */
final class OutputParsers {
    private OutputParsers() {}

    /** Result of a parse: the value, or an "insufficient text" verdict from the model. */
    static final class Parsed<T> {
        final T value;
        final boolean insufficient;

        private Parsed(T value, boolean insufficient) {
            this.value = value;
            this.insufficient = insufficient;
        }

        static <T> Parsed<T> of(T v) { return new Parsed<>(v, false); }
        static <T> Parsed<T> insufficient() { return new Parsed<>(null, true); }
    }

    static Parsed<Summary> summary(JSONObject o, boolean truncated, boolean chunked) throws ValidationException, JSONException {
        if (!o.getBoolean("sufficient")) return Parsed.insufficient();
        String summary = TextClean.clean(o.getString("summary"));
        String interpretation = TextClean.clean(o.optString("interpretation", ""));
        if (summary.length() < 40) throw new ValidationException("\"summary\" is empty or too short; write 3 to 6 sentences");
        int sentences = countSentences(summary);
        if (sentences > 8) throw new ValidationException("\"summary\" has " + sentences + " sentences; use at most 6");
        // Small models often paste the summary into the interpretation field; an echo adds nothing, so it is dropped.
        if (restates(summary, interpretation)) interpretation = "";
        return Parsed.of(new Summary(summary, interpretation, truncated, chunked));
    }

    static Parsed<Takeaways> takeaways(JSONObject o, boolean truncated) throws ValidationException, JSONException {
        if (!o.getBoolean("sufficient")) return Parsed.insufficient();
        List<String> points = cleanList(o.getJSONArray("key_points"));
        String main = TextClean.clean(o.getString("main_contribution"));
        if (points.isEmpty() && main.isEmpty()) throw new ValidationException("no key points or main contribution were given");
        return Parsed.of(new Takeaways(points, main, cleanList(o.getJSONArray("technologies")),
                TextClean.clean(o.getString("audience")), cleanList(o.getJSONArray("use_cases")),
                cleanList(o.getJSONArray("caveats")), truncated));
    }

    static Parsed<Explanation> explanation(JSONObject o, ExplainLevel level, boolean truncated) throws ValidationException, JSONException {
        if (!o.getBoolean("sufficient")) return Parsed.insufficient();
        String text = TextClean.clean(o.getString("explanation"));
        if (text.length() < 60) throw new ValidationException("\"explanation\" is empty or too short");
        return Parsed.of(new Explanation(level, text, cleanList(o.getJSONArray("inferred")), truncated));
    }

    static Answer answer(JSONObject o, String sourceText, boolean truncated) throws ValidationException, JSONException {
        boolean answerable = o.getBoolean("answerable");
        String answer = TextClean.clean(o.getString("answer"));
        String evidence = TextClean.clean(o.getString("evidence"));
        if (answer.isEmpty()) throw new ValidationException("\"answer\" is empty");
        if (!answerable) return new Answer(false, answer, "", false, truncated);
        if (evidence.isEmpty()) throw new ValidationException("\"evidence\" must quote the document when \"answerable\" is true");
        boolean grounded = TextClean.fold(sourceText).contains(TextClean.fold(evidence));
        return new Answer(true, answer, evidence, grounded, truncated);
    }

    static DailyBriefing briefing(JSONObject o, List<BriefingCandidate> offered) throws ValidationException, JSONException {
        java.util.Map<Integer, Long> idByNumber = new java.util.HashMap<>();
        for (BriefingCandidate c : offered) idByNumber.put(c.number, c.itemId);

        String headline = TextClean.clean(o.getString("headline"));
        if (headline.isEmpty()) throw new ValidationException("\"headline\" is empty");

        List<DailyBriefing.Entry> entries = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        JSONArray ea = o.getJSONArray("entries");
        for (int i = 0; i < ea.length(); i++) {
            JSONObject e = ea.getJSONObject(i);
            int number = e.getInt("id");
            Long itemId = idByNumber.get(number);
            if (itemId == null || !seen.add(number)) continue; // unknown or repeated numbers are dropped
            String why = TextClean.clean(e.getString("why"));
            if (!why.isEmpty()) entries.add(new DailyBriefing.Entry(itemId, why));
        }
        if (entries.isEmpty()) throw new ValidationException("no entry used a valid item number from the list (1 to " + offered.size() + ")");

        List<DailyBriefing.Theme> themes = new ArrayList<>();
        JSONArray ta = o.getJSONArray("themes");
        for (int i = 0; i < ta.length(); i++) {
            JSONObject t = ta.getJSONObject(i);
            List<Long> ids = new ArrayList<>();
            JSONArray ia = t.getJSONArray("ids");
            for (int j = 0; j < ia.length(); j++) {
                Long id = idByNumber.get(ia.getInt(j));
                if (id != null && !ids.contains(id)) ids.add(id);
            }
            String name = TextClean.clean(t.getString("name"));
            if (!ids.isEmpty() && !name.isEmpty()) themes.add(new DailyBriefing.Theme(name, ids));
        }
        return new DailyBriefing(headline, entries, themes);
    }

    private static List<String> cleanList(JSONArray a) throws JSONException {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            String s = TextClean.clean(a.getString(i));
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    /** True when {@code candidate} is (nearly) the same text as {@code reference}: at least 60% shared words. */
    static boolean restates(String reference, String candidate) {
        if (candidate.isEmpty()) return false;
        java.util.Set<String> ref = new HashSet<>(java.util.Arrays.asList(TextClean.fold(reference).split(" ")));
        java.util.Set<String> cand = new HashSet<>(java.util.Arrays.asList(TextClean.fold(candidate).split(" ")));
        if (cand.isEmpty()) return true;
        int shared = 0;
        for (String w : cand) if (ref.contains(w)) shared++;
        return shared >= cand.size() * 0.6;
    }

    static int countSentences(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && (i + 1 == s.length() || Character.isWhitespace(s.charAt(i + 1)))) n++;
        }
        return Math.max(n, s.isEmpty() ? 0 : 1);
    }
}
