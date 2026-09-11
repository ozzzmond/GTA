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

    @Test fun independentIdsMergeByIdentityWithoutGrowingOnRepeatedSync() {
        val remote = web()
        val local = web().also {
            it.getJSONArray("songs").getJSONObject(0).put("id", "android-id").put("title", " SHARED SONG ").put("artist", " artist ")
            it.getJSONArray("setlists").getJSONObject(0).put("id", "android-gig").put("name", " GIG ")
                .getJSONArray("songs").getJSONObject(0).put("id", "android-id")
        }
        val merged = SyncPayload.merge(local, remote, null)
        assertEquals(1, merged.getJSONArray("songs").length())
        assertEquals(1, merged.getJSONArray("setlists").length())
        assertEquals("android-id", merged.getJSONArray("songs").getJSONObject(0).getString("id"))
        assertEquals("android-gig", merged.getJSONArray("setlists").getJSONObject(0).getString("id"))
        assertEquals("android-id", merged.getJSONArray("setlists").getJSONObject(0).getJSONArray("songs").getJSONObject(0).getString("id"))
        assertTrue(SyncPayload.sameLibrary(merged, SyncPayload.merge(merged, remote, remote)))
    }
    @Test fun roomCleanupRetainsPrimaryRowsAndRepointsOrderedMemberships() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GtaDatabase::class.java).build()
        try {
            val a = db.songDao().insertSong(SongEntity(syncId = "a", title = "Song", artist = "Artist", rawContent = "Primary"))
            val duplicate = db.songDao().insertSong(SongEntity(syncId = "duplicate", title = " SONG ", artist = " artist ", rawContent = "Duplicate"))
            val b = db.songDao().insertSong(SongEntity(syncId = "b", title = "Other", rawContent = "Other"))
            val first = db.setlistDao().insertSetlist(com.joel.gta.data.local.entity.SetlistEntity(syncId = "first", name = "Gig"))
            val second = db.setlistDao().insertSetlist(com.joel.gta.data.local.entity.SetlistEntity(syncId = "second", name = " GIG "))
            db.setlistDao().addSongToSetlist(com.joel.gta.data.local.entity.SetlistSongCrossRef(first, duplicate, 0))
            db.setlistDao().addSongToSetlist(com.joel.gta.data.local.entity.SetlistSongCrossRef(first, b, 1))
            db.setlistDao().addSongToSetlist(com.joel.gta.data.local.entity.SetlistSongCrossRef(second, a, 0))
            val store = RoomSyncStore(db)
            val repaired = store.cleanup()
            assertEquals(listOf(a, b), db.songDao().getAllSongsDirect().map { it.id }.sorted())
            assertEquals("Primary", db.songDao().getSongById(a)!!.rawContent)
            assertEquals(first, db.setlistDao().getAllSetlistsDirect().single().id)
            assertEquals(listOf(a, b), db.setlistDao().getCrossRefsForSetlist(first).map { it.songId })
            assertTrue(SyncPayload.sameLibrary(repaired, store.cleanup()))
            val remote = SyncPayload.parse(repaired.toString()).also {
                it.getJSONArray("songs").getJSONObject(0).put("id", "foreign")
                it.getJSONArray("setlists").getJSONObject(0).getJSONArray("songs").getJSONObject(0).put("id", "foreign")
            }
            store.apply(repaired, SyncPayload.merge(repaired, remote, null))
            assertEquals(2, db.songDao().getAllSongsDirect().size)
            assertEquals(a, db.songDao().getAllSongsDirect().first { it.syncId == "a" }.id)
        } finally { db.close() }
    }
    @Test fun dedupeKeepsDistinctArtistsAndDropsRepeatedReferences() {
        val payload = web()
        val original = payload.getJSONArray("songs").getJSONObject(0)
        payload.getJSONArray("songs").put(JSONObject(original.toString()).put("id", "other").put("artist", "Different Artist"))
        val setlist = payload.getJSONArray("setlists").getJSONObject(0)
        setlist.getJSONArray("songs").put(JSONObject(setlist.getJSONArray("songs").getJSONObject(0).toString()))
        val repaired = com.joel.gta.data.sync.SyncDedup.deduplicate(SyncPayload.parse(payload.toString()))
        assertEquals(2, repaired.getJSONArray("songs").length())
        assertEquals(1, repaired.getJSONArray("setlists").getJSONObject(0).getJSONArray("songs").length())
    }

}
