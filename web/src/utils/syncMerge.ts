import { bindLegacySetlists, ensureSongIds, resolveSetlistSong, songIdentity, validateSetlistReferences } from './setlistSongs'
import type { ActiveSongState, WebSetlist } from '../types/gtar'
export interface SyncLibrary { songs: ActiveSongState[]; setlists: WebSetlist[] }
const canonical = (value: unknown): string => JSON.stringify(value, (_key, item) => item && typeof item === 'object' && !Array.isArray(item) ? Object.fromEntries(Object.entries(item).sort(([a], [b]) => a.localeCompare(b))) : item)
const equal = (a: unknown, b: unknown) => canonical(a) === canonical(b)
const nameIdentity = (setlist: WebSetlist) => setlist.name.trim().toLowerCase()

/** Exact IDs and normalized identities form one equivalence class, even across renames. */
function aliases<T extends { id?: string | number }>(items: T[], identity: (item: T) => string) {
  const parent = new Map<string, string>()
  const firstIdentity = new Map<string, string>()
  const root = (id: string): string => {
    const next = parent.get(id)!
    if (next === id) return id
    const resolved = root(next); parent.set(id, resolved); return resolved
  }
  for (const item of items) {
    const id = String(item.id)
    if (!parent.has(id)) parent.set(id, id)
    const key = identity(item), previous = firstIdentity.get(key)
    if (previous !== undefined) parent.set(root(id), root(previous))
    else firstIdentity.set(key, id)
  }
  const primary = new Map<string, string | number>()
  for (const item of items) if (!primary.has(root(String(item.id)))) primary.set(root(String(item.id)), item.id!)
  return new Map(items.map(item => [String(item.id), primary.get(root(String(item.id)))!]))
}
function unionRefs(a: WebSetlist['songs'], b: WebSetlist['songs']) {
  const seen = new Set<string>()
  return [...a, ...b].filter(ref => { const key = ref.id === undefined ? JSON.stringify([ref.title, ref.artist]) : String(ref.id); if (seen.has(key)) return false; seen.add(key); return true })
}
function align(libraries: SyncLibrary[]): SyncLibrary[] {
  const prepared = libraries.map(library => ({ songs: ensureSongIds(library.songs), setlists: library.setlists }))
  const songIds = aliases(prepared.flatMap(library => library.songs), songIdentity)
  const setlistIds = aliases(prepared.flatMap(library => library.setlists), nameIdentity)
  return prepared.map(library => {
    const songs = new Map<string, ActiveSongState>()
    for (const song of library.songs) {
      const id = songIds.get(String(song.id))!
      if (!songs.has(String(id))) songs.set(String(id), { ...song, id })
    }
    const setlists = new Map<string, WebSetlist>()
    for (const setlist of bindLegacySetlists(library.setlists, library.songs)) {
      const id = setlistIds.get(String(setlist.id))!
      const refs = setlist.songs.map(ref => ({ ...ref, id: songIds.get(String(ref.id)) ?? ref.id ?? resolveSetlistSong(ref, [...songs.values()])?.id }))
      const previous = setlists.get(String(id))
      setlists.set(String(id), { ...(previous ?? setlist), id, songs: unionRefs(previous?.songs ?? [], refs) })
    }
    return { songs: [...songs.values()], setlists: bindLegacySetlists([...setlists.values()], [...songs.values()]) }
  })
}
/** Idempotent repair: retain the first record, rewrite references, and union named setlists. */
export function deduplicateLibrary(library: SyncLibrary): SyncLibrary { return align([library])[0] }

function reconcile<T extends { id?: string | number }>(local: T[], remote: T[], base: T[], combine?: (left: T, right: T) => T) {
  const result: T[] = []
  const l = new Map(local.map(item => [String(item.id), item]))
  const r = new Map(remote.map(item => [String(item.id), item]))
  const b = new Map(base.map(item => [String(item.id), item]))
  for (const id of new Set([...l.keys(), ...r.keys(), ...b.keys()])) {
    const left = l.get(id), right = r.get(id), before = b.get(id)
    let chosen: T | undefined
    if (equal(left, right)) chosen = left
    else if (equal(left, before)) chosen = right
    else if (equal(right, before)) chosen = left
    else if (left && right && combine) chosen = combine(left, right)
    else if (!before) chosen = right ?? left
    else throw new Error('Conflicting local and cloud edits. Export both backups and resolve them before syncing.')
    if (chosen) result.push(chosen)
  }
  return result
}
export function mergeSyncLibrary(local: SyncLibrary, remote: SyncLibrary | null, base: SyncLibrary | null): SyncLibrary {
  if (!remote) return deduplicateLibrary(local)
  const [l, r, b] = align([local, remote, base ?? { songs: [], setlists: [] }])
  const songs = reconcile(l.songs, r.songs, b.songs)
  const setlists = bindLegacySetlists(reconcile(l.setlists, r.setlists, b.setlists,
    (left, right) => ({ ...right, id: left.id, songs: unionRefs(left.songs, right.songs) })), songs)
  const errors = validateSetlistReferences(setlists, songs)
  if (errors.length) throw new Error(errors.join('\n'))
  return deduplicateLibrary({ songs, setlists })
}
export function initializeSyncLibrary(local: SyncLibrary, cloud: SyncLibrary): SyncLibrary {
  return mergeSyncLibrary(local, cloud, null)
}
