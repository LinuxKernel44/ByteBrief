package app.bytebrief.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FeedDao {
    /** @return the new id, or -1 if the URL already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(Feed feed);

    @Update
    void update(Feed feed);

    @Query("DELETE FROM feeds WHERE id = :id")
    void delete(long id);

    @Query("DELETE FROM content_items WHERE feed_id = :feedId AND is_bookmarked = 0")
    void deleteUnbookmarkedItems(long feedId);

    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE, url")
    LiveData<List<Feed>> observeAll();

    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE, url")
    List<Feed> getAll();

    @Query("SELECT * FROM feeds WHERE enabled = 1")
    List<Feed> getEnabled();

    @Query("SELECT * FROM feeds WHERE id = :id")
    Feed getById(long id);

    @Query("SELECT * FROM feeds WHERE url = :url LIMIT 1")
    Feed getByUrl(String url);
}
