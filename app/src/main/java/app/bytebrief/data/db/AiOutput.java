package app.bytebrief.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A validated model output for one item. Valid only while {@link #contentHash} equals the item's
 * current hash and {@link #modelId} equals the active model.
 */
@Entity(
        tableName = "ai_outputs",
        foreignKeys = @ForeignKey(entity = ContentItem.class, parentColumns = "id", childColumns = "item_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"item_id", "kind"}, unique = true)})
public class AiOutput {
    public static final String KIND_SUMMARY = "summary";
    public static final String KIND_TAKEAWAYS = "takeaways";
    public static final String KIND_EXPLAIN_PREFIX = "explain_";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "item_id")
    public long itemId;

    @NonNull
    public String kind = "";

    /** Validated JSON produced by the model. */
    @NonNull
    public String json = "";

    @NonNull
    @ColumnInfo(name = "content_hash")
    public String contentHash = "";

    @NonNull
    @ColumnInfo(name = "model_id")
    public String modelId = "";

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
