package com.joel.gta.data.parser

import com.joel.gta.data.model.ChordSegment
import com.joel.gta.data.model.ParsedSong
import com.joel.gta.data.model.SongFormat
import com.joel.gta.data.model.SongLine

object SongParser {

    /**
     * Parses raw song text in either 2-line tabs format or ChordPro format.
     */
    fun parse(rawText: String, defaultTitle: String = "Untitled Song"): ParsedSong {
        val lines = rawText.lines()
        var title = defaultTitle
        var artist: String? = null
        var key: String? = null
        var capo: String? = null
        var tempo: String? = null

        val parsedLines = mutableListOf<SongLine>()
        var chordProLineCount = 0
        var twoLineChordCount = 0

        for ((index, rawLine) in lines.withIndex()) {
            val trimmed = rawLine.trim()

            // 1. Empty lines
            if (trimmed.isEmpty()) {
                parsedLines.add(SongLine.EmptyLine)
                continue
            }

            // 2. Check for ChordPro directives {key: value}
            val directiveMatch = ChordRegex.CHORDPRO_DIRECTIVE_REGEX.matchEntire(trimmed)
            if (directiveMatch != null) {
                val tag = directiveMatch.groupValues[1].lowercase()
                val value = directiveMatch.groupValues[2].trim()
                when (tag) {
                    "t", "title" -> title = value
                    "a", "artist", "subtitle", "st" -> artist = value
                    "key" -> key = value
                    "capo" -> capo = value
                    "tempo" -> tempo = value
                    "c", "comment" -> parsedLines.add(SongLine.SectionHeader(value))
                }
                chordProLineCount++
                continue
            }

            // 3. Guitar tablature lines (e|---0-2-3---|)
            if (ChordRegex.TAB_LINE_REGEX.matches(trimmed) && trimmed.length > 5) {
                parsedLines.add(SongLine.TabLine(rawLine))
                continue
            }

            // 4. Section headers like [Verse 1], <Verse 1>, [Chorus], <Chorus>, [Intro], [Solo]
            if (ChordRegex.SECTION_HEADER_REGEX.matches(trimmed) && trimmed.length < 40) {
                val cleanTitle = trimmed.trim('[', ']', '<', '>').trim()
                parsedLines.add(SongLine.SectionHeader(cleanTitle))
                continue
            }

            // 5. Check for bracketed chords (e.g. [G], <G>, <D/F#>, [Am7])
            val bracketedChords = findBracketedChords(rawLine)
            if (bracketedChords.isNotEmpty()) {
                if (hasInlineLyricContent(rawLine, bracketedChords)) {
                    // 5a. Inline ChordPro line (chords embedded within lyrics on the same line)
                    val (chordLine, lyricLine) = convertChordProToTwoLine(rawLine)
                    if (chordLine.isNotBlank()) {
                        parsedLines.add(SongLine.ChordLine(chordLine))
                    }
                    if (lyricLine.isNotBlank()) {
                        parsedLines.add(SongLine.LyricLine(lyricLine))
                    }
                    chordProLineCount++
                } else {
                    // 5b. 2-line standalone chord row with bracketed chords: e.g. "<G>              <A7>"
                    val normalizedChords = normalizeChordLineBrackets(rawLine, bracketedChords)
                    parsedLines.add(SongLine.ChordLine(normalizedChords))
                    twoLineChordCount++
                }
                continue
            }

            // 6. 2-line raw chord row detection (unbracketed: "G              A7")
            if (isChordLine(rawLine)) {
                parsedLines.add(SongLine.ChordLine(rawLine))
                twoLineChordCount++
                continue
            }

            // 7. Regular lyric line (fallback)
            parsedLines.add(SongLine.LyricLine(rawLine))
        }

        val format = when {
            chordProLineCount > twoLineChordCount -> SongFormat.CHORD_PRO
            twoLineChordCount > 0 -> SongFormat.TWO_LINE
            else -> SongFormat.PLAIN
        }

        return ParsedSong(
            title = title,
            artist = artist,
            key = key,
            capo = capo,
            tempo = tempo,
            format = format,
            lines = parsedLines,
            rawText = rawText
        )
    }

    data class BracketedChordMatch(
        val chord: String,
        val startIndex: Int,
        val endIndex: Int,
        val fullMatch: String
    )

    /**
     * Finds all valid bracketed chords (e.g. [G], <G>, <D/F#>, [Am7]) in a line.
     * Ensures non-chords like [Verse 1] or <Chorus> are NOT matched as chords.
     */
    fun findBracketedChords(line: String): List<BracketedChordMatch> {
        return ChordRegex.CHORDPRO_INLINE_REGEX.findAll(line)
            .mapNotNull { match ->
                val chordCandidate = match.groupValues[1].trim()
                if (ChordRegex.CHORD_TOKEN_REGEX.matches(chordCandidate)) {
                    BracketedChordMatch(
                        chord = chordCandidate,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                        fullMatch = match.value
                    )
                } else {
                    null
                }
            }
            .toList()
    }

    /**
     * Determines whether a line with bracketed chords is an Inline ChordPro line
     * (where chords are embedded within lyrics words on the same line)
     * or a 2-Line / Standalone Chord row (where the line consists only of chords, spaces, bars, etc.).
     */
    fun hasInlineLyricContent(line: String, matches: List<BracketedChordMatch>): Boolean {
        val sb = StringBuilder(line)
        for (match in matches.reversed()) {
            sb.delete(match.startIndex, match.endIndex)
        }
        val remainingText = sb.toString()
        return remainingText.any { it.isLetter() }
    }

    /**
     * Normalizes a 2-line chord row with brackets (e.g. "<G>              <A7>")
     * by replacing bracketed chords with clean chord names anchored at their exact start column.
     * This preserves horizontal alignment with the lyric line underneath.
     */
    fun normalizeChordLineBrackets(line: String, matches: List<BracketedChordMatch>): String {
        if (matches.isEmpty()) return line

        val result = StringBuilder()
        var lastIdx = 0

        for (match in matches) {
            val chord = match.chord
            val start = match.startIndex
            val end = match.endIndex

            val prefix = if (start > lastIdx) line.substring(lastIdx, start) else ""
            if (prefix.any { !it.isWhitespace() }) {
                while (result.length < lastIdx) {
                    result.append(' ')
                }
                result.append(prefix)
            }

            while (result.length < start) {
                result.append(' ')
            }

            result.append(chord)
            lastIdx = end
        }

        if (lastIdx < line.length) {
            val suffix = line.substring(lastIdx)
            if (suffix.any { !it.isWhitespace() }) {
                while (result.length < lastIdx) {
                    result.append(' ')
                }
                result.append(suffix)
            }
        }

        return result.toString()
    }

    /**
     * Inspects line tokens to determine if it is a standalone chord row.
     * Supports standard spaced chords, hyphen-separated progressions (e.g. G - D/F# - Em7 - C - D),
     * bar notation (e.g. | G | D/F# | Em7 | C |), and slash/pipe/dash musical separators.
     */
    fun isChordLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false

        // Do not treat guitar tablature lines as chord lines
        if (ChordRegex.TAB_LINE_REGEX.matches(trimmed) || trimmed.contains("---") || trimmed.contains("---|")) {
            return false
        }

        // Split by whitespace
        val rawTokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (rawTokens.isEmpty()) return false

        var chordCount = 0
        var lyricWordCount = 0

        // Sub-split tokens if they are connected by hyphens or pipes without spaces (e.g. "G-D/F#-Em7" or "|G|D|")
        val processedTokens = mutableListOf<String>()
        for (token in rawTokens) {
            val clean = token.trim('(', ')', '[', ']', '<', '>', '{', '}', ',', ';', ':')
            // If the token matches a chord directly, keep it
            if (ChordRegex.CHORD_TOKEN_REGEX.matches(clean)) {
                processedTokens.add(clean)
            } else if (clean.contains('-') || clean.contains('|') || clean.contains('–') || clean.contains('—')) {
                // Split by dash/pipe while respecting slash chords (do not split on '/')
                val parts = clean.split(Regex("[-|–—]")).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.isNotEmpty()) {
                    processedTokens.addAll(parts)
                } else {
                    processedTokens.add(token)
                }
            } else {
                processedTokens.add(token)
            }
        }

        for (token in processedTokens) {
            // Check if token is a musical delimiter/separator
            if (isDelimiterToken(token)) {
                continue // Musical separators like '-', '|', '/', '...', 'x2' do not count as lyrics
            }

            val cleanToken = token.trim('(', ')', '[', ']', '<', '>', ',', '|', '{', '}', '/', '-', '–', '—', ':', ';', '.')
            if (cleanToken.isEmpty()) {
                continue
            }

            if (ChordRegex.CHORD_TOKEN_REGEX.matches(cleanToken)) {
                chordCount++
            } else {
                lyricWordCount++
            }
        }

        if (chordCount == 0) return false

        // If the line consists only of valid chords and musical delimiters/annotations with 0 lyric words:
        if (lyricWordCount == 0) return true

        // If there are words, check if chord ratio >= 65%
        val totalMeaningful = chordCount + lyricWordCount
        return (chordCount.toFloat() / totalMeaningful) >= 0.65f
    }

    private fun isDelimiterToken(token: String): Boolean {
        // Pure punctuation / musical delimiter characters
        if (token.all { it in "-–—|/\\:;,.·~()[]{}%*+^'\"" }) return true

        // Repeat indicators or musical timing (e.g. "x2", "2x", "x4", "4x", "4/4", "3/4", "6/8")
        if (token.matches(Regex("^x?\\d+x?$", RegexOption.IGNORE_CASE))) return true
        if (token.matches(Regex("^\\d+/\\d+$"))) return true

        // Common stage/chord sheet annotations like "(x2)", "(hold)", "(break)", "(stop)", "(fade)", "N.C."
        val stripped = token.trim('(', ')', '[', ']', '<', '>')
        if (stripped.matches(Regex("^(?:x?\\d+x?|hold|break|stop|fade|riff|nc|n\\.c\\.)$", RegexOption.IGNORE_CASE))) {
            return true
        }

        return false
    }

    /**
     * Converts an inline ChordPro line (e.g., "When the [A]night has come" or "When the <A>night has come")
     * into a 2-line pair of chords over lyrics with exact character-column alignment.
     * Brackets [] and <> are removed, chords are placed on line 1 directly above the corresponding
     * lyric syllable/word on line 2.
     */
    fun convertChordProToTwoLine(line: String): Pair<String, String> {
        val chordBuilder = StringBuilder()
        val lyricBuilder = StringBuilder()

        var lastIdx = 0
        val matches = findBracketedChords(line)

        if (matches.isEmpty()) {
            return Pair("", line)
        }

        for (match in matches) {
            val chord = match.chord
            val chordStart = match.startIndex
            val chordEnd = match.endIndex

            // Lyrics segment before this chord
            if (chordStart > lastIdx) {
                val lyricSegment = line.substring(lastIdx, chordStart)
                lyricBuilder.append(lyricSegment)
            }

            // Target column in lyrics where this chord should sit
            var targetCol = lyricBuilder.length

            // Ensure chord does not collide with previous chord on the chord line
            if (chordBuilder.isNotEmpty() && targetCol < chordBuilder.length + 1) {
                val paddingNeeded = (chordBuilder.length + 1) - targetCol
                // Pad lyric line with spaces so syllables stay aligned under wide chords
                repeat(paddingNeeded) { lyricBuilder.append(' ') }
                targetCol = lyricBuilder.length
            }

            // Pad chord line up to target column
            while (chordBuilder.length < targetCol) {
                chordBuilder.append(' ')
            }

            // Append the chord name
            chordBuilder.append(chord)
            lastIdx = chordEnd
        }

        // Append any remaining lyrics after the last chord
        if (lastIdx < line.length) {
            lyricBuilder.append(line.substring(lastIdx))
        }

        return Pair(chordBuilder.toString(), lyricBuilder.toString())
    }

    /**
     * Splits a ChordPro line into segments of chord + lyric syllable/word.
     * Example: "[G]Amazing [D]grace [Em]how sweet" or "<G>Amazing <D>grace"
     */
    private fun parseChordProLine(line: String): List<ChordSegment> {
        val segments = mutableListOf<ChordSegment>()
        val matches = findBracketedChords(line)

        if (matches.isEmpty()) {
            return listOf(ChordSegment(chord = null, text = line))
        }

        var lastIdx = 0
        for (match in matches) {
            val chord = match.chord
            val chordStart = match.startIndex
            val chordEnd = match.endIndex

            // Text before the chord if any
            if (chordStart > lastIdx) {
                val leadingText = line.substring(lastIdx, chordStart)
                segments.add(ChordSegment(chord = null, text = leadingText))
            }

            // Look ahead to find text following this chord until next chord or end of line
            val nextMatchStart = matches.getOrNull(matches.indexOf(match) + 1)?.startIndex ?: line.length
            val associatedText = if (nextMatchStart > chordEnd) {
                line.substring(chordEnd, nextMatchStart)
            } else {
                ""
            }

            segments.add(ChordSegment(chord = chord, text = associatedText))
            lastIdx = nextMatchStart
        }

        return segments
    }

    /**
     * Built-in sample song (Eraserheads - Ang Huling El Bimbo) in 2-line tabs format
     * for instant testing without needing an external file.
     */
    val SAMPLE_SONG_TWO_LINE = """
{title: Ang Huling El Bimbo}
{artist: Eraserheads}
{key: G}
{capo: No Capo}

[Intro]
G  A7  C  G
G  A7  C  G

[Verse 1]
G              A7
Kamukha mo si Paraluman
C                  G
Nung tayo ay bata pa
G              A7
At ang galing-galing mong sumayaw
C               G
Mapa-Boogie man o Cha-Cha

[Chorus]
     Em           G
Magkahawak ang ating kamay
    C             D
At walang kamalay-malay
       Em           G
Na ang huling El Bimbo
       C          D          G
Magtatapos pala sa ating pag-ibig

[Tab]
e|---3---3---0-------3---|
B|---0---2---1-------0---|
G|---0---0---0-------0---|
D|---0---2---2-------0---|
A|---2---0---3-------2---|
E|---3---------------3---|
""".trimIndent()

    /**
     * Built-in sample song in ChordPro format.
     */
    val SAMPLE_SONG_CHORDPRO = """
{title: Stand By Me}
{artist: Ben E. King}
{key: A}

[Intro]
[A]    [F#m]    [D]   [E]   [A]

[Verse 1]
When the [A]night has come
And the [F#m]land is dark
And the [D]moon is the [E]only light we'll [A]see
No, I [A]won't be afraid, oh, I [F#m]won't be afraid
Just as [D]long as you [E]stand, stand by [A]me

[Chorus]
So, darling, darling, [A]stand by me, oh, [F#m]stand by me
Oh, [D]stand, [E]stand by me, [A]stand by me
""".trimIndent()
}
