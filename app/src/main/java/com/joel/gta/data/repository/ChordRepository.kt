package com.joel.gta.data.repository

import android.content.Context
import com.joel.gta.data.chord.ChordDictionary
import com.joel.gta.data.local.GtaDatabase
import com.joel.gta.data.local.dao.ChordDao
import com.joel.gta.data.local.entity.ChordVoicingEntity
import com.joel.gta.data.model.ChordVoicing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChordRepository(private val database: GtaDatabase) {

    private val chordDao: ChordDao = database.chordDao()

    /**
     * Preloads guitar chords on app launch into Room database and in-memory cache.
     * On first launch, parses assets/guitar_chords.json and inserts into Room.
     * On subsequent launches, populates the memory cache from Room for instant lookup.
     */
    suspend fun preloadIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        try {
            val count = chordDao.getCount()
            if (count > 0) {
                val entities = chordDao.getAllVoicings()
                ChordDictionary.populate(entities.map { it.toChordVoicing() })
            } else {
                val voicings = ChordDictionary.loadVoicingsFromAssets(context)
                if (voicings.isNotEmpty()) {
                    val entities = voicings.map { ChordVoicingEntity.fromChordVoicing(it) }
                    chordDao.insertVoicings(entities)
                    ChordDictionary.populate(voicings)
                }
            }
        } catch (_: Exception) {
            // Graceful resilience: offline fallbacks in ChordDictionary remain active
        }
    }

    suspend fun getVoicing(chord: String): ChordVoicing? = withContext(Dispatchers.IO) {
        val cached = ChordDictionary.getVoicing(chord)
        if (cached != null) return@withContext cached

        val entity = chordDao.getVoicing(chord)
        if (entity != null) {
            val voicing = entity.toChordVoicing()
            ChordDictionary.populate(listOf(voicing))
            return@withContext voicing
        }
        null
    }
}
