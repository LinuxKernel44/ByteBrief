package app.bytebrief.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import app.bytebrief.domain.AiStatus;
import app.bytebrief.domain.BodyState;

/**
 * One item from one source. Items from different sources that point at the same subject share a
 * {@code dedupKey}; they remain separate rows so source-specific data is never lost.
 */
@Entity(
        tableName = "content_items",
        indices = {
                @Index(value = {"source_type", "source_id"}, unique = true),
                @Index(value = {"uid"}, unique = true),
                @Index("dedup_key"),
                @Index("published_at"),
                @Index("is_bookmarked"),
                @Index("is_read"),
                @Index("feed_id")
        })
public class ContentItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Stable internal identifier derived from source type and source id. */
    @NonNull
    public String uid = "";

    @NonNull
    @ColumnInfo(name = "source_type")
    public String sourceType = "";

    @NonNull
    @ColumnInfo(name = "source_id")
    public String sourceId = "";

    /** Owning RSS feed, if any. */
    @Nullable
    @ColumnInfo(name = "feed_id")
    public Long feedId;

    /** Human-readable origin, e.g. the feed title. */
    @Nullable
    @ColumnInfo(name = "source_label")
    public String sourceLabel;

    @NonNull
    public String title = "";

    @NonNull
    @ColumnInfo(name = "canonical_url")
    public String canonicalUrl = "";

    @NonNull
    @ColumnInfo(name = "original_url")
    public String originalUrl = "";

    @Nullable
    public String author;

    @ColumnInfo(name = "published_at")
    public long publishedAt;

    @ColumnInfo(name = "retrieved_at")
    public long retrievedAt;

    @Nullable
    public String description;

    /** Extracted article text, README, release notes or discussion text. */
    @Nullable
    @ColumnInfo(name = "body_text")
    public String bodyText;

    @ColumnInfo(name = "body_state")
    public int bodyState = BodyState.NONE;

    @Nullable
    public String language;

    /** Lower-case topics joined by commas. */
    @Nullable
    public String topics;

    @Nullable
    @ColumnInfo(name = "image_url")
    public String imageUrl;

    @ColumnInfo(name = "is_read")
    public boolean isRead;

    @ColumnInfo(name = "is_bookmarked")
    public boolean isBookmarked;

    /** -1 negative, 0 none, 1 positive. */
    public int feedback;

    @NonNull
    @ColumnInfo(name = "content_hash")
    public String contentHash = "";

    @NonNull
    @ColumnInfo(name = "dedup_key")
    public String dedupKey = "";

    @ColumnInfo(name = "ai_status")
    public int aiStatus = AiStatus.NONE;

    /** HN points or GitHub stars; meaning depends on {@link #sourceType}. */
    public long score;

    @ColumnInfo(name = "comment_count")
    public int commentCount;

    /** Source-specific fields (forks, language, tag, prerelease, ...) as a JSON object. */
    @Nullable
    @ColumnInfo(name = "metadata_json")
    public String metadataJson;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}
