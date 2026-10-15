package app.bytebrief.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ContentDao {
    String GROUP_SIZE = "(SELECT COUNT(*) FROM content_items g WHERE g.dedup_key = ci.dedup_key) AS group_size";
    /** Restricts a query to one representative row per dedup group (the first-seen item). */
    String REPRESENTATIVE = "ci.id = (SELECT MIN(r.id) FROM content_items r WHERE r.dedup_key = ci.dedup_key)";
    String FILTERS = "(:source IS NULL OR ci.source_type = :source)"
            + " AND (:language IS NULL OR ci.language = :language)"
            + " AND (:topic IS NULL OR (',' || ci.topics || ',') LIKE '%,' || :topic || ',%')"
            + " AND ci.published_at >= :since"
            + " AND (:readFilter < 0 OR ci.is_read = :readFilter)"
            + " AND (:bookmarkedOnly = 0 OR ci.is_bookmarked = 1)";

    /** @return the new row id, or -1 if (source_type, source_id) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(ContentItem item);

    @Update
    int update(ContentItem item);

    @Query("SELECT * FROM content_items WHERE source_type = :sourceType AND source_id = :sourceId LIMIT 1")
    ContentItem findBySource(String sourceType, String sourceId);

    @Query("SELECT * FROM content_items WHERE id = :id")
    ContentItem getById(long id);

    @Query("SELECT * FROM content_items WHERE id = :id")
    LiveData<ContentItem> observeById(long id);

    @Query("SELECT * FROM content_items WHERE dedup_key = :dedupKey ORDER BY published_at DESC")
    List<ContentItem> getGroup(String dedupKey);

    @Query("SELECT * FROM content_items WHERE dedup_key = :dedupKey ORDER BY published_at DESC")
    LiveData<List<ContentItem>> observeGroup(String dedupKey);

    /** Chronological browsing, one row per dedup group. */
    @Query("SELECT ci.*, " + GROUP_SIZE + " FROM content_items ci WHERE " + REPRESENTATIVE + " AND " + FILTERS
            + " ORDER BY ci.published_at DESC LIMIT :limit")
    LiveData<List<ContentListItem>> observeList(String source, String language, String topic, long since,
                                                int readFilter, int bookmarkedOnly, int limit);

    /** Full-text search. {@code ftsQuery} must come from {@code FtsQuery.build}. */
    @Query("SELECT ci.*, " + GROUP_SIZE + " FROM content_items ci JOIN content_fts ON content_fts.docid = ci.id"
            + " WHERE content_fts MATCH :ftsQuery AND " + FILTERS
            + " ORDER BY ci.published_at DESC LIMIT :limit")
    LiveData<List<ContentListItem>> search(String ftsQuery, String source, String language, String topic, long since,
                                           int readFilter, int bookmarkedOnly, int limit);

    @Query("SELECT ci.*, " + GROUP_SIZE + " FROM content_items ci WHERE " + REPRESENTATIVE
            + " AND ci.published_at >= :since ORDER BY ci.published_at DESC LIMIT :limit")
    List<ContentListItem> candidates(long since, int limit);

    @Query("SELECT DISTINCT language FROM content_items WHERE language IS NOT NULL ORDER BY language")
    LiveData<List<String>> observeLanguages();

    @Query("UPDATE content_items SET is_read = :read, updated_at = :now WHERE id = :id")
    void setRead(long id, boolean read, long now);

    @Query("UPDATE content_items SET is_bookmarked = :bookmarked, updated_at = :now WHERE id = :id")
    void setBookmarked(long id, boolean bookmarked, long now);

    @Query("UPDATE content_items SET feedback = :feedback, updated_at = :now WHERE id = :id")
    void setFeedback(long id, int feedback, long now);

    @Query("UPDATE content_items SET ai_status = :status WHERE id = :id")
    void setAiStatus(long id, int status);

    @Query("UPDATE content_items SET body_text = :body, body_state = :state, content_hash = :hash, "
            + "ai_status = 0, updated_at = :now WHERE id = :id")
    void setBody(long id, String body, int state, String hash, long now);

    /** Retention: drops old items unless the user bookmarked them. */
    @Query("DELETE FROM content_items WHERE published_at < :before AND is_bookmarked = 0")
    int deleteOlderThan(long before);

    @Query("SELECT COUNT(*) FROM content_items")
    int count();
}
