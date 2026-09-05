package com.joel.gta.data.repository

import com.joel.gta.data.local.GtaDatabase
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.local.entity.SetlistWithSongs
import com.joel.gta.data.local.entity.SongEntity
import com.joel.gta.data.model.ParsedSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.combine

class SongRepository(private val database: GtaDatabase) {

    private val songDao = database.songDao()
    private val setlistDao = database.setlistDao()

    val allSongs: Flow<List<SongEntity>> = songDao.getAllSongs()
    val favoriteSongs: Flow<List<SongEntity>> = songDao.getFavoriteSongs()
    val deletedSongs: Flow<List<SongEntity>> = songDao.getDeletedSongs()
    
    val allSetlists: Flow<List<SetlistWithSongs>> = setlistDao.getAllSetlistsWithSongs()
        .combine(setlistDao.getAllCrossRefs()) { setlists, crossRefs ->
            val crossRefMap = crossRefs.groupBy { it.setlistId }
            setlists.map { setlistWithSongs ->
                val refs = crossRefMap[setlistWithSongs.setlist.id] ?: emptyList()
                val songMap = setlistWithSongs.songs.associateBy { it.id }
                val sortedSongs = refs.mapNotNull { songMap[it.songId] }
                val remainingSongs = setlistWithSongs.songs.filter { it.id !in sortedSongs.map { s -> s.id } }
                setlistWithSongs.copy(songs = sortedSongs + remainingSongs)
            }
        }

    suspend fun getSongById(id: Long): SongEntity? = withContext(Dispatchers.IO) {
        songDao.getSongById(id)
    }

    /**
     * Inserts or updates a song. If a song with the same title already exists,
     * it updates the content and updates the lastOpenedAt timestamp.
     */
    suspend fun saveOrUpdateSong(
        song: ParsedSong,
        rawContent: String,
        transposeOffset: Int = 0,
        tags: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val existing = songDao.getSongByTitle(song.title)
        if (existing != null) {
            val updated = existing.copy(
                artist = song.artist ?: existing.artist,
                key = song.key ?: existing.key,
                capo = song.capo ?: existing.capo,
                rawContent = rawContent,
                format = song.format.name,
                transposeOffset = transposeOffset,
                tags = if (tags.isNotBlank()) tags else existing.tags,
                isDeleted = false,
                lastOpenedAt = System.currentTimeMillis()
            )
            songDao.updateSong(updated)
            existing.id
        } else {
            val entity = SongEntity(
                title = song.title,
                artist = song.artist,
                key = song.key,
                capo = song.capo,
                rawContent = rawContent,
                format = song.format.name,
                transposeOffset = transposeOffset,
                tags = tags,
                lastOpenedAt = System.currentTimeMillis()
            )
            songDao.insertSong(entity)
        }
    }

    suspend fun insertSongsBatch(entities: List<SongEntity>) = withContext(Dispatchers.IO) {
        songDao.insertSongs(entities)
    }

    suspend fun updateSongTags(id: Long, tags: String) = withContext(Dispatchers.IO) {
        songDao.updateTags(id, tags)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        songDao.updateFavoriteStatus(id, isFavorite)
    }

    suspend fun updateTransposeOffset(id: Long, offset: Int) = withContext(Dispatchers.IO) {
        songDao.updateTransposeOffset(id, offset)
    }

    suspend fun updateLastOpened(id: Long) = withContext(Dispatchers.IO) {
        songDao.updateLastOpened(id, System.currentTimeMillis())
    }


    suspend fun deleteSong(song: SongEntity) = withContext(Dispatchers.IO) {
        songDao.softDeleteSong(song.id)
    }

    suspend fun softDeleteSong(id: Long) = withContext(Dispatchers.IO) {
        songDao.softDeleteSong(id)
    }

    suspend fun restoreSong(id: Long) = withContext(Dispatchers.IO) {
        songDao.restoreSong(id)
    }

    suspend fun permanentDeleteSong(id: Long) = withContext(Dispatchers.IO) {
        songDao.permanentDeleteSong(id)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        songDao.emptyTrash()
    }

    suspend fun updateSongDetails(
        id: Long,
        title: String,
        artist: String?,
        tags: String,
        rawContent: String,
        key: String?,
        capo: String?
    ) = withContext(Dispatchers.IO) {
        songDao.updateSongDetails(id, title, artist, tags, rawContent, key, capo)
    }

    suspend fun createSetlist(name: String): Long = withContext(Dispatchers.IO) {
        setlistDao.insertSetlist(SetlistEntity(name = name))
    }

    suspend fun addSongToSetlist(setlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        val maxPos = setlistDao.getMaxPosition(setlistId)
        setlistDao.addSongToSetlist(
            SetlistSongCrossRef(setlistId = setlistId, songId = songId, position = maxPos + 1)
        )
    }

    suspend fun moveSongInSetlist(setlistId: Long, songId: Long, moveUp: Boolean) = withContext(Dispatchers.IO) {
        val refs = setlistDao.getCrossRefsForSetlist(setlistId).toMutableList()
        val currentIndex = refs.indexOfFirst { it.songId == songId }
        if (currentIndex == -1) return@withContext
        val targetIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex < 0 || targetIndex >= refs.size) return@withContext

        val item = refs.removeAt(currentIndex)
        refs.add(targetIndex, item)
        refs.forEachIndexed { newPos, ref ->
            setlistDao.updatePosition(setlistId, ref.songId, newPos)
        }
    }

    suspend fun removeSongFromSetlist(setlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        setlistDao.removeSongFromSetlist(setlistId, songId)
    }

    suspend fun deleteSetlist(setlist: SetlistEntity) = withContext(Dispatchers.IO) {
        setlistDao.deleteSetlist(setlist)
    }

    suspend fun createBackupPayload(appVersion: String): String = withContext(Dispatchers.IO) {
        val allSongs = songDao.getAllSongsDirect()
        val allSetlists = setlistDao.getAllSetlistsWithSongsDirect()
        com.joel.gta.data.backup.BackupManager.createBackupJson(allSongs, allSetlists, appVersion)
    }

    suspend fun restoreBackup(jsonString: String): com.joel.gta.data.backup.RestoreSummary = withContext(Dispatchers.IO) {
        com.joel.gta.data.backup.BackupManager.restoreBackup(jsonString, songDao, setlistDao)
    }
}
