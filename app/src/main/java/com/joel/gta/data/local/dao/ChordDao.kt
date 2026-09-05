package com.joel.gta.data.local.dao

import androidx.room.*
import com.joel.gta.data.local.entity.ChordVoicingEntity

@Dao
interface ChordDao {

    @Query("SELECT * FROM chord_voicings")
    suspend fun getAllVoicings(): List<ChordVoicingEntity>

    @Query("SELECT * FROM chord_voicings WHERE LOWER(chord) = LOWER(:chord) LIMIT 1")
    suspend fun getVoicing(chord: String): ChordVoicingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoicings(voicings: List<ChordVoicingEntity>)

    @Query("SELECT COUNT(*) FROM chord_voicings")
    suspend fun getCount(): Int

    @Query("DELETE FROM chord_voicings")
    suspend fun clearAll()
}
