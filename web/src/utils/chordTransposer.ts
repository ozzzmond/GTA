/**
 * Musical chord and key transposition utilities
 */

const CHROMATIC_SHARPS = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
const CHROMATIC_FLATS = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'Gb', 'G', 'Ab', 'A', 'Bb', 'B']

const NOTE_ALIASES: Record<string, number> = {
  'B#': 0, 'C': 0,
  'C#': 1, 'Db': 1,
  'D': 2,
  'D#': 3, 'Eb': 3,
  'E': 4, 'Fb': 4,
  'E#': 5, 'F': 5,
  'F#': 6, 'Gb': 6,
  'G': 7,
  'G#': 8, 'Ab': 8,
  'A': 9,
  'A#': 10, 'Bb': 10,
  'B': 11, 'Cb': 11,
}

/**
 * Transposes a single musical root note by semitones
 */
export function transposeNote(note: string, semitones: number, preferFlats = false): string {
  const clean = note.trim()
  if (!(clean in NOTE_ALIASES)) return note

  const currentIdx = NOTE_ALIASES[clean]
  const newIdx = ((currentIdx + semitones) % 12 + 12) % 12
  const scale = preferFlats ? CHROMATIC_FLATS : CHROMATIC_SHARPS
  return scale[newIdx]
}

/**
 * Transposes a key signature (e.g. "G", "Am", "F#m", "Bb", "C#m")
 */
export function transposeKey(key: string | null | undefined, semitones: number): string {
  if (!key || semitones === 0) return key || ''
  const trimmed = key.trim()
  const match = trimmed.match(/^([A-Ga-g][#b]?)(.*)$/)
  if (!match) return key

  const root = match[1].charAt(0).toUpperCase() + match[1].slice(1)
  const quality = match[2]
  const preferFlats = root.includes('b') || root === 'F'
  const newRoot = transposeNote(root, semitones, preferFlats)
  return `${newRoot}${quality}`
}

/**
 * Formats semitone offset for display (e.g. "+2", "-1", "0")
 */
export function formatTransposeOffset(offset: number): string {
  if (offset > 0) return `+${offset}`
  return `${offset}`
}

/**
 * Transposes a single chord token, handling slash chords (e.g. "D/F#", "Am7", "C#m7b5")
 */
export function transposeChordToken(chord: string, semitones: number): string {
  if (semitones === 0) return chord

  // Handle slash chords like D/F#
  if (chord.includes('/')) {
    const parts = chord.split('/')
    if (parts.length === 2) {
      return `${transposeChordToken(parts[0], semitones)}/${transposeChordToken(parts[1], semitones)}`
    }
  }

  const match = chord.match(/^([A-Ga-g][#b]?)(.*)$/)
  if (!match) return chord

  const root = match[1].charAt(0).toUpperCase() + match[1].slice(1)
  const suffix = match[2]
  const preferFlats = root.includes('b') || root === 'F'
  const newRoot = transposeNote(root, semitones, preferFlats)

  return `${newRoot}${suffix}`
}

/**
 * Transposes all bracketed chords in ChordPro content
 * e.g. [G]Amazing [D/F#]grace -> [A]Amazing [E/G#]grace
 */
export function transposeChordProText(text: string, semitones: number): string {
  if (semitones === 0) return text

  return text.replace(/\[([A-Ga-g][#b]?[^\]]*)\]/g, (match, chord) => {
    // Avoid transposing section headers inside brackets like [Verse 1]
    if (/^(Intro|Verse|Chorus|Bridge|Pre-Chorus|Post-Chorus|Outro|Solo|Tab)/i.test(chord)) {
      return match
    }
    return `[${transposeChordToken(chord, semitones)}]`
  })
}

/**
 * Transposes a full chord line while preserving column-width alignment.
 * Uses spacing compensation matching Android TransposeEngine.transposeChordLine.
 */
export function transposeChordLine(chordLine: string, semitones: number): string {
  if (semitones === 0 || !chordLine.trim()) return chordLine

  let result = ''
  let i = 0

  while (i < chordLine.length) {
    if (chordLine[i] === ' ' || chordLine[i] === '\t') {
      result += chordLine[i]
      i++
    } else {
      const start = i
      while (i < chordLine.length && chordLine[i] !== ' ' && chordLine[i] !== '\t') {
        i++
      }
      const rawToken = chordLine.substring(start, i)
      const prefix = rawToken.match(/^[[<({|,–—:;~]+/)?.[0] || ''
      const suffix = rawToken.match(/[\]>)}|,–—:;~]+$/)?.[0] || ''
      const cleanToken = rawToken.substring(prefix.length, rawToken.length - suffix.length)

      if (cleanToken && /^[A-G][b#]?(?:m|maj|min|dim|aug|sus[24]?|add[249]|m7b5|M7|[0-9]{1,2}|alt)*(?:\/[A-G][b#]?)?$/i.test(cleanToken)) {
        const transposed = transposeChordToken(cleanToken, semitones)
        const replacement = `${prefix}${transposed}${suffix}`
        result += replacement

        const diff = replacement.length - rawToken.length
        if (diff > 0) {
          // Consume extra spaces
          let spaceCount = 0
          let checkIdx = i
          while (checkIdx < chordLine.length && chordLine[checkIdx] === ' ') {
            spaceCount++
            checkIdx++
          }
          const maxConsumable = Math.max(0, spaceCount - 1)
          const spacesToConsume = Math.min(diff, maxConsumable)
          i += spacesToConsume
        } else if (diff < 0) {
          // Pad spaces to maintain column
          let spaceCount = 0
          let checkIdx = i
          while (checkIdx < chordLine.length && chordLine[checkIdx] === ' ') {
            spaceCount++
            checkIdx++
          }
          if (spaceCount > 1) {
            result += ' '.repeat(-diff)
          }
        }
      } else {
        result += rawToken
      }
    }
  }

  return result
}
