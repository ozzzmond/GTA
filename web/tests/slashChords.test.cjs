const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const ts = require('typescript')
for (const ext of ['.ts', '.tsx']) require.extensions[ext] = (module, filename) => module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true }
}).outputText, filename)
const { transposeChordToken, transposeChordLine, transposeChordProText } = require('../src/utils/chordTransposer.ts')
const { parseGtarSong } = require('../src/utils/songParser.ts')
const { parseAndFormatSong } = require('../src/utils/chordSheetParser.ts')
const { SongLineRenderer } = require('../src/components/SongLineRenderer.tsx')
const React = require('react')
const { renderToStaticMarkup } = require('react-dom/server')
const sharps = ['C','C#','D','D#','E','F','F#','G','G#','A','A#','B']
const flats = ['C','Db','D','Eb','E','F','Gb','G','Ab','A','Bb','B']

test('root and bass transpose together through every upward/downward chromatic interval', () => {
  const cases = [ ['D/F#',2,'',6,''], ['C/E',0,'',4,''], ['Bb/D',10,'',2,''],
    ['Dm/F',2,'m',5,''], ['F#m/C#',6,'m',1,''], ['Am/Cm',9,'m',0,'m'],
    ['C6/9/G',0,'6/9',7,''], ['B/Cb',11,'',11,''] ]
  for (let interval = -11; interval <= 11; interval++) {
    for (const [input, root, quality, bass, bassQuality] of cases) {
      for (const preferFlats of [false, true]) {
        const scale = preferFlats ? flats : sharps
        const expected = interval === 0 ? input : `${scale[(root + interval + 12) % 12]}${quality}/${scale[(bass + interval + 12) % 12]}${bassQuality}`
        assert.equal(transposeChordToken(input, interval, preferFlats), expected, `${input} ${interval}`)
      }
      if (interval !== 0) {
        const chord = transposeChordToken(input, interval)
        const parsed = parseGtarSong(`[${input}]word`, interval)
        assert.equal(parsed.lines[0].segments[0].chord, chord)
        assert.equal(parsed.lines[0].segments[0].text, 'word')
      }
    }
  }
  assert.equal(transposeChordToken('D/F#', 1, true), 'Eb/G')
  assert.equal(transposeChordToken('C6/9/G', 1), 'C#6/9/G#')
  assert.equal(transposeChordToken('C6/9', 1), 'C#6/9')
})

test('two-line slash transposition preserves following chord columns and inline lyric spacing', () => {
  for (const offset of [-11,-1,1,11]) {
    const original = 'D/F#        C/E         Am/C'
    const line = transposeChordLine(original, offset)
    assert.equal(line.indexOf(transposeChordToken('C/E', offset)), original.indexOf('C/E'))
    assert.equal(line.lastIndexOf(transposeChordToken('Am/C', offset)), original.lastIndexOf('Am/C'))
    const text = transposeChordProText('[D/F#]Sing  [C/E]a-long [Bridge]', offset)
    assert.equal(text, `[${transposeChordToken('D/F#', offset)}]Sing  [${transposeChordToken('C/E', offset)}]a-long [Bridge]`)
  }
  assert.equal(transposeChordToken('Bridge', 1), 'Bridge')
  assert.equal(transposeChordToken('D/F#', NaN), 'D/F#')
})

test('section labels separate adjacent chords and render stable block spacing in every shared view', () => {
  for (const section of ['Intro', 'Verse', 'Chorus', 'Bridge', 'Outro', 'Solo']) {
    for (const separator of ['', ' ', '\n']) {
      const parsed = parseGtarSong(`[${section.toLowerCase()}]${separator}[D/F#]Sing  [C/E]along`, 1)
      assert.equal(parsed.lines[0].type, 'SECTION_HEADER')
      assert.equal(parsed.lines[0].title, section)
      assert.equal(parsed.lines[1].type, 'CHORD_PRO')
      assert.equal(parsed.lines[1].segments[0].chord, 'D#/G')
      assert.equal(parsed.lines[1].segments[0].text, 'Sing  ')
      for (const fontSizePx of [16, 24, 60]) {
        const html = renderToStaticMarkup(React.createElement(SongLineRenderer, { lines: parsed.lines, fontSizePx }))
        assert.match(html, /role="heading" aria-level="3"/)
        assert.match(html, /padding-top:14px;padding-bottom:8px;margin:0/)
        assert.match(html, /overflow-anchor:none/)
        assert.ok(html.indexOf(`[${section}]`) < html.indexOf('D#/G'))
        assert.match(html, /D#\/G/)
      }
    }
  }
})
test('ChordSheet HTML path also separates section markers from slash chords', () => {
  const result = parseAndFormatSong('[Intro][D/F#]Sing [C/E]along', 1)
  assert.match(result.html, /comment/)
  assert.match(result.html, /Intro/)
  assert.match(result.html, /(?:D#|Eb)\/G/)
  assert.doesNotMatch(result.html, /(?:D#|Eb)\/F#/)
})


test('standalone chords preserve source columns and share slash styling', () => {
  const raw = '  Cadd9    A7sus4\tEm7  G/D   C/E'
  const html = renderToStaticMarkup(React.createElement(SongLineRenderer, { lines: parseGtarSong(raw).lines, fontSizePx: 24 }))
  assert.equal(html.replace(/<[^>]*>/g, ''), raw)
  assert.match(html, /white-space:pre/)
  assert.equal((html.match(/class="stage-chord-token cursor-pointer select-none"/g) || []).length, 5)
  assert.doesNotMatch(html, /underline|2AA198/)
})

test('tab charts stay literal during transposition and resume parsing after a boundary', () => {
  const chart = 'Chords: G D Cadd9 F G/D Dsus4\n        G/D     C/E\n        -3-     -X-'
  for (const input of [chart, '{start_of_tab}\n' + chart + '\n{end_of_tab}']) {
    const parsed = parseGtarSong(input + '\n\n[Verse]\n[G/D]Sing', 1)
    assert.deepEqual(parsed.lines.slice(0, 3).map(line => line.type), ['TAB', 'TAB', 'TAB'])
    assert.equal(parsed.lines.slice(0, 3).map(line => line.content).join('\n'), chart)
    const html = renderToStaticMarkup(React.createElement(SongLineRenderer, { lines: parsed.lines.slice(0, 3), fontSizePx: 24 }))
    assert.doesNotMatch(html, /stage-chord-token|cursor-pointer|fretboard/)
    assert.equal(parsed.lines.at(-1).segments[0].chord, 'G#/D#')
  }
})

test('a fret marker line does not swallow the following lyric or chord row', () => {
  assert.deepEqual(parseGtarSong('e|--3--X--|\nG/D C/E\nSing along').lines.map(line => line.type), ['TAB', 'CHORD_ROW', 'LYRIC'])
})
