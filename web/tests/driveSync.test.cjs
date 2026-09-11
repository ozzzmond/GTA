const { test } = require('node:test')
const assert = require('node:assert/strict')
const ts = require('typescript')
const fs = require('node:fs')
require.extensions['.ts'] = (module, filename) => module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
}).outputText, filename)
const { pullCloudBackup, pushCloudBackup, clearDriveSession } = require('../src/utils/driveSync.ts')
const { initializeSyncLibrary, mergeSyncLibrary } = require('../src/utils/syncMerge.ts')
const { normalizeBackupSong } = require('../src/utils/jsonBackup.ts')
const { readGoogleSession, validSession, saveGoogleSession } = require('../src/utils/googleAuth.ts')
const song = normalizeBackupSong({ id: 'a', title: 'A', rawContent: 'original' })
const library = { songs: [song], setlists: [] }
const payload = { ...library, app: 'GTAR', exportType: 'FULL_BACKUP', version: '1', exportedAt: new Date().toISOString() }
const json = (data, headers = {}) => new Response(JSON.stringify(data), { headers })
test('cached auth expires without requesting GIS and tolerates blocked storage', () => {
  const session = { token: 'secret', expiresAt: Date.now() + 60000, user: { sub: '1', email: 'a@example.com' } }
  global.sessionStorage = { getItem: () => JSON.stringify(session), removeItem() {}, setItem() {} }
  assert.deepEqual(readGoogleSession(), session)
  assert.equal(validSession({ ...session, expiresAt: 0 }), false)
  global.sessionStorage.getItem = () => { throw Error('blocked') }
  assert.equal(readGoogleSession(), null)
  saveGoogleSession(null)
})
test('three-way merge preserves independent edits, deletions and rejects divergent edits', () => {
  const local = { ...library, songs: [{ ...song, rawContent: 'local' }] }
  assert.equal(mergeSyncLibrary(local, library, library).songs[0].rawContent, 'local')
  assert.equal(mergeSyncLibrary(library, local, library).songs[0].rawContent, 'local')
  assert.equal(mergeSyncLibrary({ songs: [], setlists: [] }, library, library).songs.length, 0)
  assert.throws(() => mergeSyncLibrary(local, { ...library, songs: [{ ...song, rawContent: 'remote' }] }, library), /Conflicting/)
  assert.throws(() => mergeSyncLibrary(library, { songs: [], setlists: [{ id: 's', name: 'S', songs: [{ id: 'missing', title: 'Missing' }] }] }, null), /missing/)
})
test('Drive uses appData only, validates pulls, and conditionally updates', async () => {
  const original = global.fetch
  const file = { id: 'file', version: '1' }
  let calls = []
  global.fetch = async (url, init) => {
    calls.push({ url, init })
    if (url.includes('uploadType')) return json(file)
    if (url.includes('alt=media')) return json(payload, { etag: 'revision-1' })
    const params = new URL(url).searchParams
    if (!params.has('spaces')) { assert.equal(params.get('fields'), 'id,name,version,modifiedTime,md5Checksum'); return json(file) }
    assert.equal(params.get('fields'), 'files(id,name,version,modifiedTime,md5Checksum),nextPageToken')
    assert.equal(params.get('spaces'), 'appDataFolder')
    return json({ files: [file] })
  }
  try {
    await assert.rejects(pushCloudBackup('test', payload), /Download/)
    assert.equal((await pullCloudBackup('test')).isValid, true)
    await pushCloudBackup('test', payload)
    const upload = calls.at(-1)
    assert.equal(upload.init.method, 'PATCH')
    assert.equal(upload.init.headers['If-Match'], 'revision-1')
    assert.match(upload.init.body, /gtar_songbook_sync.json/)
    await pullCloudBackup('test')
    file.version = '2'
    await assert.rejects(pushCloudBackup('test', payload), /Cloud changed/)
    global.fetch = async url => url.includes('alt=media') ? json({ songs: [{ title: 'Bad' }] }) : json({ files: [file] })
    await assert.rejects(pullCloudBackup('test'), /rejected/)
    await assert.rejects(pushCloudBackup('test', payload), /Download/)
  } finally { global.fetch = original; clearDriveSession('test') }
})
test('first upload creates hidden appData file; duplicates and 401 fail safely', async () => {
  const original = global.fetch
  let upload
  global.fetch = async (url, init) => { if (url.includes('uploadType')) { upload = init; return json({ id: 'created', version: '1' }) } return json({ files: [] }) }
  try {
    assert.equal(await pullCloudBackup('new'), null)
    await pushCloudBackup('new', payload)
    assert.equal(upload.method, 'POST')
    assert.match(upload.body, /"parents":\["appDataFolder"\]/)
    global.fetch = async () => json({ files: [{ id: 'a' }, { id: 'b' }] })
    await assert.rejects(pullCloudBackup('new'), /Ambiguous/)
    global.fetch = async () => new Response('', { status: 401 })
    await assert.rejects(pullCloudBackup('new'), error => error.status === 401)
  } finally { global.fetch = original; clearDriveSession('new') }
})

test('revision metadata permits missing ETag, falls back, and caches upload response', async () => {
  const original = global.fetch
  try {
    for (const field of ['version', 'modifiedTime', 'md5Checksum']) {
      let file = { id: 'cloud', [field]: 'first' }
      let uploads = 0
      global.fetch = async (url, init) => {
        const params = new URL(url).searchParams
        if (params.has('uploadType')) {
          assert.equal(params.get('fields'), 'id,name,version,modifiedTime,md5Checksum')
          assert.equal(init.method, 'PATCH')
          assert.match(url, /files\/cloud\?/)
          assert.equal(init.headers['If-Match'], undefined)
          file = { ...file, [field]: `uploaded-${++uploads}` }
          return json(file)
        }
        if (params.has('alt')) return json(payload)
        return params.has('spaces') ? json({ files: [file] }) : json(file)
      }
      await pullCloudBackup(field)
      await pushCloudBackup(field, payload)
      await pushCloudBackup(field, payload) // Must use the new revision, without another pull.
      assert.equal(uploads, 2)
      file = { ...file, [field]: 'external-edit' }
      await assert.rejects(pushCloudBackup(field, payload), /Cloud changed/)
      assert.equal(uploads, 2)
      clearDriveSession(field)
    }
  } finally { global.fetch = original }
})
test('initial restore populates an empty client and merges cloud IDs/setlists over seed data', () => {
  const cloud = { songs: [{ ...song, rawContent: 'cloud' }], setlists: [{ id: 'gig', name: 'Cloud Gig', songs: [{ id: 'a', title: 'A' }] }] }
  assert.equal(initializeSyncLibrary({ songs: [], setlists: [] }, cloud).songs[0].rawContent, 'cloud')
  const restored = initializeSyncLibrary(library, cloud)
  assert.equal(restored.songs.length, 1)
  assert.equal(restored.songs[0].rawContent, 'cloud')
  assert.equal(restored.setlists[0].songs[0].id, 'a')
  const edited = { songs: [...library.songs, { ...song, id: 'during-download', title: 'New song during download' }], setlists: [] }
  assert.equal(mergeSyncLibrary(edited, restored, library).songs.length, 2)
})

test('deduplicated cloud repair refuses a stale guard, then uploads only after a fresh pull', async () => {
  const original = global.fetch
  let version = '1', uploaded
  const duplicate = { ...song, id: 'duplicate' }
  const polluted = { ...payload, songs: [song, duplicate], setlists: [] }
  global.fetch = async (url, init) => {
    const params = new URL(url).searchParams
    const file = { id: 'repair', version }
    if (params.has('uploadType')) { uploaded = init.body; return json(file) }
    if (params.has('alt')) return json(polluted)
    return json(params.has('spaces') ? { files: [file] } : file)
  }
  try {
    const cloud = await pullCloudBackup('repair')
    const repaired = initializeSyncLibrary(library, cloud)
    assert.equal(repaired.songs.length, 1)
    version = '2'
    await assert.rejects(pushCloudBackup('repair', { ...payload, ...repaired }), /Cloud changed/)
    assert.equal(uploaded, undefined)
    const fresh = await pullCloudBackup('repair')
    await pushCloudBackup('repair', { ...payload, ...initializeSyncLibrary(repaired, fresh) })
    assert.ok(uploaded)
    assert.equal(uploaded.includes('"id":"duplicate"'), false)
  } finally { global.fetch = original; clearDriveSession('repair') }
})
