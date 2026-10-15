package app.bytebrief.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AffinityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(Affinity affinity);

    @Query("SELECT * FROM affinities WHERE kind = :kind AND `key` = :key")
    Affinity find(String kind, String key);

    @Query("SELECT * FROM affinities")
    List<Affinity> getAll();

    @Query("SELECT * FROM affinities WHERE explicit = 1 AND kind = 'topic' ORDER BY `key`")
    LiveData<List<Affinity>> observeExplicitTopics();

    @Query("DELETE FROM affinities WHERE `key` = :key AND kind = :kind AND explicit = 1")
    void deleteExplicit(String kind, String key);

    /** Reset personalization: drops learned weights but keeps interests the user chose explicitly. */
    @Query("DELETE FROM affinities WHERE explicit = 0")
    void deleteLearned();

    @Query("DELETE FROM affinities")
    void deleteAll();
}
