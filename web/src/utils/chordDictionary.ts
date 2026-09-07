/**
 * Guitar Chord Voicings Dictionary matching Android ChordDictionary.kt
 */

export interface ChordVoicing {
  chord: string
  baseFret: number
  frets: number[] // -1 = muted/x, 0 = open, 1..n = fret number
  fingers?: number[] // 0 = none, 1 = index, 2 = middle, 3 = ring, 4 = pinky
  barres?: number[]
}

const standardChords: ChordVoicing[] = [
  // C
  { chord: 'C', baseFret: 1, frets: [-1, 3, 2, 0, 1, 0], fingers: [0, 3, 2, 0, 1, 0] },
  { chord: 'Cm', baseFret: 3, frets: [-1, 3, 5, 5, 4, 3], fingers: [0, 1, 3, 4, 2, 1], barres: [3] },
  { chord: 'C7', baseFret: 1, frets: [-1, 3, 2, 3, 1, 0], fingers: [0, 3, 2, 4, 1, 0] },
  { chord: 'Cmaj7', baseFret: 1, frets: [-1, 3, 2, 0, 0, 0], fingers: [0, 3, 2, 0, 0, 0] },
  { chord: 'Cm7', baseFret: 3, frets: [-1, 3, 5, 3, 4, 3], fingers: [0, 1, 3, 1, 2, 1], barres: [3] },
  { chord: 'Cadd9', baseFret: 1, frets: [-1, 3, 2, 0, 3, 0], fingers: [0, 2, 1, 0, 3, 0] },
  { chord: 'Csus4', baseFret: 1, frets: [-1, 3, 3, 0, 1, 1], fingers: [0, 3, 4, 0, 1, 1] },
  { chord: 'Csus2', baseFret: 1, frets: [-1, 3, 0, 0, 1, 0], fingers: [0, 3, 0, 0, 1, 0] },
  { chord: 'C#', baseFret: 4, frets: [-1, 4, 6, 6, 6, 4], fingers: [0, 1, 2, 3, 4, 1], barres: [4] },
  { chord: 'C#m', baseFret: 4, frets: [-1, 4, 6, 6, 5, 4], fingers: [0, 1, 3, 4, 2, 1], barres: [4] },
  { chord: 'C#7', baseFret: 4, frets: [-1, 4, 6, 4, 6, 4], fingers: [0, 1, 3, 1, 4, 1], barres: [4] },
  { chord: 'C#m7', baseFret: 4, frets: [-1, 4, 6, 4, 5, 4], fingers: [0, 1, 3, 1, 2, 1], barres: [4] },

  // D
  { chord: 'D', baseFret: 1, frets: [-1, -1, 0, 2, 3, 2], fingers: [0, 0, 0, 1, 3, 2] },
  { chord: 'Dm', baseFret: 1, frets: [-1, -1, 0, 2, 3, 1], fingers: [0, 0, 0, 2, 3, 1] },
  { chord: 'D7', baseFret: 1, frets: [-1, -1, 0, 2, 1, 2], fingers: [0, 0, 0, 2, 1, 3] },
  { chord: 'Dmaj7', baseFret: 1, frets: [-1, -1, 0, 2, 2, 2], fingers: [0, 0, 0, 1, 1, 1], barres: [2] },
  { chord: 'Dm7', baseFret: 1, frets: [-1, -1, 0, 2, 1, 1], fingers: [0, 0, 0, 2, 1, 1] },
  { chord: 'Dsus4', baseFret: 1, frets: [-1, -1, 0, 2, 3, 3], fingers: [0, 0, 0, 1, 3, 4] },
  { chord: 'Dsus2', baseFret: 1, frets: [-1, -1, 0, 2, 3, 0], fingers: [0, 0, 0, 1, 2, 0] },
  { chord: 'Dadd9', baseFret: 1, frets: [-1, -1, 0, 2, 5, 2], fingers: [0, 0, 0, 1, 4, 2] },
  { chord: 'D/F#', baseFret: 1, frets: [2, 0, 0, 2, 3, 2], fingers: [1, 0, 0, 2, 4, 3] },
  { chord: 'Eb', baseFret: 1, frets: [-1, -1, 1, 3, 4, 3], fingers: [0, 0, 1, 2, 4, 3] },
  { chord: 'Ebm', baseFret: 1, frets: [-1, -1, 1, 3, 4, 2], fingers: [0, 0, 1, 3, 4, 2] },

  // E
  { chord: 'E', baseFret: 1, frets: [0, 2, 2, 1, 0, 0], fingers: [0, 2, 3, 1, 0, 0] },
  { chord: 'Em', baseFret: 1, frets: [0, 2, 2, 0, 0, 0], fingers: [0, 2, 3, 0, 0, 0] },
  { chord: 'E7', baseFret: 1, frets: [0, 2, 0, 1, 0, 0], fingers: [0, 2, 0, 1, 0, 0] },
  { chord: 'Em7', baseFret: 1, frets: [0, 2, 2, 0, 3, 0], fingers: [0, 2, 3, 0, 4, 0] },
  { chord: 'Emaj7', baseFret: 1, frets: [0, 2, 1, 1, 0, 0], fingers: [0, 3, 1, 2, 0, 0] },
  { chord: 'Esus4', baseFret: 1, frets: [0, 2, 2, 2, 0, 0], fingers: [0, 2, 3, 4, 0, 0] },
  { chord: 'Esus2', baseFret: 1, frets: [0, 2, 4, 4, 0, 0], fingers: [0, 1, 3, 4, 0, 0] },

  // F
  { chord: 'F', baseFret: 1, frets: [1, 3, 3, 2, 1, 1], fingers: [1, 3, 4, 2, 1, 1], barres: [1] },
  { chord: 'Fm', baseFret: 1, frets: [1, 3, 3, 1, 1, 1], fingers: [1, 3, 4, 1, 1, 1], barres: [1] },
  { chord: 'F7', baseFret: 1, frets: [1, 3, 1, 2, 1, 1], fingers: [1, 3, 1, 2, 1, 1], barres: [1] },
  { chord: 'Fmaj7', baseFret: 1, frets: [-1, -1, 3, 2, 1, 0], fingers: [0, 0, 3, 2, 1, 0] },
  { chord: 'F#', baseFret: 2, frets: [2, 4, 4, 3, 2, 2], fingers: [1, 3, 4, 2, 1, 1], barres: [2] },
  { chord: 'F#m', baseFret: 2, frets: [2, 4, 4, 2, 2, 2], fingers: [1, 3, 4, 1, 1, 1], barres: [2] },
  { chord: 'F#7', baseFret: 2, frets: [2, 4, 2, 3, 2, 2], fingers: [1, 3, 1, 2, 1, 1], barres: [2] },
  { chord: 'F#m7', baseFret: 2, frets: [2, 4, 2, 2, 2, 2], fingers: [1, 3, 1, 1, 1, 1], barres: [2] },

  // G
  { chord: 'G', baseFret: 1, frets: [3, 2, 0, 0, 0, 3], fingers: [2, 1, 0, 0, 0, 3] },
  { chord: 'Gm', baseFret: 3, frets: [3, 5, 5, 3, 3, 3], fingers: [1, 3, 4, 1, 1, 1], barres: [3] },
  { chord: 'G7', baseFret: 1, frets: [3, 2, 0, 0, 0, 1], fingers: [3, 2, 0, 0, 0, 1] },
  { chord: 'Gmaj7', baseFret: 1, frets: [3, 2, 0, 0, 0, 2], fingers: [3, 2, 0, 0, 0, 1] },
  { chord: 'Gm7', baseFret: 3, frets: [3, 5, 3, 3, 3, 3], fingers: [1, 3, 1, 1, 1, 1], barres: [3] },
  { chord: 'Gsus4', baseFret: 1, frets: [3, 3, 0, 0, 1, 3], fingers: [3, 4, 0, 0, 1, 2] },
  { chord: 'Gadd9', baseFret: 1, frets: [3, 2, 0, 2, 0, 3], fingers: [2, 1, 0, 3, 0, 4] },
  { chord: 'G/B', baseFret: 1, frets: [-1, 2, 0, 0, 0, 3], fingers: [0, 1, 0, 0, 0, 2] },
  { chord: 'G#', baseFret: 4, frets: [4, 6, 6, 5, 4, 4], fingers: [1, 3, 4, 2, 1, 1], barres: [4] },
  { chord: 'G#m', baseFret: 4, frets: [4, 6, 6, 4, 4, 4], fingers: [1, 3, 4, 1, 1, 1], barres: [4] },

  // A
  { chord: 'A', baseFret: 1, frets: [-1, 0, 2, 2, 2, 0], fingers: [0, 0, 1, 2, 3, 0] },
  { chord: 'Am', baseFret: 1, frets: [-1, 0, 2, 2, 1, 0], fingers: [0, 0, 2, 3, 1, 0] },
  { chord: 'A7', baseFret: 1, frets: [-1, 0, 2, 0, 2, 0], fingers: [0, 0, 2, 0, 3, 0] },
  { chord: 'Amaj7', baseFret: 1, frets: [-1, 0, 2, 1, 2, 0], fingers: [0, 0, 2, 1, 3, 0] },
  { chord: 'Am7', baseFret: 1, frets: [-1, 0, 2, 0, 1, 0], fingers: [0, 0, 2, 0, 1, 0] },
  { chord: 'Asus4', baseFret: 1, frets: [-1, 0, 2, 2, 3, 0], fingers: [0, 0, 1, 2, 3, 0] },
  { chord: 'Asus2', baseFret: 1, frets: [-1, 0, 2, 2, 0, 0], fingers: [0, 0, 1, 2, 0, 0] },
  { chord: 'Aadd9', baseFret: 1, frets: [-1, 0, 2, 4, 2, 0], fingers: [0, 0, 1, 3, 2, 0] },
  { chord: 'A/C#', baseFret: 1, frets: [-1, 4, 2, 2, 2, 0], fingers: [0, 4, 1, 2, 3, 0] },
  { chord: 'Bb', baseFret: 1, frets: [-1, 1, 3, 3, 3, 1], fingers: [0, 1, 2, 3, 4, 1], barres: [1] },
  { chord: 'Bbm', baseFret: 1, frets: [-1, 1, 3, 3, 2, 1], fingers: [0, 1, 3, 4, 2, 1], barres: [1] },

  // B
  { chord: 'B', baseFret: 2, frets: [-1, 2, 4, 4, 4, 2], fingers: [0, 1, 2, 3, 4, 1], barres: [2] },
  { chord: 'Bm', baseFret: 2, frets: [-1, 2, 4, 4, 3, 2], fingers: [0, 1, 3, 4, 2, 1], barres: [2] },
  { chord: 'B7', baseFret: 1, frets: [-1, 2, 1, 2, 0, 2], fingers: [0, 2, 1, 3, 0, 4] },
  { chord: 'Bmaj7', baseFret: 2, frets: [-1, 2, 4, 3, 4, 2], fingers: [0, 1, 3, 2, 4, 1], barres: [2] },
  { chord: 'Bm7', baseFret: 2, frets: [-1, 2, 4, 2, 3, 2], fingers: [0, 1, 3, 1, 2, 1], barres: [2] },
  { chord: 'Bsus4', baseFret: 2, frets: [-1, 2, 4, 4, 5, 2], fingers: [0, 1, 2, 3, 4, 1], barres: [2] },
]

const chordMap = new Map<string, ChordVoicing>()
for (const v of standardChords) {
  chordMap.set(v.chord.toLowerCase(), v)
}

export function getChordVoicing(chordName: string): ChordVoicing | null {
  const clean = chordName.trim()
  if (!clean || clean.toLowerCase() === 'n.c.' || clean.toLowerCase() === 'nc') {
    return null
  }

  // Exact lookup
  const exact = chordMap.get(clean.toLowerCase())
  if (exact) return exact

  // Strip parentheses if any: e.g. "(Am7)" -> "Am7"
  const stripped = clean.replace(/^[[(<]+|[\])>]+$/g, '').trim()
  const match = chordMap.get(stripped.toLowerCase())
  if (match) return match

  return null
}
