package com.joel.gta.data.model

enum class SongFormat {
    CHORD_PRO,
    TWO_LINE,
    PLAIN
}

data class ParsedSong(
    val title: String,
    val artist: String? = null,
    val key: String? = null,
    val tempo: String? = null,
    val capo: String? = null,
    val format: SongFormat = SongFormat.TWO_LINE,
    val lines: List<SongLine> = emptyList(),
    val rawText: String = ""
)
