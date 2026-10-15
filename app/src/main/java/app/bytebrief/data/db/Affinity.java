package app.bytebrief.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/** A learned or user-chosen preference weight for a topic or a source. All personalization stays on-device. */
@Entity(tableName = "affinities", primaryKeys = {"kind", "key"})
public class Affinity {
    public static final String KIND_TOPIC = "topic";
    public static final String KIND_SOURCE = "source";

    @NonNull
    public String kind = "";

    @NonNull
    public String key = "";

    public double weight;

    /** True when the user chose this interest explicitly (never decayed or reset by learning). */
    @ColumnInfo(name = "explicit")
    public boolean explicit;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}
