package com.joel.gta

import com.joel.gta.data.model.SongFormat
import com.joel.gta.data.model.SongLine
import com.joel.gta.data.parser.ChordRegex
import com.joel.gta.data.parser.SongParser
import org.junit.Assert.*
import org.junit.Test

class SongParserTest {

    @Test
    fun testChordRegexValidChords() {
        val validChords = listOf(
            "C", "G", "Em", "Am7", "F#m", "Bb", "Dsus4", "Cadd9",
            "G/B", "D/F#", "F#m7b5", "A#dim7", "B7#9", "N.C.",
            // Extended / Jazz chords, alterations, tensions & compound modifiers
            "G13", "A6", "Dbdim", "Dbdim7", "Cmaj7#11", "G7alt", "Galt",
            "Csus2", "Dsus4", "C6/9", "C69", "G7(b9)", "Cmaj7(#11)", "C7(b9,b13)",
            "Cm(maj7)", "G13/B", "F#m11", "Am9", "Cmaj9", "Cmaj13",
            "C+", "Caug", "Caug7", "C°", "C°7", "Cø", "Cø7", "CΔ7", "C5"
        )
        for (chord in validChords) {
            assertTrue("Expected '$chord' to be a valid chord", ChordRegex.CHORD_TOKEN_REGEX.matches(chord))
        }

        val nonChords = listOf(
            "Kamukha", "Paraluman", "sumayaw", "the", "night",
            "And", "Are", "At", "All", "Be", "But", "Can", "Do", "Day", "Every", "For", "Go"
        )
        for (word in nonChords) {
            assertFalse("Expected '$word' NOT to be a chord", ChordRegex.CHORD_TOKEN_REGEX.matches(word))
        }
    }

    @Test
    fun testIsChordLine() {
        assertTrue(SongParser.isChordLine("G              A7"))
        assertTrue(SongParser.isChordLine("C                  G"))
        assertTrue(SongParser.isChordLine("Em     G       C      D"))
        assertFalse(SongParser.isChordLine("Kamukha mo si Paraluman"))
        assertFalse(SongParser.isChordLine("When the night has come"))
    }

    @Test
    fun testIsChordLineWithDelimiters() {
        // Hyphen separated chord progression (The Intro bug case)
        assertTrue("Hyphen-separated chord progression should be recognized as chord line",
            SongParser.isChordLine("G - D/F# - Em7 - C - D"))
        assertTrue(SongParser.isChordLine("G – D/F# – Em7 – C – D")) // En-dash
        assertTrue(SongParser.isChordLine("G—D/F#—Em7—C—D")) // Em-dash
        assertTrue(SongParser.isChordLine("G-D/F#-Em7-C-D")) // Connected hyphens

        // Pipe bar chord progressions
        assertTrue(SongParser.isChordLine("| G | D/F# | Em7 | C |"))
        assertTrue(SongParser.isChordLine("|: G | D | Em | C :|"))

        // Extended Jazz Chords with delimiters
        assertTrue(SongParser.isChordLine("G13 - A6 - Dbdim"))
        assertTrue(SongParser.isChordLine("| G13 | A6 | Dbdim |"))
        assertTrue(SongParser.isChordLine("F#m7b5 - B7alt - Em9 - A13"))
        assertTrue(SongParser.isChordLine("Cmaj7(#11) - A7(b13) - Dm9 - G13(b9)"))

        // Slashes as delimiters / beat indicators
        assertTrue(SongParser.isChordLine("G / D / Em / C"))
        assertTrue(SongParser.isChordLine("G / / / | C / / /"))

        // Chords with repeat or timing indicators
        assertTrue(SongParser.isChordLine("G - D/F# - Em7 - C (x2)"))
        assertTrue(SongParser.isChordLine("G - D - Em - C (hold)"))

        // Tab lines must NOT be treated as chord line
        assertFalse(SongParser.isChordLine("e|---0-2-3---|"))
        assertFalse(SongParser.isChordLine("e|---3h5---|"))
        assertFalse(SongParser.isChordLine("B|---1-0-----|"))
        assertFalse(SongParser.isChordLine("|---3h5---|"))
    }

    @Test
    fun testIntroWithHyphenSeparatedChordsParsing() {
        val raw = """
            [Intro]
            G - D/F# - Em7 - C - D

            [Verse 1]
            G            D/F#
            I heard that you're settled down
        """.trimIndent()

        val parsed = SongParser.parse(raw, "Test Intro Song")
        assertEquals(SongLine.SectionHeader("Intro"), parsed.lines[0])
        assertTrue("Line under [Intro] should be SongLine.ChordLine", parsed.lines[1] is SongLine.ChordLine)
        val chordLine = parsed.lines[1] as SongLine.ChordLine
        assertEquals("G - D/F# - Em7 - C - D", chordLine.chords)
    }

    @Test
    fun testIntroWithExtendedJazzChordsParsing() {
        val raw = """
            [Intro]
            G13 - A6 - Dbdim

            [Verse 1]
            Cmaj7#11      F#m7b5
            Smooth sailing in the jazz club
        """.trimIndent()

        val parsed = SongParser.parse(raw, "Jazz Intro Song")
        assertEquals(SongLine.SectionHeader("Intro"), parsed.lines[0])
        assertTrue("Line under [Intro] should be SongLine.ChordLine", parsed.lines[1] is SongLine.ChordLine)
        val chordLine = parsed.lines[1] as SongLine.ChordLine
        assertEquals("G13 - A6 - Dbdim", chordLine.chords)

        // Ensure Verse chord line is also a ChordLine (index 4 after EmptyLine and SectionHeader)
        assertTrue("Verse chord line should be SongLine.ChordLine", parsed.lines[4] is SongLine.ChordLine)
        val verseChords = parsed.lines[4] as SongLine.ChordLine
        assertTrue(verseChords.chords.contains("Cmaj7#11"))
        assertTrue(verseChords.chords.contains("F#m7b5"))
    }

    @Test
    fun testCombinedIntroAndChordsOnSingleLine() {
        val raw = """
            [Intro] G - D/F# - Em7 - C - D
            Kamukha mo si Paraluman
        """.trimIndent()

        val parsed = SongParser.parse(raw)
        assertEquals(SongLine.SectionHeader("Intro"), parsed.lines[0])
        assertTrue("Chords should be extracted as SongLine.ChordLine", parsed.lines[1] is SongLine.ChordLine)
        assertEquals("G - D/F# - Em7 - C - D", (parsed.lines[1] as SongLine.ChordLine).chords)
        assertTrue("Lyrics should be SongLine.LyricLine", parsed.lines[2] is SongLine.LyricLine)
    }

    @Test
    fun testParseTwoLineSampleSong() {
        val song = SongParser.parse(SongParser.SAMPLE_SONG_TWO_LINE)
        assertEquals("Ang Huling El Bimbo", song.title)
        assertEquals("Eraserheads", song.artist)
        assertEquals("G", song.key)
        assertEquals(SongFormat.TWO_LINE, song.format)

        val hasChordLines = song.lines.any { it is SongLine.ChordLine }
        val hasLyricLines = song.lines.any { it is SongLine.LyricLine }
        val hasSectionHeaders = song.lines.any { it is SongLine.SectionHeader }
        val hasTabLines = song.lines.any { it is SongLine.TabLine }

        assertTrue("Should contain chord lines", hasChordLines)
        assertTrue("Should contain lyric lines", hasLyricLines)
        assertTrue("Should contain section headers", hasSectionHeaders)
        assertTrue("Should contain tab lines", hasTabLines)
    }

    @Test
    fun testConvertChordProToTwoLine() {
        val (chordLine, lyricLine) = SongParser.convertChordProToTwoLine("When the [A]night has come")
        assertEquals("         A", chordLine)
        assertEquals("When the night has come", lyricLine)

        // Check index of 'A' is exactly index of 'n' in "night" (9)
        assertEquals(9, chordLine.indexOf('A'))
        assertEquals(9, lyricLine.indexOf("night"))
    }

    @Test
    fun testParseChordProSampleSong() {
        val song = SongParser.parse(SongParser.SAMPLE_SONG_CHORDPRO)
        assertEquals("Stand By Me", song.title)
        assertEquals("Ben E. King", song.artist)
        assertEquals("A", song.key)
        assertEquals(SongFormat.CHORD_PRO, song.format)

        // Verifies chords are converted to chords-over-lyrics (ChordLine + LyricLine)
        val chordLines = song.lines.filterIsInstance<SongLine.ChordLine>()
        val lyricLines = song.lines.filterIsInstance<SongLine.LyricLine>()
        assertTrue("Should contain converted ChordLine elements", chordLines.isNotEmpty())
        assertTrue("Should contain clean LyricLine elements", lyricLines.isNotEmpty())

        // Ensure NO raw bracketed chords remain in lyric lines
        for (line in lyricLines) {
            assertFalse("Lyric line '${line.lyrics}' should not contain bracketed chords", line.lyrics.contains("[A]"))
        }
    }

    @Test
    fun testAngleBracketChordsInInlineChordPro() {
        val line = "When the <A>night has come and the <F#m>land is dark"
        val (chordLine, lyricLine) = SongParser.convertChordProToTwoLine(line)

        assertEquals("When the night has come and the land is dark", lyricLine)
        assertEquals(lyricLine.indexOf("night"), chordLine.indexOf('A'))
        assertEquals(lyricLine.indexOf("land"), chordLine.indexOf("F#m"))
        assertFalse("Chord line should not contain angle brackets", chordLine.contains("<") || chordLine.contains(">"))
    }

    @Test
    fun testAngleBracketChordsInTwoLineFormat() {
        val rawSong = """
            {title: Wild Angle Chart}
            <Intro>
            <G>              <A7>
            Kamukha mo si Paraluman
            <C>                  <G>
            Nung tayo ay bata pa
        """.trimIndent()

        val parsed = SongParser.parse(rawSong)
        assertEquals("Wild Angle Chart", parsed.title)
        assertEquals(SongFormat.TWO_LINE, parsed.format)

        val header = parsed.lines.filterIsInstance<SongLine.SectionHeader>().firstOrNull()
        assertNotNull("Should detect <Intro> as section header", header)
        assertEquals("Intro", header?.title)

        val chordLines = parsed.lines.filterIsInstance<SongLine.ChordLine>()
        assertEquals("Should detect 2 chord lines", 2, chordLines.size)

        // Verify normalized chord row 1
        val firstChordRow = chordLines[0].chords
        assertTrue("First chord row should contain G", firstChordRow.contains("G"))
        assertTrue("First chord row should contain A7", firstChordRow.contains("A7"))
        assertFalse("First chord row must not have angle brackets", firstChordRow.contains("<") || firstChordRow.contains(">"))

        // Column 0 should be G (exact anchor of original <G>)
        assertEquals(0, firstChordRow.indexOf('G'))
        // A7 should be anchored at column 17
        assertEquals(17, firstChordRow.indexOf("A7"))

        val lyricLines = parsed.lines.filterIsInstance<SongLine.LyricLine>()
        assertEquals(2, lyricLines.size)
        assertEquals("Kamukha mo si Paraluman", lyricLines[0].lyrics)
    }

    @Test
    fun testBarSeparatedAngleBracketChords() {
        val rawLine = "| <G> | <D/F#> | <Em> | <C> |"
        val matches = SongParser.findBracketedChords(rawLine)
        assertEquals(4, matches.size)
        assertFalse("Bar separated chords line has no lyric words", SongParser.hasInlineLyricContent(rawLine, matches))

        val normalized = SongParser.normalizeChordLineBrackets(rawLine, matches)
        assertTrue(normalized.contains("G"))
        assertTrue(normalized.contains("D/F#"))
        assertTrue(normalized.contains("Em"))
        assertTrue(normalized.contains("C"))
        assertFalse(normalized.contains("<"))
        assertFalse(normalized.contains(">"))
    }
}
