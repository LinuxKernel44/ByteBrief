package app.bytebrief.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A followed XDA Forums section (a device forum or a category). */
@Entity(tableName = "forums", indices = {@Index(value = {"url"}, unique = true)})
public class Forum {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Canonical https address of the forum, ending in a slash. */
    @NonNull
    public String url = "";

    @Nullable
    public String title;

    public boolean enabled = true;

    /** Post a notification when a new thread appears. */
    @ColumnInfo(name = "notify_new_threads", defaultValue = "1")
    public boolean notifyNewThreads = true;

    @ColumnInfo(name = "last_fetched_at")
    public long lastFetchedAt;

    /** Null when the last refresh succeeded. */
    @Nullable
    @ColumnInfo(name = "last_error")
    public String lastError;

    /** The site is waiting for the user to complete a human-verification step. */
    @ColumnInfo(name = "needs_verification")
    public boolean needsVerification;

    @ColumnInfo(name = "consecutive_failures")
    public int consecutiveFailures;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
