/**
 * GTAR Live Stage Companion - JSON Backup & Restore Engine
 * Handles full library backups (songs, setlists, user themes, stage settings)
 * and single-setlist gig imports/exports.
 */

import type { ActiveSongState, WebSetlist } from '../types/gtar'
import { GTAR_APP_VERSION } from '../types/gtar'

export interface FullBackupPayload {
  app: 'GTAR'
  version: string
  exportedAt: string
  exportType: 'FULL_BACKUP'
  themeMode?: string
  customThemeColors?: {
    bgHex: string
    textHex: string
    chordHex: string
    sectionHex: string
  }
  stageSettings?: {
    fontStyle?: string
    fontSizePx?: number
    isTwoColumn?: boolean
    scrollSpeed?: number
  }
  songs: ActiveSongState[]
  setlists: WebSetlist[]
}

export interface SingleSetlistPayload {
  app: 'GTAR'
  version: string
  exportedAt: string
  exportType: 'SINGLE_SETLIST'
  setlist: {
    id: string | number
    name: string
    createdAt?: number
    songs: Array<{
      title: string
      artist?: string
      key?: string
      capo?: string
      bpm?: string
      format?: string
      rawContent?: string
    }>
  }
}

export interface ParsedBackupResult {
  isValid: boolean
  isSingleSetlist: boolean
  singleSetlistName?: string
  songs: ActiveSongState[]
  setlists: WebSetlist[]
  themeMode?: string
  customThemeColors?: any
  stageSettings?: any
  error?: string
}

/**
 * Downloads full backup JSON file: gtar-stage-backup-YYYY-MM-DD.json
 */
export function exportAllDataJson(
  songs: ActiveSongState[],
  setlists: WebSetlist[]
): string {
  const now = new Date()
  const yyyy = now.getFullYear()
  const MM = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  const fileName = `gtar-stage-backup-${yyyy}-${MM}-${dd}.json`

  let themeMode: string | undefined
  let customThemeColors: any
  let stageSettings: any

  try {
    themeMode = localStorage.getItem('gtar_theme_mode') || undefined
    const customRaw = localStorage.getItem('gtar_custom_theme')
    if (customRaw) customThemeColors = JSON.parse(customRaw)

    const fontStyle = localStorage.getItem('gtar_stage_font_style') || undefined
    const fontSize = localStorage.getItem('gtar_stage_font_size')
    const twoCol = localStorage.getItem('gtar_stage_two_column')
    const speed = localStorage.getItem('gtar_stage_scroll_speed')

    stageSettings = {
      fontStyle,
      fontSizePx: fontSize ? parseInt(fontSize, 10) : undefined,
      isTwoColumn: twoCol ? twoCol === 'true' : undefined,
      scrollSpeed: speed ? parseInt(speed, 10) : undefined,
    }
  } catch {}

  const payload: FullBackupPayload = {
    app: 'GTAR',
    version: GTAR_APP_VERSION,
    exportedAt: now.toISOString(),
    exportType: 'FULL_BACKUP',
    themeMode,
    customThemeColors,
    stageSettings,
    songs,
    setlists,
  }

  triggerDownload(JSON.stringify(payload, null, 2), fileName)
  return fileName
}

/**
 * Downloads a single setlist along with its associated chord sheets
 */
export function exportSingleSetlistJson(
  setlist: WebSetlist,
  allSongs: ActiveSongState[]
): string {
  const now = new Date()
  const sanitizedName = (setlist.name || 'Setlist')
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '')
  const fileName = `gtar-setlist-${sanitizedName || 'export'}.json`

  const resolvedSongs = (setlist.songs || []).map((ref) => {
    const matched = allSongs.find(
      (s) =>
        s.title.trim().toLowerCase() === (ref.title || '').trim().toLowerCase() &&
        (!ref.artist || (s.artist || '').trim().toLowerCase() === ref.artist.trim().toLowerCase())
    )
    return {
      title: ref.title,
      artist: ref.artist || matched?.artist || '',
      key: matched?.key || (ref as any).key || 'G',
      capo: matched?.capo || (ref as any).capo || 'No Capo',
      bpm: matched?.bpm || (ref as any).bpm || '120',
      format: matched?.format || 'CHORD_PRO',
      rawContent:
        matched?.rawContent ||
        `{title: ${ref.title}}\n{artist: ${ref.artist || ''}}\n\n[Verse 1]\n`,
    }
  })

  const payload: SingleSetlistPayload = {
    app: 'GTAR',
    version: GTAR_APP_VERSION,
    exportedAt: now.toISOString(),
    exportType: 'SINGLE_SETLIST',
    setlist: {
      id: setlist.id,
      name: setlist.name,
      createdAt: setlist.createdAt || Date.now(),
      songs: resolvedSongs,
    },
  }

  triggerDownload(JSON.stringify(payload, null, 2), fileName)
  return fileName
}

/** Validate every entry before normalization or any caller can mutate library state. */
export function validateBackupEntries(songs: unknown[], setlists: unknown[], embedded = false): string[] {
  const errors: string[] = []
  const object = (value: unknown): value is Record<string, any> => !!value && typeof value === 'object' && !Array.isArray(value)
  const check = (value: unknown, path: string, song: boolean) => {
    if (!object(value)) { errors.push(`${path}: must be an object`); return }
    const required = song ? 'title' : 'name'
    if (typeof value[required] !== 'string' || !value[required].trim()) errors.push(`${path}.${required}: must be a non-empty string`)
    for (const field of ['artist', 'key', 'capo', 'bpm', 'format', 'tags', 'rawContent', 'content']) {
      if (field in value && typeof value[field] !== 'string') errors.push(`${path}.${field}: must be a string`)
    }
    if (song && !('rawContent' in value) && !('content' in value)) errors.push(`${path}.rawContent: provide rawContent or content as a string`)
    if ('id' in value && !(typeof value.id === 'string' && value.id.trim()) && !(typeof value.id === 'number' && Number.isSafeInteger(value.id))) errors.push(`${path}.id: must be a non-empty string or integer`)
    for (const field of ['transposeOffset', 'createdAt', 'lastOpenedAt']) {
      if (field in value && (typeof value[field] !== 'number' || !Number.isFinite(value[field]))) errors.push(`${path}.${field}: must be a finite number`)
    }
    for (const field of ['isFavorite', 'isDeleted']) {
      if (field in value && typeof value[field] !== 'boolean') errors.push(`${path}.${field}: must be a boolean`)
    }
    if ('format' in value && !['CHORD_PRO', 'TWO_LINE', 'PLAIN'].includes(value.format)) errors.push(`${path}.format: unsupported song format`)
  }
  songs.forEach((song, i) => check(song, `songs[${i}]`, true))
  setlists.forEach((sl, i) => {
    const path = `setlists[${i}]`
    check(sl, path, false)
    if (!object(sl)) return
    if (!Array.isArray(sl.songs)) { errors.push(`${path}.songs: must be an array`); return }
    sl.songs.forEach((ref: unknown, j: number) => {
      const refPath = `${path}.songs[${j}]`
      if (embedded) { check(ref, refPath, true); return }
      if (!object(ref)) { errors.push(`${refPath}: must be an object`); return }
      if (typeof ref.title !== 'string' || !ref.title.trim()) errors.push(`${refPath}.title: must be a non-empty string`)
      if ('artist' in ref && typeof ref.artist !== 'string') errors.push(`${refPath}.artist: must be a string`)
      if ('id' in ref && !(typeof ref.id === 'string' && ref.id.trim()) && !(typeof ref.id === 'number' && Number.isSafeInteger(ref.id))) errors.push(`${refPath}.id: must be a non-empty string or integer`)
    })
  })
  return errors
}

/**
 * Parses and validates any GTAR JSON backup, setlist export, or song list
 */
export function parseBackupJson(rawText: string): ParsedBackupResult {
  try {
    const data = JSON.parse(rawText)
    if (!data || typeof data !== 'object') {
      return { isValid: false, isSingleSetlist: false, songs: [], setlists: [], error: 'JSON is empty or invalid.' }
    }

    const single = data.exportType === 'SINGLE_SETLIST' || 'setlist' in data
    const errors: string[] = []
    if (single) {
      errors.push(...validateBackupEntries([], [data.setlist], true))
    } else {
      if (!Array.isArray(data) && !('songs' in data) && !('title' in data)) errors.push('songs: expected a backup songs array or a song object')
      if ('songs' in data && !Array.isArray(data.songs)) errors.push('songs: must be an array')
      if ('setlists' in data && !Array.isArray(data.setlists)) errors.push('setlists: must be an array')
      errors.push(...validateBackupEntries(Array.isArray(data) ? data : Array.isArray(data.songs) ? data.songs : 'title' in data ? [data] : [], Array.isArray(data.setlists) ? data.setlists : []))
    }
    if (errors.length) return { isValid: false, isSingleSetlist: single, songs: [], setlists: [], error: `Backup rejected:\n${errors.map(error => `? ${error}`).join('\n')}` }

    // 1. Single Setlist Export Check
    if (data.exportType === 'SINGLE_SETLIST' || (data.setlist && typeof data.setlist.name === 'string')) {
      const sl = data.setlist || data
      const slName = sl.name || 'Imported Setlist'
      const slSongsRaw: any[] = Array.isArray(sl.songs) ? sl.songs : []

      const extractedSongs: ActiveSongState[] = slSongsRaw.map((s, idx) => ({
        id: Date.now() + idx,
        title: s.title || `Song ${idx + 1}`,
        artist: s.artist || '',
        key: s.key || 'G',
        capo: s.capo || '',
        bpm: s.bpm || '120',
        format: s.format || 'CHORD_PRO',
        transposeOffset: s.transposeOffset || 0,
        rawContent: s.rawContent ?? s.content,
      }))

      const extractedSetlist: WebSetlist = {
        id: `sl_${Date.now()}`,
        name: slName,
        createdAt: sl.createdAt || Date.now(),
        songs: extractedSongs.map((s) => ({
          title: s.title,
          artist: s.artist,
          id: s.id,
        })),
      }

      return {
        isValid: true,
        isSingleSetlist: true,
        singleSetlistName: slName,
        songs: extractedSongs,
        setlists: [extractedSetlist],
      }
    }

    // 2. Full Backup or GTAR Payload Check
    let incomingSongs: any[] = []
    let incomingSetlists: any[] = []

    if (Array.isArray(data.songs)) {
      incomingSongs = data.songs
    } else if (Array.isArray(data)) {
      incomingSongs = data
    } else if (data.title) {
      incomingSongs = [data]
    }

    if (Array.isArray(data.setlists)) {
      incomingSetlists = data.setlists
    }

    const normalizedSongs: ActiveSongState[] = incomingSongs.map((s, idx) => ({
      id: s.id || Date.now() + idx,
      title: s.title || 'Untitled Song',
      artist: s.artist || '',
      key: s.key || 'G',
      capo: s.capo || '',
      bpm: s.bpm || '120',
      format: s.format || 'CHORD_PRO',
      transposeOffset: s.transposeOffset || 0,
      rawContent: s.rawContent ?? s.content ?? '',
    }))

    const normalizedSetlists: WebSetlist[] = incomingSetlists.map((sl, idx) => ({
      id: sl.id || `sl_${Date.now()}_${idx}`,
      name: sl.name || `Setlist ${idx + 1}`,
      createdAt: sl.createdAt || Date.now(),
      songs: Array.isArray(sl.songs)
        ? sl.songs.map((ref: any) => ({
            title: ref.title || '',
            artist: ref.artist || '',
            id: ref.id,
          }))
        : [],
    }))

    return {
      isValid: true,
      isSingleSetlist: false,
      songs: normalizedSongs,
      setlists: normalizedSetlists,
      themeMode: data.themeMode,
      customThemeColors: data.customThemeColors,
      stageSettings: data.stageSettings,
    }
  } catch (err: any) {
    return {
      isValid: false,
      isSingleSetlist: false,
      songs: [],
      setlists: [],
      error: `Failed to parse JSON file: ${err.message}`,
    }
  }
}

/**
 * Triggers a file download in the browser
 */
function triggerDownload(content: string, fileName: string) {
  if (typeof window === 'undefined') return
  const blob = new Blob([content], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
