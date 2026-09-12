package com.joel.gta

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.joel.gta.data.local.GtaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PopulatedMigrationTest {
    private val migrations = arrayOf(GtaDatabase.MIGRATION_2_3, GtaDatabase.MIGRATION_3_4, GtaDatabase.MIGRATION_4_5, GtaDatabase.MIGRATION_5_6, GtaDatabase.MIGRATION_6_7, GtaDatabase.MIGRATION_7_8)
    private fun createV2(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE songs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, artist TEXT, `key` TEXT, capo TEXT, rawContent TEXT NOT NULL, format TEXT NOT NULL, isFavorite INTEGER NOT NULL, transposeOffset INTEGER NOT NULL, createdAt INTEGER NOT NULL, lastOpenedAt INTEGER NOT NULL)")
        for (field in listOf("title", "isFavorite", "createdAt", "lastOpenedAt")) db.execSQL("CREATE INDEX index_songs_$field ON songs($field)")
        db.execSQL("CREATE TABLE setlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE setlist_songs (setlistId INTEGER NOT NULL, songId INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(setlistId,songId), FOREIGN KEY(setlistId) REFERENCES setlists(id) ON DELETE CASCADE, FOREIGN KEY(songId) REFERENCES songs(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_setlist_songs_setlistId ON setlist_songs(setlistId)")
        db.execSQL("CREATE INDEX index_setlist_songs_songId ON setlist_songs(songId)")
        db.execSQL("INSERT INTO songs VALUES(1,'First','Artist','G','2','First chart','CHORD_PRO',1,2,123,456),(2,'Second',NULL,'A',NULL,'Second chart','TWO_LINE',0,-1,124,457)")
        db.execSQL("INSERT INTO setlists VALUES(1,'Gig',789)")
        db.execSQL("INSERT INTO setlist_songs VALUES(1,2,0),(1,1,1)")
    }
    @Test fun migrateEverySupportedInstalledVersionWithDataAndReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        for (version in 2..8) {
            val name = "populated-$version.db"
            context.deleteDatabase(name)
            val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createV2(db)
                        migrations.filter { it.endVersion <= version }.forEach { it.migrate(db) }
                        if (version >= 3) db.execSQL("UPDATE songs SET tags='set A' WHERE id=1")
                        if (version >= 4) db.execSQL("UPDATE songs SET isDeleted=1 WHERE id=2")
                        if (version >= 7) db.execSQL("UPDATE songs SET bpm='98',syncId='stable-one' WHERE id=1")
                        if (version >= 8) db.execSQL("UPDATE setlists SET isDeleted=0 WHERE id=1")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
                }).build())
            helper.writableDatabase; helper.close()
            fun open() = Room.databaseBuilder(context, GtaDatabase::class.java, name).addMigrations(*migrations).build()
            var db = open()
            try {
                val songs = db.songDao().getAllSongsDirect().sortedBy { it.id }
                assertEquals(listOf("First chart", "Second chart"), songs.map { it.rawContent })
                assertEquals(listOf(2L, 1L), db.setlistDao().getCrossRefsForSetlist(1).map { it.songId })
                assertEquals(listOf(0, 1), db.setlistDao().getCrossRefsForSetlist(1).map { it.position })
                assertEquals(version >= 4, songs[1].isDeleted)
                assertEquals(if (version >= 7) "98" else "120", songs[0].bpm)
                assertEquals(2, songs.map { it.syncId }.toSet().size)
                assertTrue(songs.all { it.syncId.isNotBlank() })
                if (version >= 7) assertEquals("stable-one", songs[0].syncId)
                if (version >= 3) assertEquals("set A", songs[0].tags)
                assertEquals(789L, db.setlistDao().getAllSetlistsDirect().single().createdAt)
                assertFalse(db.setlistDao().getAllSetlistsDirect().single().isDeleted)
                db.close(); db = open()
                assertEquals(songs, db.songDao().getAllSongsDirect().sortedBy { it.id })
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }
    @Test fun unsupportedVersionFailsWithoutDeletingRows() {
        val context = RuntimeEnvironment.getApplication()
        val name = "unsupported-version.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = createV2(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("No migration")
            }).build())
        helper.writableDatabase; helper.close()
        val room = Room.databaseBuilder(context, GtaDatabase::class.java, name).addMigrations(*migrations).build()
        try { room.openHelper.writableDatabase; fail("Unsupported version must fail") } catch (_: IllegalStateException) { } finally { room.close() }
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        raw.rawQuery("SELECT rawContent FROM songs WHERE id=1", null).use { assertTrue(it.moveToFirst()); assertEquals("First chart", it.getString(0)) }
        raw.close(); context.deleteDatabase(name)
    }
}
