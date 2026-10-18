package app.bytebrief.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ForumDao {
    /** @return the new id, or -1 if the URL already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(Forum forum);

    @Update
    void update(Forum forum);

    @Query("DELETE FROM forums WHERE id = :id")
    void delete(long id);

    @Query("DELETE FROM content_items WHERE source_type = 'xda' AND feed_id = :forumId AND is_bookmarked = 0")
    void deleteUnbookmarkedThreads(long forumId);

    @Query("SELECT * FROM forums ORDER BY title COLLATE NOCASE, url")
    LiveData<List<Forum>> observeAll();

    @Query("SELECT * FROM forums ORDER BY title COLLATE NOCASE, url")
    List<Forum> getAll();

    @Query("SELECT * FROM forums WHERE enabled = 1")
    List<Forum> getEnabled();

    @Query("SELECT * FROM forums WHERE id = :id")
    Forum getById(long id);

    @Query("SELECT * FROM forums WHERE url = :url LIMIT 1")
    Forum getByUrl(String url);

    @Query("UPDATE forums SET needs_verification = 0, last_error = NULL, consecutive_failures = 0 WHERE needs_verification = 1")
    void clearVerificationFlags();
}
