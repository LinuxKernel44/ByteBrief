package app.bytebrief.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                ContentItem.class, ContentFts.class, Feed.class, TrackedRepo.class,
                AiOutput.class, Briefing.class, Affinity.class
        },
        version = 1,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public static final String NAME = "bytebrief.db";

    private static volatile AppDatabase instance;

    public abstract ContentDao contentDao();

    public abstract FeedDao feedDao();

    public abstract TrackedRepoDao trackedRepoDao();

    public abstract AiOutputDao aiOutputDao();

    public abstract BriefingDao briefingDao();

    public abstract AffinityDao affinityDao();

    public static AppDatabase get(Context context) {
        AppDatabase local = instance;
        if (local == null) {
            synchronized (AppDatabase.class) {
                local = instance;
                if (local == null) {
                    // No destructive fallback: a missing migration must fail loudly, not wipe user data.
                    local = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, NAME).build();
                    instance = local;
                }
            }
        }
        return local;
    }
}
