package com.joel.gta.data.local.dao

import androidx.room.*
import com.joel.gta.data.local.entity.SetlistEntity
import com.joel.gta.data.local.entity.SetlistSongCrossRef
import com.joel.gta.data.local.entity.SetlistWithSongs
import kotlinx.coroutines.flow.Flow

@Dao
interface SetlistDao {

    @Transaction
    @Query("SELECT * FROM setlists ORDER BY createdAt DESC")
    fun getAllSetlistsWithSongs(): Flow<List<SetlistWithSongs>>

    @Transaction
    @Query("SELECT * FROM setlists ORDER BY createdAt DESC")
    suspend fun getAllSetlistsWithSongsDirect(): List<SetlistWithSongs>

    @Query("SELECT * FROM setlists")
    suspend fun getAllSetlistsDirect(): List<SetlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetlist(setlist: SetlistEntity): Long

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

    @Delete
    suspend fun deleteSetlist(setlist: SetlistEntity)
}
