const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const ts = require('typescript')
require.extensions['.ts'] = (module, filename) => module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
}).outputText, filename)
const { deduplicateLibrary, initializeSyncLibrary, mergeSyncLibrary } = require('../src/utils/syncMerge.ts')
const { normalizeBackupSong, parseBackupJson } = require('../src/utils/jsonBackup.ts')
const song = (id, title = 'Song', artist = 'Artist') => normalizeBackupSong({ id, title, artist, rawContent: `content-${id}` })
const ref = song => ({ id: song.id, title: song.title, artist: song.artist })
const list = (id, name, songs) => ({ id, name, songs: songs.map(ref) })

test('cleanup retains primary songs and unions normalized setlists with ordered remapped references', () => {
  const a = song('primary'), b = song('duplicate', ' SONG ', ' artist '), c = song('other', 'Other')
  const input = { songs: [a, b, c], setlists: [list('first', 'Gig', [b, c, a]), list('second', ' GIG ', [a, c])] }
  const clean = deduplicateLibrary(input)
  assert.deepEqual(clean.songs.map(s => s.id), ['primary', 'other'])
  assert.equal(clean.songs[0].rawContent, a.rawContent)
  assert.deepEqual(clean.setlists.map(s => s.id), ['first'])
  assert.deepEqual(clean.setlists[0].songs.map(s => s.id), ['primary', 'other'])
  assert.deepEqual(deduplicateLibrary(clean), clean)
  assert.equal(input.songs.length, 3)
  assert.equal(parseBackupJson(JSON.stringify(clean)).isValid, true)
})

test('independent Android and Web IDs merge 394 identical songs and six setlists without growth', () => {
  const library = prefix => {
    const songs = Array.from({ length: 394 }, (_, i) => song(`${prefix}-${i}`, `Song ${i}`))
    return { songs, setlists: Array.from({ length: 6 }, (_, i) => list(`${prefix}-gig-${i}`, `Gig ${i}`, songs.slice(i * 10, i * 10 + 10))) }
  }
  const local = library('android'), remote = library('web')
  const initial = initializeSyncLibrary(local, remote)
  assert.equal(initial.songs.length, 394)
  assert.equal(initial.setlists.length, 6)
  assert.equal(initial.songs[0].id, local.songs[0].id)
  const again = mergeSyncLibrary(initial, remote, remote)
  assert.deepEqual(again, initial)
  assert.deepEqual(mergeSyncLibrary(again, again, again), again)
})

test('ID matches survive renames; title matches keep different artists separate', () => {
  const a = song('a'), other = song('other-artist', 'Song', 'Other Artist')
  const renamed = { ...a, title: 'Renamed' }
  const merged = initializeSyncLibrary({ songs: [a, other], setlists: [] }, { songs: [renamed], setlists: [] })
  assert.equal(merged.songs.length, 2)
  assert.equal(merged.songs[0].title, 'Renamed')
})

test('membership removals and permanent deletions remain effective after ID reconciliation', () => {
  const a = song('a'), b = song('b', 'B')
  const base = { songs: [a, b], setlists: [list('gig', 'Gig', [a, b])] }
  const local = { songs: [a], setlists: [list('gig', 'Gig', [a])] }
  const merged = mergeSyncLibrary(local, base, base)
  assert.deepEqual(merged.songs.map(s => s.id), ['a'])
  assert.deepEqual(merged.setlists[0].songs.map(s => s.id), ['a'])
})

test('legacy references are repointed when duplicate titles made their original lookup ambiguous', () => {
  const clean = deduplicateLibrary({ songs: [song('a'), song('b')], setlists: [{ id: 'gig', name: 'Gig', songs: [{ title: 'Song', artist: 'Artist' }] }] })
  assert.equal(clean.setlists[0].songs[0].id, 'a')
})
