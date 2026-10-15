package app.bytebrief.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TrackedRepoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(TrackedRepo repo);

    @Update
    void update(TrackedRepo repo);

    @Query("DELETE FROM tracked_repos WHERE id = :id")
    void delete(long id);

    @Query("SELECT * FROM tracked_repos ORDER BY owner COLLATE NOCASE, name COLLATE NOCASE")
    LiveData<List<TrackedRepo>> observeAll();

    @Query("SELECT * FROM tracked_repos WHERE enabled = 1")
    List<TrackedRepo> getEnabled();
}
