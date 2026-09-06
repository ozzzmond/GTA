package com.joel.gta

import com.joel.gta.data.local.dao.SetlistDao
import com.joel.gta.data.local.dao.SongDao
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.local.entity.SetlistWithSongs
import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.setlist.SetlistExportImportManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SetlistExportImportTest {

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
        override suspend fun deleteSetlist(setlist: SetlistEntity) {
            setlists.removeAll { it.id == setlist.id }
        }
    }

    @Test
    fun testCreateSetlistJsonSchema() {
        val setlist = SetlistEntity(id = 1, name = "Acoustic Night", createdAt = 1757150000000L)
        val songs = listOf(
            SongEntity(
                id = 10,
                title = "Wonderwall",
                artist = "Oasis",
                key = "Em",
                rawContent = "[Em]Today is gonna be the [G]day"
            ),
            SongEntity(
                id = 20,
                title = "Hotel California",
                artist = "Eagles",
                key = "Bm",
                rawContent = "[Bm]On a dark desert highway"
            )
        )

        val jsonString = SetlistExportImportManager.createSetlistJson(setlist, songs)
        val root = JSONObject(jsonString)

        assertEquals(1, root.getInt("version"))
        assertEquals("GTAR_SETLIST", root.getString("type"))
        assertEquals("Acoustic Night", root.getString("name"))
        assertTrue(root.getString("createdAt").contains("T"))

        val songsArray = root.getJSONArray("songs")
        assertEquals(2, songsArray.length())

        val song1 = songsArray.getJSONObject(0)
        assertEquals("Wonderwall", song1.getString("title"))
        assertEquals("Oasis", song1.getString("artist"))
        assertEquals("Em", song1.getString("key"))
        assertEquals("[Em]Today is gonna be the [G]day", song1.getString("chordsContent"))
        assertEquals(1, song1.getInt("order"))

        val song2 = songsArray.getJSONObject(1)
        assertEquals("Hotel California", song2.getString("title"))
        assertEquals(2, song2.getInt("order"))
    }

    @Test
    fun testIsSetlistJsonDetection() {
        val validSetlist = """
            {
              "version": 1,
              "type": "GTAR_SETLIST",
              "name": "Live Gig",
              "createdAt": "2026-09-06T12:00:00Z",
              "songs": []
            }
        """.trimIndent()
        assertTrue(SetlistExportImportManager.isSetlistJson(validSetlist))

        val regularText = "G C D\nSome lyrics here"
        assertFalse(SetlistExportImportManager.isSetlistJson(regularText))

        val backupJson = """
            {
              "metadata": { "appName": "GTAR" },
              "songs": []
            }
        """.trimIndent()
        assertFalse(SetlistExportImportManager.isSetlistJson(backupJson))
    }

    @Test
    fun testImportSetlistWithDuplicatesIncrementingSuffix() = runBlocking {
        val fakeSongDao = FakeSongDao()
        val fakeSetlistDao = FakeSetlistDao()

        // Pre-populate DB with existing song and setlist
        fakeSongDao.insertSong(
            SongEntity(id = 1, title = "Chop Suey", artist = "System of a Down", rawContent = "Wake up!")
        )
        fakeSongDao.insertSong(
            SongEntity(id = 2, title = "Chop Suey (1)", artist = "System of a Down", rawContent = "Wake up again!")
        )
        fakeSetlistDao.insertSetlist(
            SetlistEntity(id = 1, name = "Metal Set")
        )

        val incomingJson = """
            {
              "version": 1,
              "type": "GTAR_SETLIST",
              "name": "Metal Set",
              "createdAt": "2026-09-06T12:00:00Z",
              "songs": [
                {
                  "title": "Chop Suey",
                  "artist": "System of a Down",
                  "key": "Gm",
                  "chordsContent": "Grab a brush and put a little makeup",
                  "order": 1
                },
                {
                  "title": "Toxicity",
                  "artist": "System of a Down",
                  "key": "Cm",
                  "chordsContent": "Conversion software version 7.0",
                  "order": 2
                }
              ]
            }
        """.trimIndent()

        val result = SetlistExportImportManager.importSetlist(incomingJson, fakeSongDao, fakeSetlistDao)

        // Setlist name should have incremented to "Metal Set (1)"
        assertEquals("Metal Set (1)", result.setlistName)
        assertEquals(2, result.songsImportedCount)

        // Chop Suey duplicate should have become "Chop Suey (2)" because base and (1) already exist
        val importedChopSuey = fakeSongDao.songs.find { it.rawContent.contains("Grab a brush") }
        assertNotNull(importedChopSuey)
        assertEquals("Chop Suey (2)", importedChopSuey!!.title)

        // Toxicity was unique so it retains its name
        val importedToxicity = fakeSongDao.songs.find { it.title == "Toxicity" }
        assertNotNull(importedToxicity)

        // Check cross references are properly ordered
        val refs = fakeSetlistDao.crossRefs.filter { it.setlistId == result.setlistId }
        assertEquals(2, refs.size)
        assertEquals(0, refs[0].position)
        assertEquals(importedChopSuey.id, refs[0].songId)
        assertEquals(1, refs[1].position)
        assertEquals(importedToxicity!!.id, refs[1].songId)
    }
}
