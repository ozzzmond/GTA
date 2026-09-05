package com.joel.gta.data.model

/**
 * Represents a parsed line in a song sheet.
 */
sealed class SongLine {
    /** Blank line between verses/choruses */
    object EmptyLine : SongLine()

    /** Section headers like [Verse 1], [Chorus], [Intro], [Bridge], [Outro] */
    data class SectionHeader(val title: String) : SongLine()

    /** Guitar tablature line, e.g., e|---0-2-3---| */
    data class TabLine(val content: String) : SongLine()

    /** Standalone chord row (2-line tabs / chord-over-lyric format) */
    data class ChordLine(val chords: String) : SongLine()

    /** Regular lyric line */
    data class LyricLine(val lyrics: String) : SongLine()

    /** ChordPro line containing inline chords like [G]Amazing [D]grace */
    data class ChordProLine(
        val raw: String,
        val segments: List<ChordSegment>
    ) : SongLine()
}

/**
 * Represents a chord and the lyric segment it is attached to in ChordPro format.
 * E.g., for "[G]Amazing", chord = "G", text = "Amazing".
 */
data class ChordSegment(
    val chord: String?,
    val text: String
)
