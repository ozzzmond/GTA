package com.joel.gta.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.joel.gta.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE query = :query COLLATE NOCASE LIMIT 1")
    suspend fun findByQuery(query: String): SearchHistoryEntity?

    @Query("SELECT * FROM search_history WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SearchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SearchHistoryEntity): Long

    @Update
    suspend fun update(item: SearchHistoryEntity)

    @Transaction
    suspend fun insertSearch(query: String, timestamp: Long = System.currentTimeMillis()): Long {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return -1L
        val existing = findByQuery(trimmed)
        return if (existing != null) {
            update(existing.copy(timestamp = timestamp))
            existing.id
        } else {
            insert(SearchHistoryEntity(query = trimmed, timestamp = timestamp))
        }
    }

    @Query("UPDATE search_history SET isImported = 1, importedSongId = :songId WHERE id = :id")
    suspend fun markAsImportedById(id: Long, songId: Long)

    @Query("UPDATE search_history SET isImported = 1, importedSongId = :songId WHERE query = :query COLLATE NOCASE")
    suspend fun markAsImportedByQuery(query: String, songId: Long)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearAllHistory()
}
