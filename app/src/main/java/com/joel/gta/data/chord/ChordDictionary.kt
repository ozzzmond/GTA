package com.joel.gta.data.chord

import android.content.Context
import com.joel.gta.data.model.ChordVoicing
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

object ChordDictionary {

    private val cache = ConcurrentHashMap<String, ChordVoicing>()
    private var isLoadedFromAssets = false

    // Built-in offline fallback definitions for standard guitar chords
    private val standardChords = listOf(
        ChordVoicing("C", 1, listOf(-1, 3, 2, 0, 1, 0), listOf(0, 3, 2, 0, 1, 0)),
        ChordVoicing("Cm", 3, listOf(-1, 3, 5, 5, 4, 3), listOf(0, 1, 3, 4, 2, 1), listOf(3)),
        ChordVoicing("C7", 1, listOf(-1, 3, 2, 3, 1, 0), listOf(0, 3, 2, 4, 1, 0)),
        ChordVoicing("Cmaj7", 1, listOf(-1, 3, 2, 0, 0, 0), listOf(0, 3, 2, 0, 0, 0)),
        ChordVoicing("Cadd9", 1, listOf(-1, 3, 2, 0, 3, 0), listOf(0, 2, 1, 0, 3, 0)),
        ChordVoicing("Csus4", 1, listOf(-1, 3, 3, 0, 1, 1), listOf(0, 3, 4, 0, 1, 1)),
        ChordVoicing("C#", 4, listOf(-1, 4, 6, 6, 6, 4), listOf(0, 1, 2, 3, 4, 1), listOf(4)),
        ChordVoicing("C#m", 4, listOf(-1, 4, 6, 6, 5, 4), listOf(0, 1, 3, 4, 2, 1), listOf(4)),
        ChordVoicing("D", 1, listOf(-1, -1, 0, 2, 3, 2), listOf(0, 0, 0, 1, 3, 2)),
        ChordVoicing("Dm", 1, listOf(-1, -1, 0, 2, 3, 1), listOf(0, 0, 0, 2, 3, 1)),
        ChordVoicing("D7", 1, listOf(-1, -1, 0, 2, 1, 2), listOf(0, 0, 0, 2, 1, 3)),
        ChordVoicing("Dmaj7", 1, listOf(-1, -1, 0, 2, 2, 2), listOf(0, 0, 0, 1, 1, 1), listOf(2)),
        ChordVoicing("Dm7", 1, listOf(-1, -1, 0, 2, 1, 1), listOf(0, 0, 0, 2, 1, 1)),
        ChordVoicing("Dsus4", 1, listOf(-1, -1, 0, 2, 3, 3), listOf(0, 0, 0, 1, 3, 4)),
        ChordVoicing("Dsus2", 1, listOf(-1, -1, 0, 2, 3, 0), listOf(0, 0, 0, 1, 2, 0)),
        ChordVoicing("D/F#", 1, listOf(2, 0, 0, 2, 3, 2), listOf(1, 0, 0, 2, 4, 3)),
        ChordVoicing("E", 1, listOf(0, 2, 2, 1, 0, 0), listOf(0, 2, 3, 1, 0, 0)),
        ChordVoicing("Em", 1, listOf(0, 2, 2, 0, 0, 0), listOf(0, 2, 3, 0, 0, 0)),
        ChordVoicing("E7", 1, listOf(0, 2, 0, 1, 0, 0), listOf(0, 2, 0, 1, 0, 0)),
        ChordVoicing("Em7", 1, listOf(0, 2, 2, 0, 3, 0), listOf(0, 2, 3, 0, 4, 0)),
        ChordVoicing("Emaj7", 1, listOf(0, 2, 1, 1, 0, 0), listOf(0, 3, 1, 2, 0, 0)),
        ChordVoicing("Esus4", 1, listOf(0, 2, 2, 2, 0, 0), listOf(0, 2, 3, 4, 0, 0)),
        ChordVoicing("F", 1, listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1), listOf(1)),
        ChordVoicing("Fm", 1, listOf(1, 3, 3, 1, 1, 1), listOf(1, 3, 4, 1, 1, 1), listOf(1)),
        ChordVoicing("F7", 1, listOf(1, 3, 1, 2, 1, 1), listOf(1, 3, 1, 2, 1, 1), listOf(1)),
        ChordVoicing("Fmaj7", 1, listOf(-1, -1, 3, 2, 1, 0), listOf(0, 0, 3, 2, 1, 0)),
        ChordVoicing("F#", 2, listOf(2, 4, 4, 3, 2, 2), listOf(1, 3, 4, 2, 1, 1), listOf(2)),
        ChordVoicing("F#m", 2, listOf(2, 4, 4, 2, 2, 2), listOf(1, 3, 4, 1, 1, 1), listOf(2)),
        ChordVoicing("F#7", 2, listOf(2, 4, 2, 3, 2, 2), listOf(1, 3, 1, 2, 1, 1), listOf(2)),
        ChordVoicing("F#m7", 2, listOf(2, 4, 2, 2, 2, 2), listOf(1, 3, 1, 1, 1, 1), listOf(2)),
        ChordVoicing("G", 1, listOf(3, 2, 0, 0, 0, 3), listOf(2, 1, 0, 0, 0, 3)),
        ChordVoicing("Gm", 3, listOf(3, 5, 5, 3, 3, 3), listOf(1, 3, 4, 1, 1, 1), listOf(3)),
        ChordVoicing("G7", 1, listOf(3, 2, 0, 0, 0, 1), listOf(3, 2, 0, 0, 0, 1)),
        ChordVoicing("Gmaj7", 1, listOf(3, 2, 0, 0, 0, 2), listOf(3, 2, 0, 0, 0, 1)),
        ChordVoicing("Gm7", 3, listOf(3, 5, 3, 3, 3, 3), listOf(1, 3, 1, 1, 1, 1), listOf(3)),
        ChordVoicing("Gsus4", 1, listOf(3, 3, 0, 0, 1, 3), listOf(3, 4, 0, 0, 1, 2)),
        ChordVoicing("G/B", 1, listOf(-1, 2, 0, 0, 3, 3), listOf(0, 1, 0, 0, 3, 4)),
        ChordVoicing("A", 1, listOf(-1, 0, 2, 2, 2, 0), listOf(0, 0, 1, 2, 3, 0)),
        ChordVoicing("Am", 1, listOf(-1, 0, 2, 2, 1, 0), listOf(0, 0, 2, 3, 1, 0)),
        ChordVoicing("A7", 1, listOf(-1, 0, 2, 0, 2, 0), listOf(0, 0, 2, 0, 3, 0)),
        ChordVoicing("Amaj7", 1, listOf(-1, 0, 2, 1, 2, 0), listOf(0, 0, 2, 1, 3, 0)),
        ChordVoicing("Am7", 1, listOf(-1, 0, 2, 0, 1, 0), listOf(0, 0, 2, 0, 1, 0)),
        ChordVoicing("Asus4", 1, listOf(-1, 0, 2, 2, 3, 0), listOf(0, 0, 1, 2, 3, 0)),
        ChordVoicing("Asus2", 1, listOf(-1, 0, 2, 2, 0, 0), listOf(0, 0, 1, 2, 0, 0)),
        ChordVoicing("Bb", 1, listOf(-1, 1, 3, 3, 3, 1), listOf(0, 1, 2, 3, 4, 1), listOf(1)),
        ChordVoicing("Bbm", 1, listOf(-1, 1, 3, 3, 2, 1), listOf(0, 1, 3, 4, 2, 1), listOf(1)),
        ChordVoicing("Bb7", 1, listOf(-1, 1, 3, 1, 3, 1), listOf(0, 1, 3, 1, 4, 1), listOf(1)),
        ChordVoicing("B", 2, listOf(-1, 2, 4, 4, 4, 2), listOf(0, 1, 2, 3, 4, 1), listOf(2)),
        ChordVoicing("Bm", 2, listOf(-1, 2, 4, 4, 3, 2), listOf(0, 1, 3, 4, 2, 1), listOf(2)),
        ChordVoicing("B7", 1, listOf(-1, 2, 1, 2, 0, 2), listOf(0, 2, 1, 3, 0, 4)),
        ChordVoicing("Bmaj7", 2, listOf(-1, 2, 4, 3, 4, 2), listOf(0, 1, 3, 2, 4, 1), listOf(2)),
        ChordVoicing("Bm7", 2, listOf(-1, 2, 4, 2, 3, 2), listOf(0, 1, 3, 1, 2, 1), listOf(2)),
        ChordVoicing("Bsus4", 2, listOf(-1, 2, 4, 4, 5, 2), listOf(0, 1, 2, 3, 4, 1), listOf(2))
    )

    init {
        for (item in standardChords) {
            cache[normalizeKey(item.chord)] = item
        }
    }

    /**
     * Loads chord definitions from assets/chords_db.json if available.
     */
    fun loadFromAssets(context: Context) {
        if (isLoadedFromAssets) return
        try {
            val jsonString = context.assets.open("chords_db.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val chord = obj.getString("chord")
                val baseFret = obj.optInt("baseFret", 1)
                val fretsArray = obj.getJSONArray("frets")
                val frets = (0 until fretsArray.length()).map { fretsArray.getInt(it) }

                val fingers = if (obj.has("fingers")) {
                    val arr = obj.getJSONArray("fingers")
                    (0 until arr.length()).map { arr.getInt(it) }
                } else emptyList()

                val barres = if (obj.has("barres")) {
                    val arr = obj.getJSONArray("barres")
                    (0 until arr.length()).map { arr.getInt(it) }
                } else emptyList()

                val voicing = ChordVoicing(chord, baseFret, frets, fingers, barres)
                cache[normalizeKey(chord)] = voicing
            }
            isLoadedFromAssets = true
        } catch (_: Exception) {
            // Fallback definitions remain active
        }
    }

    /**
     * Looks up chord voicing with smart fallback (e.g. D/F# -> D, Gmaj7 -> G, etc.)
     */
    fun getVoicing(rawChord: String, context: Context? = null): ChordVoicing? {
        if (context != null && !isLoadedFromAssets) {
            loadFromAssets(context)
        }

        val cleaned = rawChord.trim()
            .trim('[', ']', '(', ')', '{', '}', ',', ';', ':')
            .trim()

        if (cleaned.isBlank()) return null

        // Direct lookup
        cache[normalizeKey(cleaned)]?.let { return it }

        // Enharmonic equivalent lookup (e.g. C# <-> Db, F# <-> Gb, G# <-> Ab, A# <-> Bb, D# <-> Eb)
        val enharmonic = getEnharmonic(cleaned)
        if (enharmonic != null) {
            cache[normalizeKey(enharmonic)]?.let { return it }
        }

        // Slash chord fallback (e.g. G/B -> G, D/F# -> D)
        if (cleaned.contains("/")) {
            val root = cleaned.substringBefore("/")
            cache[normalizeKey(root)]?.let {
                return it.copy(chord = cleaned)
            }
        }

        // Extended chord fallback (e.g. Cadd9 -> C, Gsus4 -> G, etc.)
        val rootFallback = extractRootChord(cleaned)
        if (rootFallback != null && rootFallback != cleaned) {
            cache[normalizeKey(rootFallback)]?.let {
                return it.copy(chord = cleaned)
            }
        }

        return null
    }

    private fun normalizeKey(name: String): String {
        return name.trim().lowercase()
    }

    private fun getEnharmonic(chord: String): String? {
        return when {
            chord.startsWith("C#", ignoreCase = true) -> "Db" + chord.substring(2)
            chord.startsWith("Db", ignoreCase = true) -> "C#" + chord.substring(2)
            chord.startsWith("D#", ignoreCase = true) -> "Eb" + chord.substring(2)
            chord.startsWith("Eb", ignoreCase = true) -> "D#" + chord.substring(2)
            chord.startsWith("F#", ignoreCase = true) -> "Gb" + chord.substring(2)
            chord.startsWith("Gb", ignoreCase = true) -> "F#" + chord.substring(2)
            chord.startsWith("G#", ignoreCase = true) -> "Ab" + chord.substring(2)
            chord.startsWith("Ab", ignoreCase = true) -> "G#" + chord.substring(2)
            chord.startsWith("A#", ignoreCase = true) -> "Bb" + chord.substring(2)
            chord.startsWith("Bb", ignoreCase = true) -> "A#" + chord.substring(2)
            else -> null
        }
    }

    private fun extractRootChord(chord: String): String? {
        val match = Regex("^([A-G][b#]?(?:m)?)").find(chord)
        return match?.groupValues?.get(1)
    }
}
