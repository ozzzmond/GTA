package com.joel.gta.data.local.dao

import androidx.room.*
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.local.entity.SetlistWithSongs
import kotlinx.coroutines.flow.Flow

@Dao
interface SetlistDao {
    @Update
    suspend fun updateSetlist(setlist: SetlistEntity)

    @Transaction
    @Query("SELECT * FROM setlists WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllSetlistsWithSongs(): Flow<List<SetlistWithSongs>>

    @Transaction
    @Query("SELECT * FROM setlists WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllSetlistsWithSongsDirect(): List<SetlistWithSongs>

    @Query("SELECT * FROM setlists WHERE isDeleted = 0")
    suspend fun getActiveSetlistsDirect(): List<SetlistEntity>

    @Query("SELECT * FROM setlists")
    suspend fun getAllSetlistsDirect(): List<SetlistEntity>

    @Transaction
    @Query("SELECT * FROM setlists WHERE isDeleted = 1 ORDER BY createdAt DESC")
    fun getDeletedSetlistsWithSongs(): Flow<List<SetlistWithSongs>>

    @Query("SELECT * FROM setlists WHERE isDeleted = 1")
    suspend fun getDeletedSetlistsDirect(): List<SetlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetlist(setlist: SetlistEntity): Long

    @Query("UPDATE setlists SET name = :newName WHERE id = :id")
    suspend fun renameSetlist(id: Long, newName: String)

    @Query("UPDATE setlists SET isDeleted = 1 WHERE id = :id")
    suspend fun softDeleteSetlist(id: Long)

    @Query("UPDATE setlists SET isDeleted = 0 WHERE id = :id")
    suspend fun restoreSetlist(id: Long)

    @Query("DELETE FROM setlists WHERE id = :id")
    suspend fun deleteSetlistById(id: Long)

    @Query("DELETE FROM setlists WHERE isDeleted = 1")
    suspend fun purgeDeletedSetlists()

    @Query("SELECT * FROM setlist_songs ORDER BY setlistId, position ASC")
    fun getAllCrossRefs(): Flow<List<SetlistSongCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToSetlist(crossRef: SetlistSongCrossRef)

    @Query("SELECT COALESCE(MAX(position), -1) FROM setlist_songs WHERE setlistId = :setlistId")
    suspend fun getMaxPosition(setlistId: Long): Int

    @Query("SELECT * FROM setlist_songs WHERE setlistId = :setlistId ORDER BY position ASC")
    suspend fun getCrossRefsForSetlist(setlistId: Long): List<SetlistSongCrossRef>

    @Query("UPDATE setlist_songs SET position = :newPosition WHERE setlistId = :setlistId AND songId = :songId")
    suspend fun updatePosition(setlistId: Long, songId: Long, newPosition: Int)

    @Query("DELETE FROM setlist_songs WHERE setlistId = :setlistId AND songId = :songId")
    suspend fun removeSongFromSetlist(setlistId: Long, songId: Long)

    @Query("DELETE FROM setlist_songs WHERE setlistId = :setlistId")
    suspend fun clearSongsFromSetlist(setlistId: Long)

    @Query("DELETE FROM setlists")
    suspend fun deleteAllSetlists()

    @Query("DELETE FROM setlist_songs")
    suspend fun deleteAllCrossRefs()

    @Delete
    suspend fun deleteSetlist(setlist: SetlistEntity)
}
