package app.bytebrief.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A GitHub repository whose releases the user follows. */
@Entity(tableName = "tracked_repos", indices = {@Index(value = {"owner", "name"}, unique = true)})
public class TrackedRepo {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String owner = "";

    @NonNull
    public String name = "";

    public boolean enabled = true;

    @Nullable
    public String etag;

    @ColumnInfo(name = "last_checked_at")
    public long lastCheckedAt;

    @Nullable
    @ColumnInfo(name = "last_error")
    public String lastError;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
