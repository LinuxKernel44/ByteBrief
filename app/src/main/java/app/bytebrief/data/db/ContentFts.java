package app.bytebrief.data.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Fts4;

/** FTS4 index over {@link ContentItem}. FTS4 is used because Room supports it on every Android SQLite build. */
@Fts4(contentEntity = ContentItem.class, tokenizer = "unicode61")
@Entity(tableName = "content_fts")
public class ContentFts {
    public String title;
    public String description;
    @ColumnInfo(name = "body_text")
    public String bodyText;
    public String author;
    public String topics;
}
