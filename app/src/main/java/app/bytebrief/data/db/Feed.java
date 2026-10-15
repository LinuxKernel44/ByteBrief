package app.bytebrief.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A user-configured RSS or Atom feed. */
@Entity(tableName = "feeds", indices = {@Index(value = {"url"}, unique = true)})
public class Feed {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Normalized https feed URL. */
    @NonNull
    public String url = "";

    @Nullable
    public String title;

    @Nullable
    @ColumnInfo(name = "site_url")
    public String siteUrl;

    public boolean enabled = true;

    @ColumnInfo(name = "last_fetched_at")
    public long lastFetchedAt;

    /** Null when the last refresh succeeded. */
    @Nullable
    @ColumnInfo(name = "last_error")
    public String lastError;

    @ColumnInfo(name = "consecutive_failures")
    public int consecutiveFailures;

    /** HTTP validators for conditional requests. */
    @Nullable
    public String etag;

    @Nullable
    @ColumnInfo(name = "last_modified")
    public String lastModified;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
