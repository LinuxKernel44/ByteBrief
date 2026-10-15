package app.bytebrief.content;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.SourceType;

/** Renders the facts a source reported about an item, so the model can cite them and the UI can show them. */
public final class MetadataFormatter {
    private MetadataFormatter() {}

    public static String format(ContentItem item) {
        StringBuilder sb = new StringBuilder();
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        JSONObject meta = parse(item.metadataJson);
        switch (SourceType.fromCode(item.sourceType)) {
            case HACKER_NEWS:
                line(sb, "Hacker News points: " + nf.format(item.score));
                line(sb, "Hacker News comments: " + nf.format(item.commentCount));
                break;
            case GITHUB_TRENDING:
                line(sb, "GitHub stars (total, as listed by GitHub): " + nf.format(item.score));
                if (meta.has("stars_in_period")) {
                    line(sb, "Stars gained " + periodLabel(meta.optString("period")) + " (GitHub Trending): " + nf.format(meta.optLong("stars_in_period")));
                }
                if (meta.has("forks")) line(sb, "Forks: " + nf.format(meta.optLong("forks")));
                if (meta.has("programming_language")) line(sb, "Programming language: " + meta.optString("programming_language"));
                if (meta.has("license")) line(sb, "License: " + meta.optString("license"));
                if (meta.has("pushed_at")) line(sb, "Last pushed: " + meta.optString("pushed_at"));
                if (meta.optBoolean("archived")) line(sb, "The repository is archived.");
                break;
            case GITHUB_RELEASE:
                if (meta.has("tag")) line(sb, "Release tag: " + meta.optString("tag"));
                line(sb, "Pre-release: " + (meta.optBoolean("prerelease") ? "yes" : "no"));
                break;
            default:
                break;
        }
        if (item.author != null) line(sb, "Author: " + item.author);
        return sb.toString().trim();
    }

    private static String periodLabel(String period) {
        switch (period) {
            case "weekly": return "this week";
            case "monthly": return "this month";
            default: return "today";
        }
    }

    private static void line(StringBuilder sb, String s) {
        sb.append(s).append('\n');
    }

    private static JSONObject parse(String json) {
        try {
            return json == null ? new JSONObject() : new JSONObject(json);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
