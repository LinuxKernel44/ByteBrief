package app.bytebrief.source.github;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.TrackedRepo;
import app.bytebrief.domain.BodyState;
import app.bytebrief.domain.HtmlText;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.SourceType;
import app.bytebrief.net.SourceException;

/** Collects releases of repositories the user chose to follow, using the official GitHub REST API. */
public class ReleaseSource {
    public static final class Result {
        public final List<ContentItem> items = new ArrayList<>();
        public boolean notModified;
        @Nullable
        public String etag;
        public int rateLimitRemaining = -1;
    }

    private final GithubApi api;
    private final ItemFactory factory;

    public ReleaseSource(GithubApi api, ItemFactory factory) {
        this.api = api;
        this.factory = factory;
    }

    public Result fetch(TrackedRepo repo, boolean includePrereleases) throws SourceException {
        GithubApi.ReleasesResult r = api.fetchReleases(repo.owner, repo.name, 10, repo.etag);
        Result result = new Result();
        result.notModified = r.notModified;
        result.etag = r.etag;
        result.rateLimitRemaining = r.rateLimitRemaining;
        for (JSONObject o : r.releases) {
            ContentItem item = map(repo, o, includePrereleases);
            if (item != null) result.items.add(item);
        }
        return result;
    }

    @Nullable
    ContentItem map(TrackedRepo repo, JSONObject o, boolean includePrereleases) {
        if (o.optBoolean("draft")) return null;
        boolean prerelease = o.optBoolean("prerelease");
        if (prerelease && !includePrereleases) return null;

        String tag = o.optString("tag_name", "");
        String name = o.isNull("name") ? "" : o.optString("name", "").trim();
        String label = name.isEmpty() ? tag : name;
        String notes = o.isNull("body") ? "" : o.optString("body", "").trim();
        String full = repo.owner + "/" + repo.name;
        JSONObject author = o.optJSONObject("author");

        List<String> topics = new ArrayList<>(Arrays.asList("release"));
        if (prerelease) topics.add("prerelease");

        ContentItem item = factory.create(SourceType.GITHUB_RELEASE, Long.toString(o.optLong("id", -1)),
                full + " " + label, o.optString("html_url", ""), author == null ? repo.owner : author.optString("login", repo.owner),
                parseTime(o.optString("published_at", o.optString("created_at", ""))),
                notes.isEmpty() ? null : HtmlText.truncate(notes, 500), notes.isEmpty() ? null : notes,
                BodyState.FULL, topics, null);
        if (item == null || o.optLong("id", -1) < 0) return null;
        item.sourceLabel = SourceType.GITHUB_RELEASE.label;
        try {
            JSONObject meta = new JSONObject();
            meta.put("owner", repo.owner);
            meta.put("repo", repo.name);
            meta.put("tag", tag);
            meta.put("prerelease", prerelease);
            item.metadataJson = meta.toString();
        } catch (JSONException ignored) {
            // Only thrown for non-finite numbers.
        }
        return item;
    }

    private static long parseTime(String iso) {
        try {
            return iso.isEmpty() ? 0 : Instant.parse(iso).toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }
}
