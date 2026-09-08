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

/**
 * Parses and validates any GTAR JSON backup, setlist export, or song list
 */
export function parseBackupJson(rawText: string): ParsedBackupResult {
  try {
    const data = JSON.parse(rawText)
    if (!data || typeof data !== 'object') {
      return { isValid: false, isSingleSetlist: false, songs: [], setlists: [], error: 'JSON is empty or invalid.' }
    }

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
        rawContent: s.rawContent || `{title: ${s.title}}\n{artist: ${s.artist || ''}}\n\n`,
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
      rawContent: s.rawContent || s.content || '',
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
      isValid: normalizedSongs.length > 0 || normalizedSetlists.length > 0,
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
