import { mergeBackupLibrary, bindLegacySetlists } from './setlistSongs'
import type { ActiveSongState, WebSetlist } from '../types/gtar'
export interface SyncLibrary { songs: ActiveSongState[]; setlists: WebSetlist[] }
const canonical = (value: unknown): string => JSON.stringify(value, (_key, item) => item && typeof item === 'object' && !Array.isArray(item) ? Object.fromEntries(Object.entries(item).sort(([a], [b]) => a.localeCompare(b))) : item)
const equal = (a: unknown, b: unknown) => canonical(a) === canonical(b)
function reconcile<T extends { id?: string | number }>(local: T[], remote: T[], base: T[]) {
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
    else if (!before && (!left || !right)) chosen = left ?? right
    else throw new Error('Conflicting local and cloud edits. Export both backups and resolve them before syncing.')
    if (chosen) result.push(chosen)
  }
  return result
}
export function mergeSyncLibrary(local: SyncLibrary, remote: SyncLibrary | null, base: SyncLibrary | null): SyncLibrary {
  if (!remote) return local
  const songs = reconcile(local.songs, remote.songs, base?.songs ?? [])
  const setlists = reconcile(local.setlists, remote.setlists, base?.setlists ?? [])
  return mergeBackupLibrary([], songs, bindLegacySetlists(setlists, songs))
}

/** Initial restore uses the same incoming-ID reservation rules as manual backup import. */
export function initializeSyncLibrary(local: SyncLibrary, cloud: SyncLibrary): SyncLibrary {
  const merged = mergeBackupLibrary(local.songs, cloud.songs, cloud.setlists)
  const setlists = [...local.setlists]
  for (const incoming of merged.setlists) {
    const index = setlists.findIndex(item => String(item.id) === String(incoming.id))
    if (index < 0) setlists.push(incoming)
    else setlists[index] = incoming
  }
  return { songs: merged.songs, setlists: bindLegacySetlists(setlists, merged.songs) }
}
