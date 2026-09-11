const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const ts = require('typescript')
require.extensions['.ts'] = (module, filename) => module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
}).outputText, filename)
const { deduplicateLibrary, mergeSyncLibrary } = require('../src/utils/syncMerge.ts')
const { parseBackupJson } = require('../src/utils/jsonBackup.ts')
const fixtures = require('../../tests/fixtures/sync-contract.json')
const parse = value => value === null ? null : parseBackupJson(JSON.stringify(value))
for (const fixture of fixtures) for (const reverse of [false, true]) test(`${fixture.name}; reverse=${reverse}`, () => {
  const local = parse(fixture.local), cloud = parse(fixture.cloud), base = parse(fixture.base)
  if (reverse) { cloud.songs.reverse(); cloud.setlists.reverse() }
  const run = () => mergeSyncLibrary(local, cloud, base)
  if (fixture.conflict) assert.throws(run, /Conflicting/)
  else {
    const result = run()
    assert.deepEqual(result.songs.map(s => s.id).sort(), fixture.songIds)
    if (fixture.setlistIds) assert.deepEqual(result.setlists.map(s => s.id).sort(), fixture.setlistIds)
    if (fixture.chart) {
      assert.equal(result.songs.find(s => s.id === 'A').rawContent, fixture.chart)
      assert.deepEqual(result.setlists[0].songs.map(s => s.id), ['A', 'B'])
    }
  }
})
test('same-name stable songs and setlists are retained without rewriting their IDs', () => {
  const library = parse({ songs: [{id:'a',title:'Same',rawContent:'First'}, {id:'b',title:'Same',rawContent:'Second'}], setlists: [{id:'x',name:'Gig',songs:[{id:'a',title:'Same'}]}, {id:'y',name:'Gig',songs:[{id:'b',title:'Same'}]}] })
  const result = deduplicateLibrary(library)
  assert.equal(result.songs.length, 2)
  assert.equal(result.setlists.length, 2)
  assert.deepEqual(result.setlists.map(s => s.songs[0].id), ['a', 'b'])
})
