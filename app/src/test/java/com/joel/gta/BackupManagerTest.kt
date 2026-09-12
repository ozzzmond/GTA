package com.joel.gta

import com.joel.gta.data.backup.BackupManager
import com.joel.gta.data.local.dao.SetlistDao
import com.joel.gta.data.local.dao.SongDao
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.local.entity.SetlistWithSongs
import com.joel.gta.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
class BackupManagerTest {

    // In-memory fake implementation of SongDao for unit testing
    private class FakeSongDao : SongDao {
        val songs = mutableListOf<SongEntity>()
        private var nextId = 1L

        override fun getAllSongs(): Flow<List<SongEntity>> = emptyFlow()
        override fun getFavoriteSongs(): Flow<List<SongEntity>> = emptyFlow()
        override fun getDeletedSongs(): Flow<List<SongEntity>> = emptyFlow()
        override suspend fun getSongById(id: Long): SongEntity? = songs.find { it.id == id }
        override suspend fun getSongByTitle(title: String): SongEntity? = songs.find { it.title.equals(title, ignoreCase = true) }
        override suspend fun getAllSongsDirect(): List<SongEntity> = songs.toList()

        override suspend fun insertSong(song: SongEntity): Long {
            val id = if (song.id == 0L) nextId++ else song.id
            val stored = song.copy(id = id)
            songs.removeAll { it.id == id }
            songs.add(stored)
            return id
        }

        override suspend fun insertSongs(songsList: List<SongEntity>): List<Long> {
            return songsList.map { insertSong(it) }
        }

        override suspend fun updateSong(song: SongEntity) {
            songs.removeAll { it.id == song.id }
            songs.add(song)
        }

        override suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean) {}
        override suspend fun updateTransposeOffset(id: Long, transposeOffset: Int) {}
        override suspend fun updateTags(id: Long, tags: String) {}
        override suspend fun updateSongDetails(id: Long, title: String, artist: String?, tags: String, rawContent: String, key: String?, capo: String?, timestamp: Long) {}
        override suspend fun softDeleteSong(id: Long) {}
        override suspend fun restoreSong(id: Long) {}
        override suspend fun permanentDeleteSong(id: Long) {}
        override suspend fun emptyTrash() {}
        override suspend fun deleteAllSongs() {
            songs.clear()
        }
        override suspend fun updateLastOpened(id: Long, timestamp: Long) {}
        override suspend fun deleteSong(song: SongEntity) {}
    }

    // In-memory fake implementation of SetlistDao for unit testing
    private class FakeSetlistDao : SetlistDao {
        override suspend fun updateSetlist(setlist: SetlistEntity) { insertSetlist(setlist) }
        val setlists = mutableListOf<SetlistEntity>()
        val crossRefs = mutableListOf<SetlistSongCrossRef>()
        private var nextId = 1L

        override fun getAllSetlistsWithSongs(): Flow<List<SetlistWithSongs>> = emptyFlow()
        override suspend fun getAllSetlistsWithSongsDirect(): List<SetlistWithSongs> = emptyList()
        override suspend fun getAllSetlistsDirect(): List<SetlistEntity> = setlists.toList()

        override suspend fun insertSetlist(setlist: SetlistEntity): Long {
            val id = if (setlist.id == 0L) nextId++ else setlist.id
            val stored = setlist.copy(id = id)
            setlists.removeAll { it.id == id }
            setlists.add(stored)
            return id
        }

        override fun getAllCrossRefs(): Flow<List<SetlistSongCrossRef>> = emptyFlow()
        override suspend fun addSongToSetlist(crossRef: SetlistSongCrossRef) {
            crossRefs.add(crossRef)
        }

        override suspend fun getMaxPosition(setlistId: Long): Int =
            crossRefs.filter { it.setlistId == setlistId }.maxOfOrNull { it.position } ?: -1

        override suspend fun getCrossRefsForSetlist(setlistId: Long): List<SetlistSongCrossRef> =
            crossRefs.filter { it.setlistId == setlistId }

        override suspend fun updatePosition(setlistId: Long, songId: Long, newPosition: Int) {}
        override suspend fun removeSongFromSetlist(setlistId: Long, songId: Long) {}
        override suspend fun clearSongsFromSetlist(setlistId: Long) {
            crossRefs.removeAll { it.setlistId == setlistId }
        }
        override suspend fun deleteAllSetlists() {
            setlists.clear()
        }
        override suspend fun deleteAllCrossRefs() {
            crossRefs.clear()
        }
        override suspend fun getActiveSetlistsDirect(): List<SetlistEntity> = setlists.filter { !it.isDeleted }
        override suspend fun getSetlistById(id: Long): SetlistEntity? = setlists.find { it.id == id }
        override fun getDeletedSetlistsWithSongs(): Flow<List<SetlistWithSongs>> = emptyFlow()
        override suspend fun getDeletedSetlistsDirect(): List<SetlistEntity> = setlists.filter { it.isDeleted }
        override suspend fun renameSetlist(id: Long, newName: String) {
            val idx = setlists.indexOfFirst { it.id == id }
            if (idx != -1) setlists[idx] = setlists[idx].copy(name = newName)
        }
        override suspend fun softDeleteSetlist(id: Long) {
            val idx = setlists.indexOfFirst { it.id == id }
            if (idx != -1) setlists[idx] = setlists[idx].copy(isDeleted = true)
        }
        override suspend fun restoreSetlist(id: Long) {
            val idx = setlists.indexOfFirst { it.id == id }
            if (idx != -1) setlists[idx] = setlists[idx].copy(isDeleted = false)
        }
        override suspend fun deleteSetlistById(id: Long) {
            setlists.removeAll { it.id == id }
        }
        override suspend fun purgeDeletedSetlists() {
            setlists.removeAll { it.isDeleted }
        }
        override suspend fun deleteSetlist(setlist: SetlistEntity) {
            setlists.removeAll { it.id == setlist.id }
        }
    }

    @Test
    fun testBackupPayloadCreation() {
        val songs = listOf(
            SongEntity(id = 1, title = "Torete", artist = "Moonstar88", key = "D", rawContent = "D A Bm G"),
            SongEntity(id = 2, title = "With or Without You", artist = "U2", key = "D", rawContent = "D A Bm G")
        )
        val setlists = listOf(
            SetlistWithSongs(
                setlist = SetlistEntity(id = 10, name = "Acoustic Night"),
                songs = listOf(songs[0])
            )
        )

        val jsonString = BackupManager.createBackupJson(songs, setlists, "1.0.24")
        assertNotNull(jsonString)

        val json = JSONObject(jsonString)
        val metadata = json.getJSONObject("metadata")
        assertEquals("GTAR", metadata.getString("appName"))
        assertEquals("1.0.24", metadata.getString("appVersion"))
        assertTrue(metadata.getLong("exportTimestamp") > 0)

        val songsArray = json.getJSONArray("songs")
        assertEquals(2, songsArray.length())
        assertEquals("Torete", songsArray.getJSONObject(0).getString("title"))

        val setlistsArray = json.getJSONArray("setlists")
        assertEquals(1, setlistsArray.length())
        assertEquals("Acoustic Night", setlistsArray.getJSONObject(0).getString("name"))
    }

    @Test
    fun testSmartMergeRestore() = runBlocking {
        val songDao = FakeSongDao()
        val setlistDao = FakeSetlistDao()

        // Pre-existing song in database: "Torete" (older timestamp)
        songDao.insertSong(
            SongEntity(
                id = 101,
                title = "Torete",
                artist = "Moonstar88",
                key = "C", // Old key
                rawContent = "Old lyrics",
                tags = "OPM",
                createdAt = 1000L,
                lastOpenedAt = 1000L
            )
        )

        // Pre-existing song that is NOT in backup: "Ang Huling El Bimbo" (must not be deleted!)
        songDao.insertSong(
            SongEntity(
                id = 102,
                title = "Ang Huling El Bimbo",
                artist = "Eraserheads",
                key = "G",
                rawContent = "G A7 C G"
            )
        )

        // Backup payload containing:
        // 1. Updated "Torete" (newer timestamp, key D, merged tag "Acoustic")
        // 2. New song "With or Without You"
        val backupJson = """
        {
          "metadata": {
            "appName": "GTA",
            "appVersion": "1.0.24",
            "exportTimestamp": 5000
          },
          "songs": [
            {
              "title": "Torete",
              "artist": "Moonstar88",
              "key": "D",
              "capo": "Capo 2",
              "rawContent": "D A Bm G",
              "tags": "Acoustic",
              "isFavorite": true,
              "lastOpenedAt": 5000,
              "createdAt": 5000
            },
            {
              "title": "With or Without You",
              "artist": "U2",
              "key": "D",
              "rawContent": "D A Bm G",
              "tags": "Rock",
              "isFavorite": false,
              "lastOpenedAt": 5000,
              "createdAt": 5000
            }
          ],
          "setlists": [
            {
              "name": "Sunday Live",
              "createdAt": 5000,
              "songs": [
                { "title": "Torete", "artist": "Moonstar88", "position": 0 },
                { "title": "With or Without You", "artist": "U2", "position": 1 }
              ]
            }
          ]
        }
        """.trimIndent()

        val summary = BackupManager.restoreBackup(backupJson, songDao, setlistDao)

        assertEquals(1, summary.songsRestored) // "With or Without You" inserted
        assertEquals(1, summary.songsUpdated)  // "Torete" updated
        assertEquals(1, summary.setlistsRestored) // "Sunday Live" created

        // Verify existing unmentioned song "Ang Huling El Bimbo" was NEVER deleted!
        val elBimbo = songDao.getSongByTitle("Ang Huling El Bimbo")
        assertNotNull("Existing song not in backup must be preserved!", elBimbo)

        // Verify "Torete" was merged
        val updatedTorete = songDao.getSongByTitle("Torete")
        assertNotNull(updatedTorete)
        assertEquals("D", updatedTorete?.key)
        assertEquals("Capo 2", updatedTorete?.capo)
        assertEquals("D A Bm G", updatedTorete?.rawContent)
        assertTrue("Tags should be merged", updatedTorete?.tags?.contains("OPM") == true && updatedTorete.tags.contains("Acoustic"))
        assertTrue("Favorite status should be preserved/updated", updatedTorete?.isFavorite == true)

        // Verify setlist songs were linked to correct song IDs
        val setlists = setlistDao.getAllSetlistsDirect()
        assertEquals(1, setlists.size)
        val setlistId = setlists[0].id
        val refs = setlistDao.getCrossRefsForSetlist(setlistId)
        assertEquals(2, refs.size)
        // Ref 0 should be Torete's ID (101)
        assertEquals(101L, refs[0].songId)
        // Ref 1 should be With or Without You's newly generated ID
        val u2 = songDao.getSongByTitle("With or Without You")
        assertEquals(u2?.id, refs[1].songId)
    }

    @Test
    fun testBackupFileNameFormat() {
        val fileName = BackupManager.generateBackupFileName()
        assertTrue(
            "Backup filename '$fileName' must match strictly 'gtar_backup_YYYYMMDD_HHmm.json'",
            fileName.matches(Regex("^gtar_backup_\\d{8}_\\d{4}\\.json$"))
        )
    }

    @Test
    fun testFullRestoreWithZeroSetlistsDoesNotCreateSetlists() = runBlocking {
        val database = androidx.room.Room.inMemoryDatabaseBuilder(
            org.robolectric.RuntimeEnvironment.getApplication(),
            com.joel.gta.data.local.GtaDatabase::class.java
        ).build()
        val songDao = database.songDao()
        val setlistDao = database.setlistDao()

        // Seed with existing data
        songDao.insertSong(SongEntity(id = 1, title = "Old Song", rawContent = "lyrics"))
        setlistDao.insertSetlist(SetlistEntity(id = 1, name = "Old Setlist"))

        val backupJsonWithZeroSetlists = """
        {
          "metadata": {
            "appName": "GTAR",
            "appVersion": "1.0.44",
            "exportTimestamp": 10000
          },
          "songs": [
            { "title": "Song One", "artist": "Artist 1", "rawContent": "Chords 1" },
            { "title": "Song Two", "artist": "Artist 2", "rawContent": "Chords 2" }
          ],
          "setlists": []
        }
        """.trimIndent()

        val summary = BackupManager.fullRestoreWipeAndReplace(backupJsonWithZeroSetlists, database)
        assertEquals(2, summary.songsRestored)
        assertEquals(0, summary.setlistsRestored)

        // Verify songs are in library
        assertEquals(2, songDao.getAllSongsDirect().size)

        // CRITICAL: Verify NO synthetic setlists were created for the library!
        val remainingSetlists = setlistDao.getAllSetlistsDirect()
        assertEquals(0, remainingSetlists.size)
        database.close()
    }
    @Test
    fun invalidBackupsAndInsertionFailuresPreserveAllData() = runBlocking {
        val database = androidx.room.Room.inMemoryDatabaseBuilder(
            org.robolectric.RuntimeEnvironment.getApplication(),
            com.joel.gta.data.local.GtaDatabase::class.java
        ).build()
        try {
            val song = SongEntity(id = 1, title = "Original", rawContent = "Original lyrics")
            val setlist = SetlistEntity(id = 1, name = "Original setlist")
            val ref = SetlistSongCrossRef(setlistId = 1, songId = 1, position = 0)
            database.songDao().insertSong(song)
            database.setlistDao().insertSetlist(setlist)
            database.setlistDao().addSongToSetlist(ref)
            val invalid = listOf(
                "{", "{}", """{"songs":[]} trailing garbage""", """{"songs":null}""",
                """{"songs":[{"title":"Valid","rawContent":"ok"},{}]}""",
                """{"songs":[{"title":42,"rawContent":"ok"}]}""",
                """{"songs":[{"title":"Bad","rawContent":{}}]}""",
                """{"songs":[null]}""",
                """{"songs":[],"setlists":[{"name":"Gig","songs":[{"title":"Missing"}]}]}"""
            )
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_restore BEFORE INSERT ON songs WHEN NEW.title = 'Fail' BEGIN SELECT RAISE(ABORT, 'forced insertion failure'); END"
            )
            for (payload in invalid + """{"songs":[{"title":"Inserted","rawContent":"ok"},{"title":"Fail","rawContent":"ok"}],"setlists":[]}""") {
                try {
                    BackupManager.fullRestoreWipeAndReplace(payload, database)
                    fail("Restore should fail: $payload")
                } catch (_: Exception) { }
                assertEquals(listOf(song), database.songDao().getAllSongsDirect())
                assertEquals(listOf(setlist), database.setlistDao().getAllSetlistsDirect())
                assertEquals(listOf(ref), database.setlistDao().getCrossRefsForSetlist(1))
            }
            BackupManager.fullRestoreWipeAndReplace("""{"songs":[],"setlists":[]}""", database)
            assertTrue(database.songDao().getAllSongsDirect().isEmpty())
            assertTrue(database.setlistDao().getAllSetlistsDirect().isEmpty())
        } finally {
            database.close()
        }
    }

}

