import type { ActiveSongState, WebSetlist } from '../types/gtar'

export type SongReference = WebSetlist['songs'][number]
export const songIdentity = (song: { title: string; artist?: string }) =>
  JSON.stringify([song.title.trim().toLowerCase(), (song.artist ?? '').trim().toLowerCase()])

export function resolveSetlistSong(ref: SongReference, songs: ActiveSongState[]): ActiveSongState | undefined {
  if (ref.id !== undefined) return songs.find(song => song.id !== undefined && String(song.id) === String(ref.id))
  const matches = songs.filter(song => song.title.trim().toLowerCase() === ref.title.trim().toLowerCase() &&
    (!ref.artist?.trim() || song.artist.trim().toLowerCase() === ref.artist.trim().toLowerCase()))
  return matches.length === 1 ? matches[0] : undefined
}

/** Run at library load/import; saved IDs remain authoritative after edits. */
export function bindLegacySetlists(setlists: WebSetlist[], songs: ActiveSongState[]): WebSetlist[] {
  return setlists.map(setlist => ({ ...setlist, songs: setlist.songs.map(ref => {
    const song = resolveSetlistSong(ref, songs)
    return song ? { ...ref, id: song.id, title: song.title, artist: song.artist } : ref
  }) }))
}

export function validateSetlistReferences(setlists: WebSetlist[], songs: ActiveSongState[]): string[] {
  const errors: string[] = []
  setlists.forEach((setlist, i) => setlist.songs.forEach((ref, j) => {
    if (!resolveSetlistSong(ref, songs)) errors.push(`setlists[${i}].songs[${j}]: song ${ref.id ?? ref.title} is missing or ambiguous`)
  }))
  return errors
}

export function ensureSongIds(songs: ActiveSongState[]): ActiveSongState[] {
  return songs.map(song => song.id === undefined ? { ...song, id: crypto.randomUUID() } : song)
}

export function partitionSongs(songs: ActiveSongState[]) {
  return { active: songs.filter(song => !song.isDeleted), deleted: songs.filter(song => song.isDeleted) }
}

/** Merge by ID, then legacy identity, rebinding imported references when deduplicating. */
export function mergeBackupLibrary(existing: ActiveSongState[], incoming: ActiveSongState[], setlists: WebSetlist[]) {
  const songs = [...existing]
  const remapped = new Map<string, string | number>()
  for (const song of incoming) {
    const match = songs.findIndex(item => String(item.id) === String(song.id))
    const identityMatches = songs.map((item, index) => ({ item, index })).filter(({ item }) => songIdentity(item) === songIdentity(song))
    const uniqueIncomingIdentity = incoming.filter(item => songIdentity(item) === songIdentity(song)).length === 1
    const index = match >= 0 ? match : uniqueIncomingIdentity && identityMatches.length === 1 ? identityMatches[0].index : -1
    if (index >= 0) {
      const id = songs[index].id!
      remapped.set(String(song.id), id)
      songs[index] = { ...songs[index], ...song, id }
    } else songs.push(song)
  }
  const rebound = setlists.map(setlist => ({ ...setlist, songs: setlist.songs.map(ref =>
    ref.id !== undefined && remapped.has(String(ref.id)) ? { ...ref, id: remapped.get(String(ref.id)) } : ref) }))
  const errors = validateSetlistReferences(rebound, songs)
  if (errors.length) throw new Error(errors.join('\n'))
  return { songs, setlists: bindLegacySetlists(rebound, songs) }
}
