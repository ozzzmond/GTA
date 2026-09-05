package com.joel.gta.data.chord

import android.content.Context
import com.joel.gta.data.model.ChordVoicing
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object ChordDictionary {

    private val cache = ConcurrentHashMap<String, ChordVoicing>()
    @Volatile
    private var isLoadedFromAssets = false

    // Built-in offline fallback definitions for standard guitar chords
    private val standardChords = listOf(
        ChordVoicing("C", 1, listOf(-1, 3, 2, 0, 1, 0), listOf(0, 3, 2, 0, 1, 0)),
        ChordVoicing("Cm", 3, listOf(-1, 3, 5, 5, 4, 3), listOf(0, 1, 3, 4, 2, 1), listOf(3)),
        ChordVoicing("C7", 1, listOf(-1, 3, 2, 3, 1, 0), listOf(0, 3, 2, 4, 1, 0)),
        ChordVoicing("Cmaj7", 1, listOf(-1, 3, 2, 0, 0, 0), listOf(0, 3, 2, 0, 0, 0)),
        ChordVoicing("Cm7", 3, listOf(-1, 3, 5, 3, 4, 3), listOf(0, 1, 3, 1, 2, 1), listOf(3)),
        ChordVoicing("Cadd9", 1, listOf(-1, 3, 2, 0, 3, 0), listOf(0, 2, 1, 0, 3, 0)),
        ChordVoicing("Csus4", 1, listOf(-1, 3, 3, 0, 1, 1), listOf(0, 3, 4, 0, 1, 1)),
        ChordVoicing("Csus2", 1, listOf(-1, 3, 0, 0, 1, 0), listOf(0, 3, 0, 0, 1, 0)),
        ChordVoicing("C6/9", 1, listOf(-1, 3, 2, 2, 3, 3), listOf(0, 2, 1, 1, 3, 4), listOf(2)),
        ChordVoicing("C11", 1, listOf(-1, 3, 3, 3, 3, 1), listOf(0, 3, 3, 3, 3, 1), listOf(3)),
        ChordVoicing("C#", 4, listOf(-1, 4, 6, 6, 6, 4), listOf(0, 1, 2, 3, 4, 1), listOf(4)),
        ChordVoicing("C#m", 4, listOf(-1, 4, 6, 6, 5, 4), listOf(0, 1, 3, 4, 2, 1), listOf(4)),
        ChordVoicing("D", 1, listOf(-1, -1, 0, 2, 3, 2), listOf(0, 0, 0, 1, 3, 2)),
        ChordVoicing("Dm", 1, listOf(-1, -1, 0, 2, 3, 1), listOf(0, 0, 0, 2, 3, 1)),
        ChordVoicing("D7", 1, listOf(-1, -1, 0, 2, 1, 2), listOf(0, 0, 0, 2, 1, 3)),
        ChordVoicing("Dmaj7", 1, listOf(-1, -1, 0, 2, 2, 2), listOf(0, 0, 0, 1, 1, 1), listOf(2)),
        ChordVoicing("Dm7", 1, listOf(-1, -1, 0, 2, 1, 1), listOf(0, 0, 0, 2, 1, 1)),
        ChordVoicing("Dsus4", 1, listOf(-1, -1, 0, 2, 3, 3), listOf(0, 0, 0, 1, 3, 4)),
        ChordVoicing("Dsus2", 1, listOf(-1, -1, 0, 2, 3, 0), listOf(0, 0, 0, 1, 2, 0)),
        ChordVoicing("D11", 1, listOf(-1, -1, 0, 2, 1, 3), listOf(0, 0, 0, 2, 1, 3)),
        ChordVoicing("D/F#", 1, listOf(2, 0, 0, 2, 3, 2), listOf(1, 0, 0, 2, 4, 3)),
        ChordVoicing("E", 1, listOf(0, 2, 2, 1, 0, 0), listOf(0, 2, 3, 1, 0, 0)),
        ChordVoicing("Em", 1, listOf(0, 2, 2, 0, 0, 0), listOf(0, 2, 3, 0, 0, 0)),
        ChordVoicing("E7", 1, listOf(0, 2, 0, 1, 0, 0), listOf(0, 2, 0, 1, 0, 0)),
        ChordVoicing("Em7", 1, listOf(0, 2, 2, 0, 3, 0), listOf(0, 2, 3, 0, 4, 0)),
        ChordVoicing("Emaj7", 1, listOf(0, 2, 1, 1, 0, 0), listOf(0, 3, 1, 2, 0, 0)),
        ChordVoicing("Esus4", 1, listOf(0, 2, 2, 2, 0, 0), listOf(0, 2, 3, 4, 0, 0)),
        ChordVoicing("Esus2", 1, listOf(0, 2, 4, 4, 0, 0), listOf(0, 1, 3, 4, 0, 0)),
        ChordVoicing("E6/9", 1, listOf(0, 2, 2, 1, 2, 2), listOf(0, 2, 3, 1, 4, 4), listOf(2)),
        ChordVoicing("E11", 1, listOf(0, 2, 2, 2, 3, 2), listOf(0, 1, 2, 3, 4, 1), listOf(2)),
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
        ChordVoicing("Gsus2", 1, listOf(3, 0, 0, 0, 3, 3), listOf(2, 0, 0, 0, 3, 4)),
        ChordVoicing("G/B", 1, listOf(-1, 2, 0, 0, 3, 3), listOf(0, 1, 0, 0, 3, 4)),
        ChordVoicing("A", 1, listOf(-1, 0, 2, 2, 2, 0), listOf(0, 0, 1, 2, 3, 0)),
        ChordVoicing("Am", 1, listOf(-1, 0, 2, 2, 1, 0), listOf(0, 0, 2, 3, 1, 0)),
        ChordVoicing("A7", 1, listOf(-1, 0, 2, 0, 2, 0), listOf(0, 0, 2, 0, 3, 0)),
        ChordVoicing("Amaj7", 1, listOf(-1, 0, 2, 1, 2, 0), listOf(0, 0, 2, 1, 3, 0)),
        ChordVoicing("Am7", 1, listOf(-1, 0, 2, 0, 1, 0), listOf(0, 0, 2, 0, 1, 0)),
        ChordVoicing("Asus4", 1, listOf(-1, 0, 2, 2, 3, 0), listOf(0, 0, 1, 2, 3, 0)),
        ChordVoicing("Asus2", 1, listOf(-1, 0, 2, 2, 0, 0), listOf(0, 0, 1, 2, 0, 0)),
        ChordVoicing("A11", 1, listOf(-1, 0, 0, 0, 0, 0), listOf(0, 0, 0, 0, 0, 0)),
        ChordVoicing("Bb", 1, listOf(-1, 1, 3, 3, 3, 1), listOf(0, 1, 2, 3, 4, 1), listOf(1)),
        ChordVoicing("Bbm", 1, listOf(-1, 1, 3, 3, 2, 1), listOf(0, 1, 3, 4, 2, 1), listOf(1)),
        ChordVoicing("Bb7", 1, listOf(-1, 1, 3, 1, 3, 1), listOf(0, 1, 3, 1, 4, 1), listOf(1)),
        ChordVoicing("B", 2, listOf(-1, 2, 4, 4, 4, 2), listOf(0, 1, 2, 3, 4, 1), listOf(2)),
        ChordVoicing("Bm", 2, listOf(-1, 2, 4, 4, 3, 2), listOf(0, 1, 3, 4, 2, 1), listOf(2)),
        ChordVoicing("B7", 1, listOf(-1, 2, 1, 2, 0, 2), listOf(0, 2, 1, 3, 0, 4)),
        ChordVoicing("Bmaj7", 2, listOf(-1, 2, 4, 3, 4, 2), listOf(0, 1, 3, 2, 4, 1), listOf(2)),
        ChordVoicing("Bm7", 2, listOf(-1, 2, 4, 2, 3, 2), listOf(0, 1, 3, 1, 2, 1), listOf(2)),
        ChordVoicing("Bsus4", 2, listOf(-1, 2, 4, 4, 5, 2), listOf(0, 1, 2, 3, 4, 1), listOf(2)),
        ChordVoicing("B11", 2, listOf(-1, 2, 2, 2, 2, 2), listOf(0, 1, 1, 1, 1, 1), listOf(2))
    )

    init {
        for (item in standardChords) {
            cache[normalizeKey(item.chord)] = item
        }
    }

    /**
     * Populates in-memory cache with voicings.
     */
    fun populate(voicings: Collection<ChordVoicing>) {
        for (voicing in voicings) {
            cache[normalizeKey(voicing.chord)] = voicing
        }
    }

    /**
     * Returns all currently cached voicings.
     */
    fun getAllCachedVoicings(): List<ChordVoicing> {
        return cache.values.toList()
    }

    /**
     * Loads chord definitions from assets/guitar_chords.json (falling back to chords_db.json).
     */
    fun loadVoicingsFromAssets(context: Context): List<ChordVoicing> {
        val loaded = mutableListOf<ChordVoicing>()
        val assetNames = listOf("guitar_chords.json", "chords_db.json")
        for (name in assetNames) {
            try {
                val jsonString = context.assets.open(name).bufferedReader().use { it.readText() }
                val voicings = parseVoicingsFromJson(jsonString)
                if (voicings.isNotEmpty()) {
                    populate(voicings)
                    isLoadedFromAssets = true
                    loaded.addAll(voicings)
                    break
                }
            } catch (_: Exception) {
                // Try next asset file
            }
        }
        return loaded
    }

    /**
     * Legacy asset loader invocation.
     */
    fun loadFromAssets(context: Context) {
        if (isLoadedFromAssets) return
        loadVoicingsFromAssets(context)
    }

    /**
     * Flexible parser supporting:
     * 1. Array of chord objects: [{"chord": "E6/9", "baseFret": 1, "frets": [...], "fingers": [...], "barres": [...]}]
     * 2. Tombatossals chords-db nested object: {"chords": {"E": [{"key": "E", "suffix": "69", "positions": [...]}]}}
     */
    fun parseVoicingsFromJson(jsonString: String): List<ChordVoicing> {
        val results = mutableListOf<ChordVoicing>()
        val trimmed = jsonString.trim()
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                parseChordObject(obj)?.let { results.add(it) }
            }
        } else if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            if (root.has("chords")) {
                val chordsObj = root.getJSONObject("chords")
                val keys = chordsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val chordArr = chordsObj.getJSONArray(key)
                    for (i in 0 until chordArr.length()) {
                        val item = chordArr.getJSONObject(i)
                        parseTombatossalsItem(item, results)
                    }
                }
            }
        }
        return results
    }

    private fun parseChordObject(obj: JSONObject): ChordVoicing? {
        val chordName = obj.optString("chord").ifBlank {
            val k = obj.optString("key")
            val s = obj.optString("suffix")
            if (k.isNotBlank()) {
                if (s.equals("major", ignoreCase = true)) k else "$k$s"
            } else ""
        }
        if (chordName.isBlank()) return null

        val baseFret = obj.optInt("baseFret", 1)
        val fretsArray = obj.optJSONArray("frets") ?: return null
        val frets = (0 until fretsArray.length()).map { fretsArray.getInt(it) }

        val fingers = if (obj.has("fingers")) {
            val arr = obj.getJSONArray("fingers")
            (0 until arr.length()).map { arr.getInt(it) }
        } else emptyList()

        val barres = if (obj.has("barres")) {
            val arr = obj.getJSONArray("barres")
            (0 until arr.length()).map { arr.getInt(it) }
        } else emptyList()

        return ChordVoicing(chordName, baseFret, frets, fingers, barres)
    }

    private fun parseTombatossalsItem(item: JSONObject, out: MutableList<ChordVoicing>) {
        val key = item.optString("key")
        val suffix = item.optString("suffix")
        val chordName = if (suffix.equals("major", ignoreCase = true)) key else "$key$suffix"

        val positions = item.optJSONArray("positions") ?: return
        for (p in 0 until positions.length()) {
            val pos = positions.getJSONObject(p)
            val baseFret = pos.optInt("baseFret", 1)
            val fretsArr = pos.optJSONArray("frets") ?: continue
            val frets = (0 until fretsArr.length()).map { fretsArr.getInt(it) }

            val fingers = if (pos.has("fingers")) {
                val arr = pos.getJSONArray("fingers")
                (0 until arr.length()).map { arr.getInt(it) }
            } else emptyList()

            val barres = if (pos.has("barres")) {
                val bVal = pos.opt("barres")
                when (bVal) {
                    is JSONArray -> (0 until bVal.length()).map { bVal.getInt(it) }
                    is Number -> listOf(bVal.toInt())
                    else -> emptyList()
                }
            } else emptyList()

            out.add(ChordVoicing(chordName, baseFret, frets, fingers, barres))
            break // Keep best/primary position for standard chord viewer
        }
    }

    /**
     * Looks up chord voicing with smart alias resolution, enharmonics,
     * slash chords, and intelligent extensions fallback.
     */
    fun getVoicing(rawChord: String, context: Context? = null): ChordVoicing? {
        if (context != null && !isLoadedFromAssets) {
            loadVoicingsFromAssets(context)
        }

        val cleaned = rawChord.trim()
            .trim('[', ']', '(', ')', '{', '}', ',', ';', ':')
            .trim()

        if (cleaned.isBlank()) return null

        // 1. Direct normalized lookup
        val direct = cache[normalizeKey(cleaned)]
        if (direct != null) return direct

        // 2. Alias variations (e.g. E69 <-> E6/9, EM7 <-> Emaj7, Edim <-> Edim7, etc.)
        val aliasNames = generateAliases(cleaned)
        for (alias in aliasNames) {
            cache[normalizeKey(alias)]?.let { return it.copy(chord = cleaned) }
        }

        // 3. Enharmonic equivalent lookup (e.g. C#9 <-> Db9, F#6/9 <-> Gb6/9)
        val enharmonic = getEnharmonic(cleaned)
        if (enharmonic != null) {
            cache[normalizeKey(enharmonic)]?.let { return it.copy(chord = cleaned) }
            // Try aliases of enharmonic
            for (enhAlias in generateAliases(enharmonic)) {
                cache[normalizeKey(enhAlias)]?.let { return it.copy(chord = cleaned) }
            }
        }

        // 4. Slash chord lookup (e.g. D/F# -> D, G/B -> G)
        if (cleaned.contains("/")) {
            val root = cleaned.substringBefore("/")
            getVoicing(root, context)?.let {
                return it.copy(chord = cleaned)
            }
        }

        // 5. Extended chord root fallback (e.g. Cadd9 -> C, Gsus4 -> G, etc.)
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
            .replace(" ", "")
            .replace("-", "")
    }

    private fun generateAliases(chord: String): List<String> {
        val aliases = mutableListOf<String>()

        // 6/9 <-> 69
        if (chord.contains("6/9", ignoreCase = true)) {
            aliases.add(chord.replace("6/9", "69", ignoreCase = true))
        } else if (chord.contains("69", ignoreCase = true)) {
            aliases.add(chord.replace("69", "6/9", ignoreCase = true))
        }

        // M7 / major7 / maj7
        if (chord.contains("maj7", ignoreCase = true)) {
            aliases.add(chord.replace("maj7", "M7", ignoreCase = true))
        } else if (chord.endsWith("M7")) {
            aliases.add(chord.substring(0, chord.length - 2) + "maj7")
        }

        // sus -> sus4
        if (chord.endsWith("sus", ignoreCase = true)) {
            aliases.add(chord + "4")
        }

        // add(9) -> add9
        if (chord.contains("add(9)", ignoreCase = true)) {
            aliases.add(chord.replace("add(9)", "add9", ignoreCase = true))
        }

        // Half diminished m7b5 <-> ø <-> m7-5 <-> m7(b5)
        if (chord.contains("m7b5", ignoreCase = true)) {
            aliases.add(chord.replace("m7b5", "ø", ignoreCase = true))
            aliases.add(chord.replace("m7b5", "m7-5", ignoreCase = true))
            aliases.add(chord.replace("m7b5", "m7(b5)", ignoreCase = true))
        } else if (chord.contains("ø")) {
            aliases.add(chord.replace("ø", "m7b5"))
        } else if (chord.contains("m7-5", ignoreCase = true)) {
            aliases.add(chord.replace("m7-5", "m7b5", ignoreCase = true))
        }

        // Diminished dim <-> dim7 <-> ° <-> 0
        if (chord.contains("dim7", ignoreCase = true)) {
            aliases.add(chord.replace("dim7", "dim", ignoreCase = true))
        } else if (chord.contains("dim", ignoreCase = true)) {
            aliases.add(chord.replace("dim", "dim7", ignoreCase = true))
            aliases.add(chord.replace("dim", "°", ignoreCase = true))
        } else if (chord.contains("°")) {
            aliases.add(chord.replace("°", "dim"))
            aliases.add(chord.replace("°", "dim7"))
        }

        // Augmented aug <-> +
        if (chord.contains("aug", ignoreCase = true)) {
            aliases.add(chord.replace("aug", "+", ignoreCase = true))
        } else if (chord.contains("+")) {
            aliases.add(chord.replace("+", "aug"))
        }

        return aliases
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
