package com.joel.gta.data.engine

import com.joel.gta.data.model.ChordSegment
import com.joel.gta.data.model.ParsedSong
import com.joel.gta.data.model.SongLine
import com.joel.gta.data.parser.ChordRegex

object TransposeEngine {

    private val SHARP_SCALE = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_SCALE = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    private val NOTE_PITCH_MAP = mapOf(
        "C" to 0, "B#" to 0,
        "C#" to 1, "DB" to 1, "Db" to 1,
        "D" to 2,
        "D#" to 3, "EB" to 3, "Eb" to 3,
        "E" to 4, "FB" to 4, "Fb" to 4,
        "F" to 5, "E#" to 5,
        "F#" to 6, "GB" to 6, "Gb" to 6,
        "G" to 7,
        "G#" to 8, "AB" to 8, "Ab" to 8,
        "A" to 9,
        "A#" to 10, "BB" to 10, "Bb" to 10,
        "B" to 11, "CB" to 11, "Cb" to 11
    )

    /**
     * Regex splitting chord into:
     * Group 1: Root note (e.g., "D", "F#", "Bb")
     * Group 2: Modifiers / quality (e.g., "m7", "sus4", "maj9")
     * Group 3: Optional slash bass note (e.g., "F#", "A")
     */
    private val CHORD_SPLIT_REGEX = Regex(
        "^([A-G][#b]?)(.*?)(?:\\/([A-G][#b]?))?$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Transposes a single note by given semitones (-11..+11).
     */
    fun transposeNote(note: String, semitones: Int, preferFlats: Boolean = false): String {
        val pitch = NOTE_PITCH_MAP[note] ?: return note
        val targetPitch = Math.floorMod(pitch + semitones, 12)
        val scale = if (preferFlats) FLAT_SCALE else SHARP_SCALE
        return scale[targetPitch]
    }

    /**
     * Transposes a single chord (e.g., "D/F#" + 2 -> "E/G#").
     */
    fun transposeChord(chord: String, semitones: Int, preferFlats: Boolean = false): String {
        if (semitones % 12 == 0) return chord
        val clean = chord.trim(' ', '[', ']', '<', '>', '(', ')')
        if (clean.equals("N.C.", ignoreCase = true) || clean.equals("NC", ignoreCase = true)) {
            return chord
        }

        val match = CHORD_SPLIT_REGEX.matchEntire(clean) ?: return chord
        val root = match.groupValues[1]
        val quality = match.groupValues[2]
        val slashBass = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }

        val shouldUseFlats = preferFlats || root.contains('b') || (slashBass?.contains('b') == true)

        val newRoot = transposeNote(root, semitones, shouldUseFlats)
        val newSlash = slashBass?.let { "/${transposeNote(it, semitones, shouldUseFlats)}" } ?: ""

        return "$newRoot$quality$newSlash"
    }

    /**
     * Transposes a full chord line while preserving column-width alignment.
     * Uses spacing compensation so following chords don't shift out of position.
     */
    fun transposeChordLine(chordLine: String, semitones: Int, preferFlats: Boolean = false): String {
        if (semitones % 12 == 0 || chordLine.isBlank()) return chordLine

        val result = StringBuilder()
        var i = 0
        while (i < chordLine.length) {
            if (chordLine[i].isWhitespace()) {
                result.append(chordLine[i])
                i++
            } else {
                // Find end of current token
                val start = i
                while (i < chordLine.length && !chordLine[i].isWhitespace()) {
                    i++
                }
                val rawToken = chordLine.substring(start, i)
                val cleanToken = rawToken.trim('(', ')', '[', ']', '<', '>', ',', '|', '-', '–', '—', ':', ';', '~')

                if (cleanToken.isNotEmpty() && ChordRegex.CHORD_TOKEN_REGEX.matches(cleanToken)) {
                    val prefix = rawToken.takeWhile { it in "([<,|-–—:;~" }
                    val suffix = rawToken.takeLastWhile { it in ")]>|,|-–—:;~" }
                    val transposed = transposeChord(cleanToken, semitones, preferFlats)
                    val replacement = "$prefix$transposed$suffix"

                    result.append(replacement)

                    // Spacing compensation to keep columns aligned (preserving single delimiter spaces)
                    val diff = replacement.length - rawToken.length
                    if (diff > 0) {
                        var spaceCount = 0
                        var checkIdx = i
                        while (checkIdx < chordLine.length && chordLine[checkIdx] == ' ') {
                            spaceCount++
                            checkIdx++
                        }
                        val maxConsumable = (spaceCount - 1).coerceAtLeast(0)
                        val spacesToConsume = minOf(diff, maxConsumable)
                        i += spacesToConsume
                    } else if (diff < 0) {
                        var spaceCount = 0
                        var checkIdx = i
                        while (checkIdx < chordLine.length && chordLine[checkIdx] == ' ') {
                            spaceCount++
                            checkIdx++
                        }
                        if (spaceCount > 1) {
                            repeat(-diff) {
                                result.append(' ')
                            }
                        }
                    }
                } else if (cleanToken.contains('-') || cleanToken.contains('–') || cleanToken.contains('—') || cleanToken.contains('|')) {
                    // Handle hyphen/pipe connected progressions without spaces (e.g. G-D/F# or |G|D|)
                    val parts = rawToken.split(Regex("(?<=[-|–—])|(?=[-|–—])"))
                    val transposedParts = parts.joinToString("") { part ->
                        val subClean = part.trim('(', ')', '[', ']', '<', '>', ',', '|', '-', '–', '—', ':', ';', '~')
                        if (subClean.isNotEmpty() && ChordRegex.CHORD_TOKEN_REGEX.matches(subClean)) {
                            val subPrefix = part.takeWhile { it in "([<,|-–—:;~" }
                            val subSuffix = part.takeLastWhile { it in ")]>|,|-–—:;~" }
                            "$subPrefix${transposeChord(subClean, semitones, preferFlats)}$subSuffix"
                        } else {
                            part
                        }
                    }
                    result.append(transposedParts)
                } else {
                    result.append(rawToken)
                }
            }
        }
        return result.toString()
    }

    /**
     * Transposes an entire ParsedSong by semitones.
     */
    fun transposeSong(song: ParsedSong, semitones: Int, preferFlats: Boolean = false): ParsedSong {
        if (semitones % 12 == 0) return song

        val newKey = song.key?.let { transposeChord(it, semitones, preferFlats) }

        val newLines = song.lines.map { line ->
            when (line) {
                is SongLine.ChordLine -> {
                    SongLine.ChordLine(transposeChordLine(line.chords, semitones, preferFlats))
                }
                is SongLine.ChordProLine -> {
                    val newSegments = line.segments.map { seg ->
                        val newChord = seg.chord?.let { transposeChord(it, semitones, preferFlats) }
                        ChordSegment(chord = newChord, text = seg.text)
                    }
                    SongLine.ChordProLine(line.raw, newSegments)
                }
                else -> line
            }
        }

        return song.copy(
            key = newKey,
            lines = newLines
        )
    }

    /**
     * Capo Recommendation Information.
     */
    data class CapoRecommendation(
        val fret: Int,
        val playAsKey: String,
        val originalKey: String,
        val explanation: String
    )

    /**
     * Calculates capo options when shifting keys.
     * E.g. if desired key is A (+2 semitones from G),
     * you can place Capo on Fret 2 and play G shapes.
     */
    fun calculateCapoOptions(originalKey: String?): List<CapoRecommendation> {
        val key = originalKey?.trim() ?: return emptyList()
        val originalPitch = NOTE_PITCH_MAP[key] ?: return emptyList()

        val commonGuitarShapes = listOf("C", "A", "G", "E", "D")
        val recommendations = mutableListOf<CapoRecommendation>()

        for (shape in commonGuitarShapes) {
            val shapePitch = NOTE_PITCH_MAP[shape] ?: continue
            val fret = Math.floorMod(originalPitch - shapePitch, 12)
            if (fret in 1..7) { // Practical capo frets on standard guitars
                recommendations.add(
                    CapoRecommendation(
                        fret = fret,
                        playAsKey = shape,
                        originalKey = key,
                        explanation = "Put Capo on Fret $fret to play standard open $shape chord shapes."
                    )
                )
            }
        }
        return recommendations.sortedBy { it.fret }
    }
}
