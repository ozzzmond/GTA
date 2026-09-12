package com.joel.gta

import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.joel.gta.data.local.GtaDatabase
import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.repository.SongRepository
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
            java.io.File("build/reports/sync-roundtrip.json").apply { parentFile?.mkdirs(); writeText(restored.toString()) }
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
        val merged = SyncPayload.merge(local, web("Remote"), base)
        assertEquals("Remote", merged.getJSONArray("songs").getJSONObject(0).getString("rawContent"))
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

    @Test fun sharedWebAndroidContracts() {
        val fixtures = org.json.JSONArray(java.io.File("../tests/fixtures/sync-contract.json").readText())
        for (index in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(index)
            for (reverse in listOf(false, true)) {
                val local = SyncPayload.parse(fixture.getJSONObject("local").toString())
                val cloud = SyncPayload.parse(fixture.getJSONObject("cloud").toString())
                if (reverse) cloud.put("songs", org.json.JSONArray(SyncPayload.objects(cloud.getJSONArray("songs")).reversed()))
                if (reverse) cloud.put("setlists", org.json.JSONArray(SyncPayload.objects(cloud.getJSONArray("setlists")).reversed()))
                val base = fixture.optJSONObject("base")?.let { SyncPayload.parse(it.toString()) }
                if (fixture.optBoolean("conflict")) {
                    val merged = SyncPayload.merge(local, cloud, base)
                    assertNotNull(merged)
                } else {
                    val merged = SyncPayload.merge(local, cloud, base)
                    val ids = SyncPayload.objects(merged.getJSONArray("songs")).map(SyncPayload::id).sorted()
                    val expected = fixture.getJSONArray("songIds")
                    assertEquals((0 until expected.length()).map { expected.getString(it) }, ids)
                    if (fixture.has("setlistIds")) {
                        val expectedLists = fixture.getJSONArray("setlistIds")
                        assertEquals((0 until expectedLists.length()).map { expectedLists.getString(it) }, SyncPayload.objects(merged.getJSONArray("setlists")).map(SyncPayload::id).sorted())
                    }
                    if (fixture.has("chart")) assertEquals(fixture.getString("chart"), SyncPayload.objects(merged.getJSONArray("songs")).first { SyncPayload.id(it) == "A" }.getString("rawContent"))
                }
            }
        }
    }
    @Test fun roomCleanupPreservesDistinctStableIdsWithSameNames() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GtaDatabase::class.java).build()
        try {
            db.songDao().insertSong(SongEntity(syncId = "a", title = "Same", rawContent = "First"))
            db.songDao().insertSong(SongEntity(syncId = "b", title = "Same", rawContent = "Second"))
            val result = RoomSyncStore(db).cleanup()
            assertEquals(2, result.getJSONArray("songs").length())
            assertEquals(setOf("First", "Second"), db.songDao().getAllSongsDirect().map { it.rawContent }.toSet())
        } finally { db.close() }
    }
    @Test fun durableJournalPreservesFailedUploadsDeletionsAndInterruptedApply() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("gtar_sync_v1", 0).edit().clear().commit()
        val base = web()
        val journal = com.joel.gta.data.sync.SyncJournal(context, "owner")
        journal.prepare(base, base); journal.acknowledge(); journal.complete()
        for (local in listOf(web("Unsent"), SyncPayload.parse("{\"songs\":[],\"setlists\":[]}"))) {
            val attempt = com.joel.gta.data.sync.SyncJournal(context, "owner")
            attempt.archive(local, base); attempt.prepare(local, local)
            val restart = com.joel.gta.data.sync.SyncJournal(context, "owner")
            assertTrue(SyncPayload.sameLibrary(local, SyncPayload.merge(restart.resume(local), base, restart.baseline())))
        }
        journal.prepare(base, web("Downloaded")); journal.acknowledge()
        val recovered = com.joel.gta.data.sync.SyncJournal(context, "owner")
        assertTrue(SyncPayload.sameLibrary(web("Downloaded"), recovered.resume(base)))
        try { com.joel.gta.data.sync.SyncJournal(context, "other"); fail("Account switch must stop") } catch (_: IllegalStateException) { }
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

    @Test fun preserveAndroidSetlistTrashThroughSyncAndRestore() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GtaDatabase::class.java).build()
        try {
            val store = RoomSyncStore(db)
            val repo = SongRepository(db)

            // Seed active song and two setlists: one active, one to be trashed
            val song1Id = db.songDao().insertSong(SongEntity(syncId = "song-1", title = "First Song", rawContent = "[C]Hello"))
            val song2Id = db.songDao().insertSong(SongEntity(syncId = "song-2", title = "Second Song", rawContent = "[G]World"))

            val activeListId = db.setlistDao().insertSetlist(SetlistEntity(syncId = "setlist-active", name = "Active Gig", isDeleted = false))
            db.setlistDao().addSongToSetlist(SetlistSongCrossRef(activeListId, song1Id, 0))

            val trashListId = db.setlistDao().insertSetlist(SetlistEntity(syncId = "setlist-trash", name = "Trashed Gig", isDeleted = false))
            db.setlistDao().addSongToSetlist(SetlistSongCrossRef(trashListId, song2Id, 0))

            // Soft-delete the second setlist locally
            repo.softDeleteSetlist(trashListId)
            assertTrue(db.setlistDao().getSetlistById(trashListId)!!.isDeleted)
            assertEquals(1, db.setlistDao().getCrossRefsForSetlist(trashListId).size)

            // Verify snapshot only exports active setlists
            val snapshot = store.snapshot()
            val exportedSetlists = snapshot.getJSONArray("setlists")
            assertEquals(1, exportedSetlists.length())
            assertEquals("setlist-active", exportedSetlists.getJSONObject(0).getString("id"))

            // Apply an unchanged active snapshot: Trashed setlist must NOT be permanently deleted!
            val applied = store.apply(snapshot, snapshot)
            val trashedAfterSync = db.setlistDao().getSetlistById(trashListId)
            assertNotNull("Soft-deleted setlist must survive active-only sync snapshots", trashedAfterSync)
            assertTrue(trashedAfterSync!!.isDeleted)
            val crossRefsAfterSync = db.setlistDao().getCrossRefsForSetlist(trashListId)
            assertEquals("Song memberships of trashed setlists must survive sync", 1, crossRefsAfterSync.size)
            assertEquals(song2Id, crossRefsAfterSync[0].songId)

            // Remote edit to active setlist applies cleanly while trash remains intact
            val remotePayload = JSONObject(snapshot.toString())
            remotePayload.getJSONArray("setlists").getJSONObject(0).put("name", "Renamed Active Gig")
            val updated = store.apply(applied, remotePayload)
            assertEquals("Renamed Active Gig", db.setlistDao().getSetlistById(activeListId)!!.name)
            assertNotNull(db.setlistDao().getSetlistById(trashListId))
            assertTrue(db.setlistDao().getSetlistById(trashListId)!!.isDeleted)
            assertEquals(1, db.setlistDao().getCrossRefsForSetlist(trashListId).size)

            // Restore from trash
            repo.restoreSetlist(trashListId)
            assertFalse(db.setlistDao().getSetlistById(trashListId)!!.isDeleted)
            assertEquals(1, db.setlistDao().getCrossRefsForSetlist(trashListId).size)

            // Trashing again and explicit purge permanently deletes it
            repo.softDeleteSetlist(trashListId)
            assertTrue(db.setlistDao().getSetlistById(trashListId)!!.isDeleted)
            repo.emptySetlistTrash()
            assertNull("Explicit purge must permanently delete trashed setlists", db.setlistDao().getSetlistById(trashListId))
            assertEquals(0, db.setlistDao().getCrossRefsForSetlist(trashListId).size)
            // Active setlist still exists
            assertNotNull(db.setlistDao().getSetlistById(activeListId))
        } finally { db.close() }
    }
}
