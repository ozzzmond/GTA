package com.joel.gta

import com.joel.gta.data.chord.ChordDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordDictionaryTest {

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

        // Unknown slash chord should fall back to root chord fingering with slash chord label
        val cOverE = ChordDictionary.getVoicing("C/E")
        assertNotNull(cOverE)
        assertEquals("C/E", cOverE?.chord)
        assertEquals(listOf(-1, 3, 2, 0, 1, 0), cOverE?.frets)

        // Enharmonic normalization (e.g. Db -> C#)
        val dbVoicing = ChordDictionary.getVoicing("Db")
        assertNotNull("Db should resolve to C# voicing or match", dbVoicing)
        assertTrue(dbVoicing?.chord == "Db" || dbVoicing?.chord == "C#")
    }
}
