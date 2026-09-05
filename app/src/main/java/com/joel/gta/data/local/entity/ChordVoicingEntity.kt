package com.joel.gta.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.joel.gta.data.model.ChordVoicing

@Entity(
    tableName = "chord_voicings",
    indices = [
        Index(value = ["chord"], unique = true)
    ]
)
data class ChordVoicingEntity(
    @PrimaryKey
    val chord: String,
    val baseFret: Int = 1,
    val frets: String, // Comma-separated: e.g. "0,2,2,1,2,2"
    val fingers: String = "", // Comma-separated: e.g. "0,2,3,1,4,4"
    val barres: String = "" // Comma-separated: e.g. "2"
) {
    fun toChordVoicing(): ChordVoicing {
        val parsedFrets = if (frets.isBlank()) emptyList() else frets.split(",").mapNotNull { it.trim().toIntOrNull() }
        val parsedFingers = if (fingers.isBlank()) emptyList() else fingers.split(",").mapNotNull { it.trim().toIntOrNull() }
        val parsedBarres = if (barres.isBlank()) emptyList() else barres.split(",").mapNotNull { it.trim().toIntOrNull() }
        return ChordVoicing(
            chord = chord,
            baseFret = baseFret,
            frets = parsedFrets,
            fingers = parsedFingers,
            barres = parsedBarres
        )
    }

    companion object {
        fun fromChordVoicing(voicing: ChordVoicing): ChordVoicingEntity {
            return ChordVoicingEntity(
                chord = voicing.chord,
                baseFret = voicing.baseFret,
                frets = voicing.frets.joinToString(","),
                fingers = voicing.fingers.joinToString(","),
                barres = voicing.barres.joinToString(",")
            )
        }
    }
}
