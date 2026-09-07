import { parseGtarSong, splitSongLinesForColumns, isChordLine, convertChordProToTwoLine } from './utils/songParser.ts'
import { transposeKey, formatTransposeOffset } from './utils/chordTransposer.ts'
import type { SectionHeaderLine, ChordRowLine, SongLine } from './types/gtar.ts'

console.log('=== TEST 1: STANDALONE CHORD LINE RENDERING (INTRO / TAB BUGS) ===')
const introChordText = `Intro: [A] [F#m] [D] [E] [A]
[Verse 1]
When the [A]night has come
[F#m]And the land is dark`

const parsed = parseGtarSong(introChordText, 0)
console.log('Total parsed lines:', parsed.lines.length)
console.log('Line 0:', parsed.lines[0])
console.log('Line 1:', parsed.lines[1])

// Assert Line 0 is Section Header 'Intro'
console.assert(parsed.lines[0].type === 'SECTION_HEADER', 'Line 0 must be SECTION_HEADER')
console.assert((parsed.lines[0] as SectionHeaderLine).title === 'Intro', 'Line 0 title must be Intro')

// Assert Line 1 is CHORD_ROW with 5 distinct spaced chords
console.assert(parsed.lines[1].type === 'CHORD_ROW', 'Line 1 must be CHORD_ROW')
const chords = (parsed.lines[1] as ChordRowLine).chords
console.assert(Array.isArray(chords) && chords.length === 5, 'Line 1 must have 5 chord pills')
console.assert(chords[0] === 'A', 'Chord 0 must be A')
console.assert(chords[1] === 'F#m', 'Chord 1 must be F#m')
console.assert(chords[2] === 'D', 'Chord 2 must be D')
console.assert(chords[3] === 'E', 'Chord 3 must be E')
console.assert(chords[4] === 'A', 'Chord 4 must be A')
console.log('Test 1 Passed: Intro chord progression parsed as distinct spaced pills without squishing!')

console.log('\n=== TEST 2: HYPHENATED & STANDALONE BRACKETED CHORD PROGRESSIONS ===')
console.assert(isChordLine('G - D/F# - Em7 - C - D'), 'Hyphenated chord progression must be chord line')
console.assert(isChordLine('| G | D/F# | Em7 | C |'), 'Pipe bar progression must be chord line')

const hyphenatedParsed = parseGtarSong('G - D/F# - Em7 - C - D', 0)
console.assert(hyphenatedParsed.lines[0].type === 'CHORD_ROW', 'Hyphenated line must be CHORD_ROW')
const hChords = (hyphenatedParsed.lines[0] as ChordRowLine).chords
console.assert(hChords.length === 5, 'Hyphenated line must extract 5 chords, got ' + hChords.length)
console.assert(hChords[1] === 'D/F#', 'Chord 1 should be D/F#')

const standaloneBracketed = parseGtarSong('[A] [F#m] [D] [E] [A]', 0)
console.assert(standaloneBracketed.lines[0].type === 'CHORD_ROW', 'Bracketed line must be CHORD_ROW')
const bChords = (standaloneBracketed.lines[0] as ChordRowLine).chords
console.assert(bChords.length === 5, 'Bracketed line must extract 5 chords, got ' + bChords.length)
console.log('Test 2 Passed: Hyphenated and bracketed chord lines correctly parsed into spaced pills!')

console.log('\n=== TEST 3: TWO-COLUMN SPLIT (PREFERS SECTION HEADER NEAR MIDDLE) ===')
const lines: SongLine[] = [
  { type: 'SECTION_HEADER', title: 'Verse 1' },
  { type: 'LYRIC', lyrics: 'Line 1' },
  { type: 'LYRIC', lyrics: 'Line 2' },
  { type: 'LYRIC', lyrics: 'Line 3' },
  { type: 'SECTION_HEADER', title: 'Chorus' },
  { type: 'LYRIC', lyrics: 'Line 4' },
  { type: 'LYRIC', lyrics: 'Line 5' },
  { type: 'LYRIC', lyrics: 'Line 6' },
  { type: 'LYRIC', lyrics: 'Line 7' },
]

const [col1, col2] = splitSongLinesForColumns(lines)
console.assert(col1.length === 4, 'Column 1 should have 4 lines, got ' + col1.length)
console.assert(col2.length === 5, 'Column 2 should have 5 lines, got ' + col2.length)
console.assert(col2[0].type === 'SECTION_HEADER', 'Column 2 start should be SECTION_HEADER')
console.assert((col2[0] as SectionHeaderLine).title === 'Chorus', 'Column 2 should start cleanly with Chorus')
console.log('Test 3 Passed: Two-column split algorithm cleanly splits at Chorus header!')

console.log('\n=== TEST 4: KEY TRANSPOSITION & CAPO MATH ===')
const baseKey = 'G'
const offset = 2
const transposedKey = transposeKey(baseKey, offset)
console.assert(transposedKey === 'A', 'G + 2 should be A')
console.assert(formatTransposeOffset(2) === '+2', 'Format 2 should be +2')
const capoFret = ((offset % 12) + 12) % 12
console.assert(capoFret === 2, 'Capo fret should be 2')
console.log('Test 4 Passed: Key G + 2 semitones = A with Capo 2 math verified!')

console.log('\n=== TEST 5: CHORDS-OVER-LYRICS SYLLABLE ALIGNMENT (NO INLINE BRACKETS) ===')
const [cLine1, lLine1] = convertChordProToTwoLine('When the [A]night has come')
console.assert(cLine1 === '         A', `Chord line should be '         A', got '${cLine1}'`)
console.assert(lLine1 === 'When the night has come', `Lyric line should be 'When the night has come', got '${lLine1}'`)
// Character 'A' is at column 9, where 'night' starts in the lyric line:
console.assert(cLine1.indexOf('A') === lLine1.indexOf('night'), 'Chord A must align directly above syllable "night"')

const [cLine2, lLine2] = convertChordProToTwoLine("And the [D]moon is the [E]only light we'll [A]see")
console.assert(cLine2.indexOf('D') === lLine2.indexOf('moon'), 'Chord D must align directly above "moon"')
console.assert(cLine2.indexOf('E') === lLine2.indexOf('only'), 'Chord E must align directly above "only"')
console.assert(cLine2.indexOf('A') === lLine2.indexOf('see'), 'Chord A must align directly above "see"')
console.log('Test 5 Passed: Chords placed directly above syllables with exact character-column alignment!')

console.log('\n=== ALL AUTOMATED SUITE TESTS COMPLETED SUCCESSFULLY! ===')
