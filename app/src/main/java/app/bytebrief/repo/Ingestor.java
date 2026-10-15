package app.bytebrief.repo;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.ContentDao;
import app.bytebrief.data.db.ContentItem;
import app.bytebrief.domain.AiStatus;
import app.bytebrief.domain.ItemFactory;

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
        if (existing.bodyState > incoming.bodyState) {
            incoming.bodyText = existing.bodyText;
            incoming.bodyState = existing.bodyState;
        }
        if (incoming.description == null) incoming.description = existing.description;
        if (incoming.topics == null) incoming.topics = existing.topics;
        if (incoming.imageUrl == null) incoming.imageUrl = existing.imageUrl;
        if (incoming.language == null) incoming.language = existing.language;
        if (incoming.feedId == null) incoming.feedId = existing.feedId;
        ItemFactory.refreshHash(incoming);
        // Generated output describes the old text; only keep its status while the text is unchanged.
        incoming.aiStatus = incoming.contentHash.equals(existing.contentHash) ? existing.aiStatus : AiStatus.NONE;
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
