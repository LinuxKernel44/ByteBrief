package app.bytebrief.data.db;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

/** A representative item plus how many sources reference the same subject. */
public class ContentListItem {
    @Embedded
    public ContentItem item;

    @ColumnInfo(name = "group_size")
    public int groupSize;
}
