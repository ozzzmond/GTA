const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), ts = require('typescript')
require.extensions['.ts'] = (module, filename) => module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,filename)
const { openSyncJournal } = require('../src/utils/syncJournal.ts')
const { mergeSyncLibrary } = require('../src/utils/syncMerge.ts')
const base = {songs:[{id:'a',title:'Song',rawContent:'old'}],setlists:[]}
const edited = {songs:[{...base.songs[0],rawContent:'unsent'}],setlists:[]}
function storage() {
  const values = new Map()
  return {
    getItem: k => values.get(k) ?? null,
    setItem: (k, v) => values.set(k, v),
    removeItem: k => values.delete(k),
    key: i => [...values.keys()][i] ?? null,
    get length() { return values.size },
    values
  }
}
function synced(store) { const j=openSyncJournal('account',base,store);j.prepare(base,base);j.acknowledge() }
for (const local of [edited,{songs:[],setlists:[]}]) test(`offline mutation, failed upload and restart: ${local.songs.length}`,()=>{
  const store=storage();synced(store)
  const first=openSyncJournal('account',local,store)
  first.archive(base);first.prepare(local,local) // Upload fails or token expires.
  const restarted=openSyncJournal('account',local,store)
  assert.deepEqual(mergeSyncLibrary(restarted.local,base,restarted.baseline),local)
  assert.ok([...store.values.keys()].some(k=>k.startsWith('gtar_sync_recovery:')))
})
test('acknowledged upload interrupted before local apply resumes from durable pending record',()=>{
  const store=storage();synced(store)
  const j=openSyncJournal('account',base,store);j.prepare(base,edited);j.acknowledge()
  const recovered=openSyncJournal('account',base,store)
  assert.deepEqual(recovered.local,edited)
  assert.deepEqual(recovered.baseline,edited)
})
test('account switches and corrupt or unavailable storage stop before changes',()=>{
  const store=storage();synced(store)
  assert.throws(()=>openSyncJournal('other',edited,store),/another Google account/)
  store.setItem('gtar_sync_v1:account','bad')
  assert.throws(()=>openSyncJournal('account',edited,store))
  assert.throws(()=>openSyncJournal('account',edited,{getItem(){return null},setItem(){throw Error('quota')}}),/quota/)
})

test('atomic device persistence survives a restart and does not partially replace on quota failure',()=>{
  const {persistLibrary,readPersistedLibrary}=require('../src/utils/syncJournal.ts')
  const store=storage()
  persistLibrary(base,store)
  assert.deepEqual(readPersistedLibrary(store),base)
  const blocked={...store,setItem(){throw Error('quota')}}
  assert.throws(()=>persistLibrary(edited,blocked),/quota/)
  assert.deepEqual(readPersistedLibrary(store),base)
})

test('archive prunes older recovery snapshots and tolerates quota errors gracefully',()=>{
  const store=storage()
  const j=openSyncJournal('account',base,store)
  // Archive multiple times
  j.archive(base)
  j.archive(edited)
  j.archive(base)
  const recoveryKeys = [...store.values.keys()].filter(k=>k.startsWith('gtar_sync_recovery:account:'))
  assert.ok(recoveryKeys.length <= 2, `Expected at most 2 recovery snapshots, got ${recoveryKeys.length}`)

  // Storage failure during archive stops sync safely (throws) and preserves the last durable copy
  let throwsOnSet = false
  const quotaStore = {
    ...store,
    setItem(k, v) {
      if (throwsOnSet && k.startsWith('gtar_sync_recovery:')) throw new Error('QuotaExceededError')
      store.setItem(k, v)
    }
  }
  const jQuota = openSyncJournal('account',base,quotaStore)
  throwsOnSet = true
  assert.throws(() => jQuota.archive(base), /Unable to persist recovery snapshot/)
  // Verify that the existing durable recovery copy is still retained
  const retainedKeys = [...store.values.keys()].filter(k=>k.startsWith('gtar_sync_recovery:account:'))
  assert.ok(retainedKeys.length >= 1, 'Last durable recovery copy must be kept')
})

