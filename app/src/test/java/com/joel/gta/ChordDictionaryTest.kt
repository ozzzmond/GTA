package com.joel.gta

import com.joel.gta.data.chord.ChordDictionary
import com.joel.gta.data.local.entity.ChordVoicingEntity
import com.joel.gta.data.model.ChordVoicing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class ChordDictionaryTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Load guitar_chords.json if running in JVM test environment
            val candidates = listOf(
                File("src/main/assets/guitar_chords.json"),
                File("app/src/main/assets/guitar_chords.json")
            )
            val file = candidates.firstOrNull { it.exists() }
            if (file != null) {
                val json = file.readText()
                val parsed = ChordDictionary.parseVoicingsFromJson(json)
                ChordDictionary.populate(parsed)
            }
        }
    }

    @Test
    fun testStandardChordsVoicing() {
        val cChord = ChordDictionary.getVoicing("C")
        assertNotNull("C chord should be found in dictionary", cChord)
        assertEquals("C", cChord?.chord)
        assertEquals(listOf(-1, 3, 2, 0, 1, 0), cChord?.frets)

        val gChord = ChordDictionary.getVoicing("G")
        assertNotNull(gChord)
        assertEquals("G", gChord?.chord)

        val emChord = ChordDictionary.getVoicing("Em")
        assertNotNull(emChord)
        assertEquals("Em", emChord?.chord)
        assertEquals(listOf(0, 2, 2, 0, 0, 0), emChord?.frets)
    }

    @Test
    fun testRequestedSpecialVoicings() {
        // E6/9
        val e69 = ChordDictionary.getVoicing("E6/9")
        assertNotNull("E6/9 should be present in dictionary", e69)
        assertEquals(listOf(0, 2, 2, 1, 2, 2), e69?.frets)

        // E69 alias
        val e69Alias = ChordDictionary.getVoicing("E69")
        assertNotNull("E69 alias should resolve to 6/9 voicing", e69Alias)
        assertEquals(listOf(0, 2, 2, 1, 2, 2), e69Alias?.frets)

        // D11
        val d11 = ChordDictionary.getVoicing("D11")
        assertNotNull("D11 should be present in dictionary", d11)
        assertEquals(listOf(-1, -1, 0, 2, 1, 3), d11?.frets)

        // A11
        val a11 = ChordDictionary.getVoicing("A11")
        assertNotNull("A11 should be present in dictionary", a11)
        assertEquals(listOf(-1, 0, 0, 0, 0, 0), a11?.frets)

        // B11
        val b11 = ChordDictionary.getVoicing("B11")
        assertNotNull("B11 should be present in dictionary", b11)
        assertEquals(listOf(-1, 2, 2, 2, 2, 2), b11?.frets)
    }

    @Test
    fun testExtendedChordCoverage() {
        // sus2 & sus4
        val dsus2 = ChordDictionary.getVoicing("Dsus2")
        assertNotNull("Dsus2 should exist", dsus2)
        assertEquals(listOf(-1, -1, 0, 2, 3, 0), dsus2?.frets)

        val asus4 = ChordDictionary.getVoicing("Asus4")
        assertNotNull("Asus4 should exist", asus4)
        assertEquals(listOf(-1, 0, 2, 2, 3, 0), asus4?.frets)

        // add9
        val cadd9 = ChordDictionary.getVoicing("Cadd9")
        assertNotNull("Cadd9 should exist", cadd9)
        assertEquals(listOf(-1, 3, 2, 0, 3, 0), cadd9?.frets)

        // 9th
        val c9 = ChordDictionary.getVoicing("C9")
        assertNotNull("C9 should exist", c9)

        // 13th
        val g13 = ChordDictionary.getVoicing("G13")
        assertNotNull("G13 should exist", g13)

        // dim & dim7
        val cdim = ChordDictionary.getVoicing("Cdim")
        assertNotNull("Cdim should exist", cdim)

        // m7b5 (half-diminished)
        val cm7b5 = ChordDictionary.getVoicing("Cm7b5")
        assertNotNull("Cm7b5 should exist", cm7b5)

        // Half diminished alias ø
        val cHalfDim = ChordDictionary.getVoicing("Cø")
        assertNotNull("Cø should resolve to Cm7b5", cHalfDim)
    }

    @Test
    fun testBracketsAndPunctuationNormalization() {
        val chordWithBrackets = ChordDictionary.getVoicing("[Am]")
        assertNotNull(chordWithBrackets)
        assertEquals("Am", chordWithBrackets?.chord)

        val chordWithParen = ChordDictionary.getVoicing("(D7)")
        assertNotNull(chordWithParen)
        assertEquals("D7", chordWithParen?.chord)
    }

    @Test
    fun testEnharmonicsAndSlashChords() {
        val dOverFSharp = ChordDictionary.getVoicing("D/F#")
        assertNotNull(dOverFSharp)
        assertEquals("D/F#", dOverFSharp?.chord)

        val cOverE = ChordDictionary.getVoicing("C/E")
        assertNotNull(cOverE)
        assertEquals("C/E", cOverE?.chord)

        // Enharmonic normalization (e.g. Db -> C#)
        val dbVoicing = ChordDictionary.getVoicing("Db")
        assertNotNull("Db should resolve to C# voicing or match", dbVoicing)
        assertTrue(dbVoicing?.chord == "Db" || dbVoicing?.chord == "C#")

        // C#9 vs Db9
        val db9Voicing = ChordDictionary.getVoicing("Db9")
        assertNotNull("Db9 should resolve or match", db9Voicing)
    }

    @Test
    fun testChordVoicingEntityConversion() {
        val voicing = ChordVoicing(
            chord = "E6/9",
            baseFret = 1,
            frets = listOf(0, 2, 2, 1, 2, 2),
            fingers = listOf(0, 2, 3, 1, 4, 4),
            barres = listOf(2)
        )

        val entity = ChordVoicingEntity.fromChordVoicing(voicing)
        assertEquals("E6/9", entity.chord)
        assertEquals("0,2,2,1,2,2", entity.frets)
        assertEquals("0,2,3,1,4,4", entity.fingers)
        assertEquals("2", entity.barres)

        val restored = entity.toChordVoicing()
        assertEquals(voicing.chord, restored.chord)
        assertEquals(voicing.baseFret, restored.baseFret)
        assertEquals(voicing.frets, restored.frets)
        assertEquals(voicing.fingers, restored.fingers)
        assertEquals(voicing.barres, restored.barres)
    }

    @Test
    fun testTombatossalsJsonParsing() {
        val tombatossalsSample = """
        {
          "chords": {
            "A": [
              {
                "key": "A",
                "suffix": "11",
                "positions": [
                  {
                    "frets": [-1, 0, 0, 0, 0, 0],
                    "fingers": [0, 0, 0, 0, 0, 0],
                    "baseFret": 1,
                    "barres": []
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()

        val parsed = ChordDictionary.parseVoicingsFromJson(tombatossalsSample)
        assertEquals(1, parsed.size)
        assertEquals("A11", parsed[0].chord)
        assertEquals(listOf(-1, 0, 0, 0, 0, 0), parsed[0].frets)
    }
}
