package app.bytebrief.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                ContentItem.class, ContentFts.class, Feed.class, TrackedRepo.class,
                AiOutput.class, Briefing.class, Affinity.class, Forum.class
        },
        version = 2,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public static final String NAME = "bytebrief.db";

    /** Adds the table of followed XDA forums. */
    public static final androidx.room.migration.Migration MIGRATION_1_2 = new androidx.room.migration.Migration(1, 2) {
        @Override
        public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `forums` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, "
                    + "`title` TEXT, `enabled` INTEGER NOT NULL, `notify_new_threads` INTEGER NOT NULL DEFAULT 1, "
                    + "`last_fetched_at` INTEGER NOT NULL, `last_error` TEXT, `needs_verification` INTEGER NOT NULL, "
                    + "`consecutive_failures` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_forums_url` ON `forums` (`url`)");
        }
    };

    private static volatile AppDatabase instance;

    public abstract ContentDao contentDao();

    public abstract FeedDao feedDao();

    public abstract TrackedRepoDao trackedRepoDao();

    public abstract AiOutputDao aiOutputDao();

    public abstract BriefingDao briefingDao();

    public abstract AffinityDao affinityDao();

    public abstract ForumDao forumDao();

    public static AppDatabase get(Context context) {
        AppDatabase local = instance;
        if (local == null) {
            synchronized (AppDatabase.class) {
                local = instance;
                if (local == null) {
                    // No destructive fallback: a missing migration must fail loudly, not wipe user data.
                    local = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, NAME)
                            .addMigrations(MIGRATION_1_2).build();
                    instance = local;
                }
            }
        }
        return local;
    }
}
