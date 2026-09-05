package com.joel.gta

import com.joel.gta.data.engine.TransposeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransposeEngineTest {

    @Test
    fun testTransposeBasicChords() {
        assertEquals("D", TransposeEngine.transposeChord("C", 2))
        assertEquals("A", TransposeEngine.transposeChord("G", 2))
        assertEquals("F", TransposeEngine.transposeChord("E", 1))
        assertEquals("C", TransposeEngine.transposeChord("B", 1))
        assertEquals("B", TransposeEngine.transposeChord("C", -1))
        assertEquals("C", TransposeEngine.transposeChord("D", -2))
    }

    @Test
    fun testTransposeChordsWithExtensions() {
        assertEquals("Bm7", TransposeEngine.transposeChord("Am7", 2))
        assertEquals("Dadd9", TransposeEngine.transposeChord("Cadd9", 2))
        assertEquals("Esus4", TransposeEngine.transposeChord("Dsus4", 2))
        assertEquals("Gm7b5", TransposeEngine.transposeChord("F#m7b5", 1))
    }

    @Test
    fun testTransposeSlashBassChords() {
        assertEquals("E/G#", TransposeEngine.transposeChord("D/F#", 2))
        assertEquals("A/C#", TransposeEngine.transposeChord("G/B", 2))
    }

    @Test
    fun testTransposeChordLineAlignment() {
        val original = "G        D/F#     Em       C"
        val transposed = TransposeEngine.transposeChordLine(original, 2)
        // G(+2)->A, D/F#(+2)->E/G#, Em(+2)->F#m, C(+2)->D
        assertTrue(transposed.contains("A"))
        assertTrue(transposed.contains("E/G#"))
        assertTrue(transposed.contains("F#m"))
        assertTrue(transposed.contains("D"))

        // Verify index of last chord D is close to C's index (28)
        assertEquals(original.indexOf('C'), transposed.indexOf('D'))
    }

    @Test
    fun testCalculateCapoOptions() {
        val capoOptions = TransposeEngine.calculateCapoOptions("A")
        assertTrue("Should have capo recommendations for key A", capoOptions.isNotEmpty())

        val gShape = capoOptions.find { it.playAsKey == "G" }
        assertTrue("Should find G shape for key A", gShape != null)
        assertEquals(2, gShape?.fret) // Capo 2 with G shapes = Key A
    }

    @Test
    fun testTransposeAngleBracketChords() {
        assertEquals("A", TransposeEngine.transposeChord("<G>", 2))
        assertEquals("E/G#", TransposeEngine.transposeChord("<D/F#>", 2))

        val originalLine = "<G>        <D/F#>     <Em>       <C>"
        val transposed = TransposeEngine.transposeChordLine(originalLine, 2)
        assertTrue(transposed.contains("<A>"))
        assertTrue(transposed.contains("<E/G#>"))
        assertTrue(transposed.contains("<F#m>"))
        assertTrue(transposed.contains("<D>"))
    }
}
