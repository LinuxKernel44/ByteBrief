package app.bytebrief.ai;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import app.bytebrief.ai.model.BriefingCandidate;
import app.bytebrief.ai.model.DailyBriefing;
import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.Briefing;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.data.db.ContentListItem;
import app.bytebrief.domain.ContentHasher;
import app.bytebrief.domain.HtmlText;
import app.bytebrief.domain.SourceType;
import app.bytebrief.ranking.Personalizer;
import app.bytebrief.ranking.RankedItem;
import app.bytebrief.ranking.Ranker;
import app.bytebrief.ranking.RankingContext;

/**
 * Daily briefing. Which items appear is decided deterministically by the local ranker; the model only
 * writes a one-line reason for each. Without a model the briefing is still produced, with the ranker's
 * own reasons. Results are cached by date, content-set version and model.
 */
public final class BriefingService {
    static final int MAX_ITEMS = 10;
    static final int MAX_PER_SOURCE = 4;
    private static final long WINDOW_MS = TimeUnit.HOURS.toMillis(48);
    private static final long RELEASE_WINDOW_MS = TimeUnit.DAYS.toMillis(7);
    private static final int SNIPPET_CHARS = 300;

    public static final class Row {
        public final ContentItem item;
        public final int groupSize;
        /** The model's reason this matters, or null if none was generated. */
        @Nullable
        public final String why;
        /** The ranker's own reasons, always available. */
        public final List<String> rankerReasons;

        Row(ContentItem item, int groupSize, @Nullable String why, List<String> rankerReasons) {
            this.item = item;
            this.groupSize = groupSize;
            this.why = why;
            this.rankerReasons = rankerReasons;
        }
    }

    public static final class View {
        public final String dateKey;
        @Nullable
        public final String headline;
        public final List<Row> rows;
        public final List<DailyBriefing.Theme> themes;
        /** True when the model wrote the headline and reasons. */
        public final boolean aiGenerated;
        public final String note;
        public final long generatedAt;

        View(String dateKey, @Nullable String headline, List<Row> rows, List<DailyBriefing.Theme> themes, boolean aiGenerated,
             String note, long generatedAt) {
            this.dateKey = dateKey;
            this.headline = headline;
            this.rows = rows;
            this.themes = themes;
            this.aiGenerated = aiGenerated;
            this.note = note;
            this.generatedAt = generatedAt;
        }
    }

    private final AppDatabase db;
    private final Personalizer personalizer;
    private final LocalAiEngine engine;
    private final ExecutorService io;
    private final Clock clock;
    private final ZoneId zone;

    public BriefingService(AppDatabase db, Personalizer personalizer, LocalAiEngine engine, ExecutorService io, Clock clock, ZoneId zone) {
        this.db = db;
        this.personalizer = personalizer;
        this.engine = engine;
        this.io = io;
        this.clock = clock;
        this.zone = zone;
    }

    /** Deterministic selection: same stored content and preferences always give the same list. */
    public List<RankedItem> select() {
        long now = clock.millis();
        List<ContentListItem> pool = new ArrayList<>();
        for (ContentListItem c : db.contentDao().candidates(now - RELEASE_WINDOW_MS, 500)) {
            ContentItem i = c.item;
            if (i.isRead || i.feedback < 0) continue;
            long window = SourceType.GITHUB_RELEASE.code.equals(i.sourceType) ? RELEASE_WINDOW_MS : WINDOW_MS;
            if (now - i.publishedAt > window) continue;
            pool.add(c);
        }
        RankingContext ctx = personalizer.buildContext();
        List<RankedItem> ranked = Ranker.rank(pool, ctx, pool.size());
        List<RankedItem> picked = new ArrayList<>();
        Map<String, Integer> perSource = new HashMap<>();
        for (RankedItem r : ranked) {
            String key = Ranker.sourceKey(r.entry.item);
            if (perSource.getOrDefault(key, 0) >= MAX_PER_SOURCE) continue;
            perSource.merge(key, 1, Integer::sum);
            picked.add(r);
            if (picked.size() >= MAX_ITEMS) break;
        }
        return picked;
    }

    public String dateKey() {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(clock.millis()).atZone(zone));
    }

    /** Version of the selected content: changes when an item is added, removed or its text changes. */
    static String contentSetVersion(List<RankedItem> selection) {
        List<String> parts = new ArrayList<>();
        for (RankedItem r : selection) parts.add(r.entry.item.id + ":" + r.entry.item.contentHash);
        Collections.sort(parts);
        return ContentHasher.sha256Hex(String.join("|", parts)).substring(0, 32);
    }

    public AiCall<View> generate(boolean force, AiProgressListener listener) {
        AiCall<View> call = new AiCall<>();
        io.execute(() -> {
            try {
                List<RankedItem> selection = select();
                String date = dateKey();
                if (selection.isEmpty()) {
                    call.complete(done(AiResult.failure(AiResult.Status.INSUFFICIENT_CONTENT,
                            "Nothing new to brief you on yet. Pull to refresh, or check your sources.")));
                    return;
                }
                ModelDescriptor active = engine.activeModel();
                String modelId = active == null ? "none" : active.id;
                String version = contentSetVersion(selection);

                if (!force && active != null) {
                    Briefing stored = db.briefingDao().find(date, version, modelId);
                    if (stored != null) {
                        try {
                            DailyBriefing b = DailyBriefing.fromJson(new JSONObject(stored.json));
                            call.complete(fromCache(AiResult.success(compose(date, selection, b, "", stored.createdAt)), modelId));
                            return;
                        } catch (JSONException e) {
                            // fall through and regenerate
                        }
                    }
                }
                if (!engine.isReady()) {
                    call.complete(done(AiResult.success(compose(date, selection, null,
                            "Load a language model to add a short \"why it matters\" to each item.", clock.millis()))));
                    return;
                }
                List<BriefingCandidate> candidates = new ArrayList<>();
                int n = 1;
                for (RankedItem r : selection) {
                    ContentItem i = r.entry.item;
                    String text = i.description != null ? i.description : i.bodyText != null ? i.bodyText : "";
                    candidates.add(new BriefingCandidate(n++, i.id, i.title, i.sourceLabel != null ? i.sourceLabel : i.sourceType,
                            HtmlText.truncate(text, SNIPPET_CHARS), i.topics == null ? "" : i.topics));
                }
                AiTask<DailyBriefing> task = engine.generateDailyBriefing(candidates, listener);
                call.setOnCancel(task::cancel);
                AiResult<DailyBriefing> r = task.future().get();
                if (!r.isSuccess()) {
                    // Keep the briefing useful: deterministic selection plus a clear note on why the AI part is missing.
                    if (r.status == AiResult.Status.CANCELLED) {
                        call.complete(done(r.asFailure()));
                    } else {
                        call.complete(done(AiResult.success(compose(date, selection, null,
                                "The model could not write the reasons (" + r.status.name().toLowerCase().replace('_', ' ') + "). Showing the ranked items.",
                                clock.millis()))));
                    }
                    return;
                }
                Briefing row = new Briefing();
                row.dateKey = date;
                row.contentSetVersion = version;
                row.modelId = modelId;
                row.json = r.value.toJson().toString();
                row.createdAt = clock.millis();
                db.briefingDao().upsert(row);
                call.complete(done(AiResult.success(compose(date, selection, r.value, "", row.createdAt))));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                call.complete(done(AiResult.failure(AiResult.Status.CANCELLED, "Interrupted")));
            } catch (Exception e) {
                call.complete(done(AiResult.failure(AiResult.Status.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage())));
            }
        });
        return call;
    }

    /** Rows always follow the deterministic selection order; the model only contributes text. */
    static View compose(String date, List<RankedItem> selection, @Nullable DailyBriefing ai, String note, long generatedAt) {
        Map<Long, String> why = new HashMap<>();
        if (ai != null) for (DailyBriefing.Entry e : ai.entries) why.put(e.itemId, e.whyItMatters);
        List<Row> rows = new ArrayList<>();
        for (RankedItem r : selection) {
            List<String> reasons = new ArrayList<>();
            for (RankedItem.Reason reason : r.reasons) if (reason.contribution != 0 && reasons.size() < 3) reasons.add(reason.label);
            rows.add(new Row(r.entry.item, r.entry.groupSize, why.get(r.entry.item.id), reasons));
        }
        return new View(date, ai == null ? null : ai.headline, rows, ai == null ? Collections.emptyList() : ai.themes, ai != null, note, generatedAt);
    }

    private static AiCall.Outcome<View> done(AiResult<View> r) {
        return new AiCall.Outcome<>(r, false, false, null);
    }

    private static AiCall.Outcome<View> fromCache(AiResult<View> r, String modelId) {
        return new AiCall.Outcome<>(r, true, false, modelId);
    }
}
