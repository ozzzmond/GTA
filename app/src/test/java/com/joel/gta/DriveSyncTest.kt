package com.joel.gta

import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.joel.gta.data.local.GtaDatabase
import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.sync.RoomSyncStore
import com.joel.gta.data.sync.SyncPayload
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DriveSyncTest {
    private fun web(content: String = "[G]Cloud") = SyncPayload.parse("""{
      "app":"GTAR","exportType":"FULL_BACKUP","songs":[
      {"id":"web-uuid","title":"Shared song","artist":"Artist","rawContent":"$content","bpm":"98","isDeleted":true}],
      "setlists":[{"id":"gig-uuid","name":"Gig","songs":[{"id":"web-uuid","title":"Shared song","artist":"Artist"}]}]}
    """)
    @Test fun webRoomRoundTripPreservesIdsTrashTempoAndMembership() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GtaDatabase::class.java).build()
        try {
            val store = RoomSyncStore(db)
            val incoming = web()
            val restored = store.apply(store.snapshot(), incoming)
            assertTrue(SyncPayload.sameLibrary(incoming, restored))
            java.io.File("build/reports/sync-roundtrip.json").apply { parentFile.mkdirs(); writeText(restored.toString()) }
            assertTrue(db.songDao().getAllSongsDirect().single().isDeleted)
            val firstId = db.songDao().getAllSongsDirect().single().id
            store.apply(restored, web("Edited"))
            assertEquals(firstId, db.songDao().getAllSongsDirect().single().id)
            assertEquals("Edited", db.songDao().getAllSongsDirect().single().rawContent)
        } finally { db.close() }
    }
    @Test fun rejectInvalidReferencesAndPreserveConcurrentLocalEdits() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GtaDatabase::class.java).build()
        try {
            val store = RoomSyncStore(db)
            val before = store.snapshot()
            db.songDao().insertSong(SongEntity(title = "Local", rawContent = "Unsynced"))
            try { store.apply(before, web()); fail("Must not replace a changed library") } catch (_: IllegalStateException) { }
            assertEquals("Unsynced", db.songDao().getAllSongsDirect().single().rawContent)
            val broken = web().also { it.getJSONArray("setlists").getJSONObject(0).getJSONArray("songs").getJSONObject(0).put("id", "missing") }
            try { SyncPayload.parse(broken.toString()); fail("Missing reference must fail") } catch (_: IllegalArgumentException) { }
        } finally { db.close() }
    }
    @Test fun mergePreservesLocalEditsAndRejectsConflictingRemoteEdits() {
        val base = web()
        val local = web("Local")
        assertTrue(SyncPayload.sameLibrary(local, SyncPayload.merge(local, base, base)))
        try { SyncPayload.merge(local, web("Remote"), base); fail("Conflict must fail") } catch (_: IllegalStateException) { }
        val alias = JSONObject("""{"songs":[{"id":42,"title":"Alias","content":"Text"}],"setlists":[{"id":7,"name":"Gig","songIds":[42]}]}""")
        val parsed = SyncPayload.parse(alias.toString())
        assertEquals("42", parsed.getJSONArray("songs").getJSONObject(0).getString("id"))
        assertEquals("42", parsed.getJSONArray("setlists").getJSONObject(0).getJSONArray("songs").getJSONObject(0).getString("id"))
    }
    @Test fun migrationPreservesRowsAndAssignsDistinctStableIds() {
        val context = RuntimeEnvironment.getApplication()
        val name = "sync-migration-test.db"
        context.deleteDatabase(name)
        fun helper(version: Int) = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE songs (id INTEGER PRIMARY KEY, title TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE setlists (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                        db.execSQL("INSERT INTO songs VALUES (1, 'Original'), (2, 'Other')")
                        db.execSQL("INSERT INTO setlists VALUES (1, 'Gig')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, old: Int, new: Int) { GtaDatabase.MIGRATION_6_7.migrate(db) }
                }).build())
        helper(6).also { it.writableDatabase; it.close() }
        val upgraded = helper(7)
        try {
            upgraded.writableDatabase.query("SELECT title,syncId,bpm FROM songs ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("Original", cursor.getString(0))
                val first = cursor.getString(1); assertTrue(first.isNotBlank()); assertEquals("120", cursor.getString(2))
                assertTrue(cursor.moveToNext()); assertNotEquals(first, cursor.getString(1))
            }
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }

}
