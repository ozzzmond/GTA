import React from 'react'
import type { SongLine } from '../types/gtar'
import { CHORD_TOKEN_REGEX, convertChordProToTwoLine } from '../utils/songParser'

export interface SongLineRendererProps {
  lines: SongLine[]
  fontSizePx: number
  fontFamily?: 'mono' | 'sans' | 'serif'
  onChordClick?: (chord: string) => void
}

/**
 * Splits a chord line text (preserving whitespace) so chord tokens are individually clickable,
 * matching Android detectTapGestures + extractChordAtOffset.
 */
function renderInteractiveChordLine(
  text: string,
  onChordClick?: (chord: string) => void
): React.ReactNode[] {
  const elements: React.ReactNode[] = []
  let i = 0

  while (i < text.length) {
    if (text[i] === ' ' || text[i] === '\t') {
      let spaceStr = ''
      while (i < text.length && (text[i] === ' ' || text[i] === '\t')) {
        spaceStr += text[i]
        i++
      }
      elements.push(<span key={`sp-${i}`}>{spaceStr}</span>)
    } else {
      const start = i
      while (i < text.length && text[i] !== ' ' && text[i] !== '\t') {
        i++
      }
      const rawToken = text.substring(start, i)
      const cleanToken = rawToken.replace(/^[[<({|,–—:;~]+|[\]>)}|,–—:;~]+$/g, '')

      if (cleanToken && CHORD_TOKEN_REGEX.test(cleanToken)) {
        elements.push(
          <span
            key={`chord-${start}`}
            onClick={() => onChordClick?.(cleanToken)}
            className="hover:text-[#2AA198] hover:underline cursor-pointer active:scale-95 transition-colors select-none"
            title={`View ${cleanToken} fretboard diagram`}
          >
            {cleanToken}
          </span>
        )
      } else {
        elements.push(<span key={`tok-${start}`}>{rawToken}</span>)
      }
    }
  }

  return elements
}

/**
 * 1:1 Jetpack Compose RenderSongLine translation from Android SongViewerScreen.kt:
 * - EmptyLine: 20px spacer
 * - SectionHeader: 14px top spacing, [Title] in #8B5CF6 ExtraBold, letterSpacing 0.8px, paddingTop 6px, paddingBottom 4px
 * - ChordLine: Bold in #B58900, letterSpacing 0.8px, paddingTop 4px, paddingBottom 1px
 * - LyricLine: Normal in #EEE8D5, letterSpacing 0.8px, paddingTop 1px, paddingBottom 5px
 * - TabLine: Monospace in #35B8AD, letterSpacing 0.8px, paddingVertical 1.5px
 * - Standalone Progression: Clean floating chord labels with comfortable spacing (no boxes/borders)
 */
export const SongLineRenderer: React.FC<SongLineRendererProps> = ({
  lines,
  fontSizePx,
  fontFamily = 'mono',
  onChordClick,
}) => {
  const fontClass =
    fontFamily === 'serif'
      ? 'stage-serif'
      : fontFamily === 'sans'
      ? 'stage-sans'
      : 'stage-mono'

  return (
    <div
      style={{ fontSize: `${fontSizePx}px` }}
      className="select-text"
    >
      {lines.map((line, idx) => {
        switch (line.type) {
          case 'EMPTY':
            // Spacer(modifier = Modifier.height(20.dp))
            return <div key={idx} style={{ height: '20px' }} />

          case 'SECTION_HEADER':
            // Jetpack Compose SectionHeader:
            // Spacer(14.dp), [${line.title}] in sectionHeader (#8B5CF6), ExtraBold, letterSpacing = 0.8.sp, padding(top = 6.dp, bottom = 4.dp)
            return (
              <div
                key={idx}
                style={{
                  marginTop: '14px',
                  paddingTop: '6px',
                  paddingBottom: '4px',
                }}
              >
                <span
                  style={{
                    fontSize: `${fontSizePx + 1}px`,
                    lineHeight: `${(fontSizePx + 1) * 1.35}px`,
                    letterSpacing: '0.8px',
                    color: '#8B5CF6',
                  }}
                  className={`${fontClass} font-extrabold tracking-wide select-none`}
                >
                  [{line.title}]
                </span>
              </div>
            )

          case 'CHORD_ROW':
            // If standalone progression (e.g. Intro: [A] [F#m] [D] [E] [A] or [G] [A7] [C] [G]):
            // Render as clean, floating chord labels with comfortable spacing (no boxes/borders)
            if (!line.isOverLyric) {
              return (
                <div
                  key={idx}
                  style={{
                    paddingTop: '4px',
                    paddingBottom: '4px',
                  }}
                  className="flex flex-wrap items-center gap-6 sm:gap-8 select-text"
                >
                  {line.chords.filter(Boolean).map((chord, cIdx) => (
                    <span
                      key={cIdx}
                      onClick={() => onChordClick?.(chord)}
                      style={{
                        fontSize: `${fontSizePx}px`,
                        lineHeight: `${fontSizePx * 1.35}px`,
                        letterSpacing: '0.8px',
                        color: '#B58900',
                      }}
                      className={`${fontClass} font-bold hover:text-[#2AA198] hover:underline cursor-pointer active:scale-95 transition-colors select-none`}
                      title={`View ${chord} fretboard diagram`}
                    >
                      {chord}
                    </span>
                  ))}
                </div>
              )
            }

            // If 2-line chord row over lyrics: exact monospace character-column alignment matching Compose
            return (
              <div
                key={idx}
                style={{
                  paddingTop: '4px',
                  paddingBottom: '1px',
                  fontSize: `${fontSizePx}px`,
                  lineHeight: `${fontSizePx * 1.35}px`,
                  letterSpacing: '0.8px',
                  color: '#B58900',
                }}
                className={`${fontClass} font-bold whitespace-pre select-text`}
              >
                {renderInteractiveChordLine(line.raw, onChordClick)}
              </div>
            )

          case 'LYRIC':
            // Jetpack Compose LyricLine:
            // text = line.lyrics, textPrimary (#EEE8D5), letterSpacing = 0.8.sp, padding(top = 1.dp, bottom = 5.dp)
            return (
              <div
                key={idx}
                style={{
                  paddingTop: '1px',
                  paddingBottom: '5px',
                  fontSize: `${fontSizePx}px`,
                  lineHeight: `${fontSizePx * 1.35}px`,
                  letterSpacing: '0.8px',
                  color: '#EEE8D5',
                }}
                className={`${fontClass} font-normal whitespace-pre select-text`}
              >
                {line.lyrics}
              </div>
            )

          case 'TAB':
            // Jetpack Compose TabLine:
            // tabFontSize = (fontSizeSp - 1f).coerceAtLeast(11f), tabLineColor (#35B8AD), letterSpacing = 0.8.sp, padding(vertical = 1.5.dp)
            {
              const tabFontSize = Math.max(11, fontSizePx - 1)
              return (
                <div
                  key={idx}
                  style={{
                    paddingTop: '1.5px',
                    paddingBottom: '1.5px',
                    fontSize: `${tabFontSize}px`,
                    lineHeight: `${tabFontSize * 1.25}px`,
                    letterSpacing: '0.8px',
                    color: '#35B8AD',
                  }}
                  className="stage-mono font-normal whitespace-pre overflow-x-auto select-text"
                >
                  {line.content}
                </div>
              )
            }

          case 'CHORD_PRO': {
            // Stacked chords-over-lyrics without inline brackets
            const [chordLine, lyricLine] = convertChordProToTwoLine(line.raw || '')
            return (
              <div key={idx} className="select-text">
                {chordLine.trim() && (
                  <div
                    style={{
                      paddingTop: '4px',
                      paddingBottom: '1px',
                      fontSize: `${fontSizePx}px`,
                      lineHeight: `${fontSizePx * 1.35}px`,
                      letterSpacing: '0.8px',
                      color: '#B58900',
                    }}
                    className={`${fontClass} font-bold whitespace-pre`}
                  >
                    {renderInteractiveChordLine(chordLine, onChordClick)}
                  </div>
                )}
                {lyricLine && (
                  <div
                    style={{
                      paddingTop: '1px',
                      paddingBottom: '5px',
                      fontSize: `${fontSizePx}px`,
                      lineHeight: `${fontSizePx * 1.35}px`,
                      letterSpacing: '0.8px',
                      color: '#EEE8D5',
                    }}
                    className={`${fontClass} font-normal whitespace-pre`}
                  >
                    {lyricLine}
                  </div>
                )}
              </div>
            )
          }

          default:
            return null
        }
      })}
    </div>
  )
}
