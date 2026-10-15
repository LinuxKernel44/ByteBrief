package app.bytebrief.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AiOutputDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(AiOutput output);

    @Query("SELECT * FROM ai_outputs WHERE item_id = :itemId AND kind = :kind LIMIT 1")
    AiOutput find(long itemId, String kind);

    @Query("SELECT * FROM ai_outputs WHERE item_id = :itemId")
    LiveData<List<AiOutput>> observeForItem(long itemId);

    @Query("DELETE FROM ai_outputs")
    void deleteAll();
}
