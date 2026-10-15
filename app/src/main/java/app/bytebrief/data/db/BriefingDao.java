package app.bytebrief.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface BriefingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(Briefing briefing);

    @Query("SELECT * FROM briefings WHERE date_key = :dateKey AND content_set_version = :version "
            + "AND model_id = :modelId LIMIT 1")
    Briefing find(String dateKey, String version, String modelId);

    @Query("SELECT * FROM briefings ORDER BY created_at DESC LIMIT 1")
    LiveData<Briefing> observeLatest();

    @Query("DELETE FROM briefings WHERE created_at < :before")
    void deleteOlderThan(long before);
}
