package app.bytebrief.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import app.bytebrief.data.db.AppDatabase;
import app.bytebrief.data.db.Forum;

/**
 * Builds a version 1 database from the exported schema, then opens it with the current code so Room
 * runs {@link AppDatabase#MIGRATION_1_2} and validates the result against the version 2 schema.
 */
public class MigrationTest {
    private static String asset(String name) throws Exception {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void createVersion1(File file) throws Exception {
        JSONObject schema = new JSONObject(asset(AppDatabase.class.getName() + "/1.json")).getJSONObject("database");
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            JSONArray entities = schema.getJSONArray("entities");
            for (int i = 0; i < entities.length(); i++) {
                JSONObject e = entities.getJSONObject(i);
                String table = e.getString("tableName");
                db.execSQL(e.getString("createSql").replace("${TABLE_NAME}", table));
                JSONArray indices = e.optJSONArray("indices");
                for (int j = 0; indices != null && j < indices.length(); j++) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table));
                }
                JSONArray triggers = e.optJSONArray("contentSyncTriggers");
                for (int j = 0; triggers != null && j < triggers.length(); j++) db.execSQL(triggers.getString(j));
            }
            JSONArray setup = schema.getJSONArray("setupQueries");
            for (int i = 0; i < setup.length(); i++) db.execSQL(setup.getString(i));
            db.execSQL("INSERT INTO feeds (url, enabled, last_fetched_at, consecutive_failures, created_at) VALUES ('https://example.com/feed', 1, 0, 0, 1)");
            db.setVersion(1);
        } finally {
            db.close();
        }
    }

    @Test
    public void version1To2KeepsDataAndAddsForums() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "migration-test.db";
        context.deleteDatabase(name);
        createVersion1(context.getDatabasePath(name));

        AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, name).addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build();
        try {
            assertEquals("https://example.com/feed", db.feedDao().getAll().get(0).url);
            Forum f = new Forum();
            f.url = "https://xdaforums.com/f/a.1/";
            assertTrue(db.forumDao().insertIgnore(f) > 0);
            Forum stored = db.forumDao().getByUrl(f.url);
            assertTrue("alerts default to on", stored.notifyNewThreads);
            try (Cursor c = db.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM forums")) {
                c.moveToFirst();
                assertEquals(1, c.getInt(0));
            }
        } finally {
            db.close();
            context.deleteDatabase(name);
        }
    }
}
