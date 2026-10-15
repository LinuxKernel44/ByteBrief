package app.bytebrief.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A generated daily briefing, cached by date and the version of the content set it was built from. */
@Entity(tableName = "briefings", indices = {@Index(value = {"date_key", "content_set_version", "model_id"}, unique = true)})
public class Briefing {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Local date, yyyy-MM-dd. */
    @NonNull
    @ColumnInfo(name = "date_key")
    public String dateKey = "";

    @NonNull
    @ColumnInfo(name = "content_set_version")
    public String contentSetVersion = "";

    @NonNull
    @ColumnInfo(name = "model_id")
    public String modelId = "";

    @NonNull
    public String json = "";

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
