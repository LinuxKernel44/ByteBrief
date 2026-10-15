package app.bytebrief.repo;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentDao;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.AiStatus;
import app.bytebrief.domain.ItemFactory;
import app.bytebrief.domain.Topics;

/**
 * Writes collected items to the database. Ingestion is idempotent: an item is identified by
 * (source type, source id), so repeated syncs update rows instead of duplicating them, and user
 * state (read, bookmark, feedback) and richer cached text are never overwritten by a re-fetch.
 */
public class Ingestor {
    public static final class Stats {
        public int inserted;
        public int updated;
        public int unchanged;

        public void add(Stats other) {
            inserted += other.inserted;
            updated += other.updated;
            unchanged += other.unchanged;
        }

        @Override
        public String toString() {
            return inserted + " new, " + updated + " updated, " + unchanged + " unchanged";
        }
    }

    private final AppDatabase db;
    private final Clock clock;

    public Ingestor(AppDatabase db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public Stats ingest(List<ContentItem> items) {
        Stats stats = new Stats();
        db.runInTransaction(() -> {
            ContentDao dao = db.contentDao();
            for (ContentItem incoming : items) {
                ContentItem existing = dao.findBySource(incoming.sourceType, incoming.sourceId);
                if (existing == null) {
                    if (dao.insertIgnore(incoming) != -1) stats.inserted++;
                    continue;
                }
                merge(existing, incoming);
                if (sameContent(existing, incoming)) {
                    stats.unchanged++;
                } else {
                    incoming.updatedAt = clock.millis();
                    dao.update(incoming);
                    stats.updated++;
                }
            }
        });
        return stats;
    }

    /** Carries user state and richer cached data from {@code existing} into {@code incoming}. */
    static void merge(ContentItem existing, ContentItem incoming) {
        incoming.id = existing.id;
        incoming.uid = existing.uid;
        incoming.isRead = existing.isRead;
        incoming.isBookmarked = existing.isBookmarked;
        incoming.feedback = existing.feedback;
        incoming.retrievedAt = existing.retrievedAt;
        incoming.updatedAt = existing.updatedAt;
        // Sources without dates fall back to "now"; keeping the earliest date stops items bouncing upward.
        incoming.publishedAt = Math.min(existing.publishedAt, incoming.publishedAt);
        // Never replace stored text with something poorer or absent (e.g. a README with a bare trending row).
        if (existing.bodyState > incoming.bodyState || (incoming.bodyText == null && existing.bodyText != null)) {
            incoming.bodyText = existing.bodyText;
            incoming.bodyState = existing.bodyState;
        }
        if (incoming.description == null) incoming.description = existing.description;
        incoming.topics = unionTopics(existing.topics, incoming.topics);
        incoming.metadataJson = mergeJson(existing.metadataJson, incoming.metadataJson);
        if (incoming.imageUrl == null) incoming.imageUrl = existing.imageUrl;
        if (incoming.language == null) incoming.language = existing.language;
        if (incoming.feedId == null) incoming.feedId = existing.feedId;
        ItemFactory.refreshHash(incoming);
        // Generated output describes the old text; only keep its status while the text is unchanged.
        incoming.aiStatus = incoming.contentHash.equals(existing.contentHash) ? existing.aiStatus : AiStatus.NONE;
    }

    /** Existing topics first, then new ones; keeps enrichment results when a later fetch is less detailed. */
    static String unionTopics(String existing, String incoming) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;
        java.util.List<String> all = new java.util.ArrayList<>(java.util.Arrays.asList(Topics.split(existing)));
        all.addAll(java.util.Arrays.asList(Topics.split(incoming)));
        return Topics.join(all);
    }

    /** Key-wise union of two JSON objects; values from {@code incoming} win. Returns {@code incoming} if either is unreadable. */
    static String mergeJson(String existing, String incoming) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;
        try {
            JSONObject merged = new JSONObject(existing);
            JSONObject in = new JSONObject(incoming);
            for (java.util.Iterator<String> it = in.keys(); it.hasNext(); ) {
                String k = it.next();
                merged.put(k, in.get(k));
            }
            return merged.toString();
        } catch (JSONException e) {
            return incoming;
        }
    }

    static boolean sameContent(ContentItem a, ContentItem b) {
        return a.title.equals(b.title)
                && a.canonicalUrl.equals(b.canonicalUrl)
                && a.dedupKey.equals(b.dedupKey)
                && a.contentHash.equals(b.contentHash)
                && a.score == b.score
                && a.commentCount == b.commentCount
                && a.publishedAt == b.publishedAt
                && a.bodyState == b.bodyState
                && a.aiStatus == b.aiStatus
                && Objects.equals(a.author, b.author)
                && Objects.equals(a.topics, b.topics)
                && Objects.equals(a.metadataJson, b.metadataJson)
                && Objects.equals(a.sourceLabel, b.sourceLabel)
                && Objects.equals(a.imageUrl, b.imageUrl)
                && Objects.equals(a.description, b.description)
                && Objects.equals(a.bodyText, b.bodyText);
    }
}
