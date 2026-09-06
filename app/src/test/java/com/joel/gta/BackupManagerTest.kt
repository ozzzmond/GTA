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
        override suspend fun updateLastOpened(id: Long, timestamp: Long) {}
        override suspend fun deleteSong(song: SongEntity) {}
    }

    // In-memory fake implementation of SetlistDao for unit testing
    private class FakeSetlistDao : SetlistDao {
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
        override suspend fun deleteSetlist(setlist: SetlistEntity) {}
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
}
