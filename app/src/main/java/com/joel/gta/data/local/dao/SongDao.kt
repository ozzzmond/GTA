package com.joel.gta.data.local.dao

import androidx.room.*
import com.joel.gta.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs WHERE isDeleted = 0 ORDER BY lastOpenedAt DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY lastOpenedAt DESC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isDeleted = 1 ORDER BY lastOpenedAt DESC")
    fun getDeletedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE title = :title LIMIT 1")
    suspend fun getSongByTitle(title: String): SongEntity?

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsDirect(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>): List<Long>

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET transposeOffset = :transposeOffset WHERE id = :id")
    suspend fun updateTransposeOffset(id: Long, transposeOffset: Int)

    @Query("UPDATE songs SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String)

    @Query("UPDATE songs SET title = :title, artist = :artist, tags = :tags, rawContent = :rawContent, key = :key, capo = :capo, lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateSongDetails(
        id: Long,
        title: String,
        artist: String?,
        tags: String,
        rawContent: String,
        key: String?,
        capo: String?,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE songs SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteSong(id: Long)

    @Query("UPDATE songs SET isDeleted = 0 WHERE id = :id")
    suspend fun restoreSong(id: Long)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun permanentDeleteSong(id: Long)

    @Query("DELETE FROM songs WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("UPDATE songs SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: Long, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteSong(song: SongEntity)
}
