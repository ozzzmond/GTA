import { useState, useEffect, useMemo } from 'react'
import { LoginWall } from './components/LoginWall'
import { Header } from './components/Header'
import { DesktopEditor } from './components/DesktopEditor'
import { StageView } from './components/StageView'
import { StagePresentationView } from './components/StagePresentationView'
import { SongbookHomeView } from './components/SongbookHomeView'
import { TrashView } from './components/TrashView'
import { JsonBridgeModal } from './components/JsonBridgeModal'
import { KeyPickerModal } from './components/KeyPickerModal'
import { SetlistDrawer } from './components/SetlistDrawer'
import { WebsiteUrlSourceModal } from './components/WebsiteUrlSourceModal'
import { ImportDialogModal } from './components/ImportDialogModal'
import { BackupRestoreDialogModal } from './components/BackupRestoreDialogModal'
import { StageSettingsModal, type SongFontStyleOption } from './components/StageSettingsModal'
import {
  ThemeModal,
  type ThemeMode,
  type CustomThemeColors,
  DEFAULT_CUSTOM_COLORS,
  applyCustomThemeStyles,
} from './components/ThemeModal'
import { BandSyncModal } from './components/BandSyncModal'
import { bandSync } from './utils/bandSync'
import { extractDirectives } from './utils/chordSheetParser'
import type { ActiveSongState } from './types/gtar'
import { GTAR_APP_VERSION, GTAR_DEV_VERSION } from './types/gtar'
import type { FetchedChordSheet } from './utils/onlineSearch'
import { exportAllDataJson } from './utils/jsonBackup'
import { Check, Sparkles } from 'lucide-react'

// Modern GTAR v1.0.42 Default Stage Setlist
const DEFAULT_SETLIST: ActiveSongState[] = [
  {
    id: 1,
    title: 'Stand By Me',
    artist: 'Ben E. King',
    key: 'A',
    capo: 'Capo 2',
    bpm: '118',
    format: 'CHORD_PRO',
    transposeOffset: 0,
    rawContent: `{title: Stand By Me}
{artist: Ben E. King}
{key: A}
{capo: Capo 2}
{tempo: 118}

Intro: [A] [F#m] [D] [E] [A]

[Verse 1]
When the [A]night has come
[F#m]And the land is dark
And the [D]moon is the [E]only light we'll [A]see
No I [A]won't be afraid, no I [F#m]won't be afraid
Just as [D]long as you [E]stand, stand by [A]me

[Chorus]
So darling, darling, [A]stand by me
Oh [F#m]stand by me
Oh [D]stand, [E]stand by me, [A]stand by me

[Verse 2]
If the [A]sky that we look upon
[F#m]Should tumble and fall
Or the [D]mountains should [E]crumble to the [A]sea
I won't [A]cry, I won't cry, no I [F#m]won't shed a tear
Just as [D]long as you [E]stand, stand by [A]me

[Outro]
[A]Whenever you're in trouble, won't you [F#m]stand by me
Oh [D]stand by me, [E]oh stand by [A]me`,
  },
  {
    id: 2,
    title: 'Ang Huling El Bimbo',
    artist: 'Eraserheads',
    key: 'G',
    capo: 'No Capo',
    bpm: '124',
    format: 'TWO_LINE',
    transposeOffset: 0,
    rawContent: `{title: Ang Huling El Bimbo}
{artist: Eraserheads}
{key: G}
{capo: No Capo}
{tempo: 124}

[Intro]
G - A7 - C - G
[G] [A7] [C] [G]

[Verse 1]
Kamukha mo si Paraluman
Nung tayo ay bata pa
At ang galing-galing mong sumayaw
Mapa-Boogie man o Cha-Cha

[Chorus]
Magkahawak ang ating kamay
At walang kamalay-malay
Na ang huling El Bimbo
Ay papunta na sa dulo

[Bridge]
At dahan-dahang lumipas
Ang mga araw at taon
Lumaki tayong dalawa
Naiwan ang kahapon

[Outro]
[G] [A7] [C] [G]
La la la la la la la la la`,
  },
  {
    id: 3,
    title: 'Hotel California',
    artist: 'Eagles',
    key: 'Bm',
    capo: 'Capo 2',
    bpm: '75',
    format: 'CHORD_PRO',
    transposeOffset: 0,
    rawContent: `{title: Hotel California}
{artist: Eagles}
{key: Bm}
{capo: Capo 2}
{tempo: 75}

Intro: [Bm] [F#7] [A] [E] [G] [D] [Em] [F#7]

[Verse 1]
On a [Bm]dark desert highway, [F#7]cool wind in my hair
[A]Warm smell of colitas [E]rising up through the air
[G]Up ahead in the distance, [D]I saw a shimmering light
[Em]My head grew heavy and my sight grew dim, [F#7]I had to stop for the night

[Chorus]
[G]Welcome to the Hotel Cali[D]fornia
Such a [F#7]lovely place, such a [Bm]lovely face
Plenty of [G]room at the Hotel Cali[D]fornia
Any [Em]time of year, you can [F#7]find it here`,
  },
  {
    id: 4,
    title: 'Hallelujah',
    artist: 'Leonard Cohen',
    key: 'C',
    capo: 'No Capo',
    bpm: '56',
    format: 'CHORD_PRO',
    transposeOffset: 0,
    rawContent: `{title: Hallelujah}
{artist: Leonard Cohen}
{key: C}
{capo: No Capo}
{tempo: 56}

Intro: [C] [Am] [C] [Am]

[Verse 1]
Now I've [C]heard there was a [Am]secret chord
That [C]David played, and it [Am]pleased the Lord
But [F]you don't really [G]care for music, [C]do you? [G]
It [C]goes like this, the [F]fourth, the [G]fifth
The [Am]minor fall, the [F]major lift
The [G]baffled king com[E7]posing Halle[Am]lujah

[Chorus]
Halle[F]lujah, Halle[Am]lujah
Halle[F]lujah, Halle[C]lu---[G]--[C]jah`,
  },
]

const DEFAULT_SAMPLE_SETLISTS: WebSetlist[] = [
  {
    id: 'gig-set-1',
    name: 'Acoustic Gig Set',
    songs: [
      { title: 'Stand By Me', artist: 'Ben E. King' },
      { title: 'Ang Huling El Bimbo', artist: 'Eraserheads' },
      { title: 'Hotel California', artist: 'Eagles' },
      { title: 'Hallelujah', artist: 'Leonard Cohen' },
    ],
  },
]

export interface WebSetlist {
  id: string | number
  name: string
  createdAt?: number
  songs: Array<{ title: string; artist?: string; id?: string | number }>
}

function App() {
  const isPresentationRoute =
    typeof window !== 'undefined' &&
    (window.location.pathname.includes('/stage/present') ||
      window.location.search.includes('view=present') ||
      window.location.hash.includes('present'))

  if (isPresentationRoute) {
    return <StagePresentationView />
  }

  // Gated Authentication Wall (persisted in sessionStorage)
  const [isUnlocked, setIsUnlocked] = useState(() => {
    return sessionStorage.getItem('gtar_authenticated') === 'true'
  })

  // View state: Songbook Library Home vs Desktop Editor vs Stage View vs Trash Bin
  const [activeView, setActiveView] = useState<'songbook' | 'editor' | 'stage' | 'trash'>('songbook')

  // Songbook Library of Songs (Strictly separated from setlists, persisted in localStorage)
  const [songs, setSongs] = useState<ActiveSongState[]>(() => {
    try {
      const saved = localStorage.getItem('gtar_songs_store')
      if (saved) {
        const parsed = JSON.parse(saved)
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed
        }
      }
    } catch (e) {
      console.error('Failed to load songs from localStorage', e)
    }
    return DEFAULT_SETLIST
  })

  // Soft-deleted songs (Trash bin, persisted in localStorage)
  const [deletedSongs, setDeletedSongs] = useState<ActiveSongState[]>(() => {
    try {
      const saved = localStorage.getItem('gtar_trash_songs_store')
      if (saved) {
        const parsed = JSON.parse(saved)
        if (Array.isArray(parsed)) {
          return parsed
        }
      }
    } catch (e) {
      console.error('Failed to load trash from localStorage', e)
    }
    return []
  })

  // Custom Setlists (persisted in localStorage)
  const [setlists, setSetlists] = useState<WebSetlist[]>(() => {
    try {
      const saved = localStorage.getItem('gtar_setlists_store')
      if (saved) {
        const parsed = JSON.parse(saved)
        if (Array.isArray(parsed)) {
          return parsed
        }
      }
    } catch (e) {
      console.error('Failed to load setlists from localStorage', e)
    }
    return DEFAULT_SAMPLE_SETLISTS
  })

  // Stage Color Theme (persisted in localStorage)
  const [stageTheme, setStageTheme] = useState<ThemeMode>(() => {
    try {
      const saved = localStorage.getItem('gtar_theme_store') as ThemeMode
      if (saved) return saved
    } catch (e) {
      console.error('Failed to load theme from localStorage', e)
    }
    return 'solarized-dark'
  })

  // Custom Stage Theme Colors (persisted in localStorage)
  const [customThemeColors, setCustomThemeColors] = useState<CustomThemeColors>(() => {
    try {
      const saved = localStorage.getItem('gtar_custom_theme_colors')
      if (saved) {
        return { ...DEFAULT_CUSTOM_COLORS, ...JSON.parse(saved) }
      }
    } catch (e) {
      console.error('Failed to load custom theme colors from localStorage', e)
    }
    return DEFAULT_CUSTOM_COLORS
  })

  const [activeSetlistId, setActiveSetlistId] = useState<string | number | null>(() => {
    try {
      const saved = localStorage.getItem('gtar_active_setlist_id')
      if (saved) return JSON.parse(saved)
    } catch (_) {}
    return 'gig-set-1'
  })
  const [activeSongIndex, setActiveSongIndex] = useState<number>(0)
  const [activeSetlistSongIndex, setActiveSetlistSongIndex] = useState<number>(0)
  const [queueMode, setQueueMode] = useState<'library' | 'setlist'>('library')
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Display Settings (persisted in localStorage)
  const [fontStyle, setFontStyle] = useState<SongFontStyleOption>(() => {
    try {
      const saved = localStorage.getItem('gtar_font_style_store') as SongFontStyleOption
      if (saved) return saved
    } catch (_) {}
    return 'mono'
  })
  const [isTwoColumn, setIsTwoColumn] = useState<boolean>(() => {
    try {
      const saved = localStorage.getItem('gtar_twocolumn_store')
      if (saved !== null) return JSON.parse(saved)
    } catch (_) {}
    return false
  })

  // Save songs to localStorage on any change
  useEffect(() => {
    try {
      localStorage.setItem('gtar_songs_store', JSON.stringify(songs))
    } catch (e) {
      console.error('Failed to persist songs to localStorage', e)
    }
  }, [songs])

  // Save trash to localStorage on any change
  useEffect(() => {
    try {
      localStorage.setItem('gtar_trash_songs_store', JSON.stringify(deletedSongs))
    } catch (e) {
      console.error('Failed to persist trash to localStorage', e)
    }
  }, [deletedSongs])

  // Save setlists to localStorage on any change
  useEffect(() => {
    try {
      localStorage.setItem('gtar_setlists_store', JSON.stringify(setlists))
    } catch (e) {
      console.error('Failed to persist setlists to localStorage', e)
    }
  }, [setlists])

  // Save active setlist ID
  useEffect(() => {
    try {
      localStorage.setItem('gtar_active_setlist_id', JSON.stringify(activeSetlistId))
    } catch (_) {}
  }, [activeSetlistId])

  // Save font style to localStorage
  useEffect(() => {
    try {
      localStorage.setItem('gtar_font_style_store', fontStyle)
    } catch (_) {}
  }, [fontStyle])

  // Save two-column state to localStorage
  useEffect(() => {
    try {
      localStorage.setItem('gtar_twocolumn_store', JSON.stringify(isTwoColumn))
    } catch (_) {}
  }, [isTwoColumn])

  // Apply theme to document.body and persist
  useEffect(() => {
    try {
      localStorage.setItem('gtar_theme_store', stageTheme)
    } catch (_) {}

    document.body.classList.remove(
      'theme-solarized-dark',
      'theme-amber-stage',
      'theme-oled-black',
      'theme-paper-light',
      'theme-custom'
    )
    document.body.classList.add(`theme-${stageTheme}`)

    if (stageTheme === 'custom') {
      applyCustomThemeStyles(customThemeColors)
    }
  }, [stageTheme, customThemeColors])

  // Active Setlist context
  const activeSetlist = useMemo(() => {
    if (!setlists.length) return null
    return (
      setlists.find((sl) => String(sl.id) === String(activeSetlistId)) ||
      setlists[0] ||
      null
    )
  }, [setlists, activeSetlistId])

  // Resolved Setlist Songs
  const activeSetlistSongs: ActiveSongState[] = useMemo(() => {
    if (!activeSetlist) return []
    return activeSetlist.songs.map((ref, idx) => {
      const match = songs.find(
        (s) =>
          s.title.trim().toLowerCase() === ref.title.trim().toLowerCase() &&
          (!ref.artist || (s.artist || '').trim().toLowerCase() === ref.artist.trim().toLowerCase())
      )
      return (
        match || {
          id: (ref.id as number) || Date.now() + idx,
          title: ref.title,
          artist: ref.artist || '',
          key: (ref as any).key || 'G',
          capo: (ref as any).capo || 'No Capo',
          bpm: (ref as any).bpm || '120',
          format: 'CHORD_PRO',
          transposeOffset: 0,
          rawContent: `{title: ${ref.title}}\n{artist: ${ref.artist || ''}}\n\n[Verse 1]\n`,
        }
      )
    })
  }, [activeSetlist, songs])

  const isInSetlistMode =
    queueMode === 'setlist' && Boolean(activeSetlist) && activeSetlistSongs.length > 0

  // Current active song strictly honoring current active scope
  const currentSong = isInSetlistMode
    ? activeSetlistSongs[activeSetlistSongIndex] ||
      activeSetlistSongs[0] ||
      songs[0] ||
      DEFAULT_SETLIST[0]
    : songs[activeSongIndex] || DEFAULT_SETLIST[0]

  // Global Modals
  const [isWebsiteUrlModalOpen, setIsWebsiteUrlModalOpen] = useState(false)
  const [isImportModalOpen, setIsImportModalOpen] = useState(false)
  const [isBackupRestoreModalOpen, setIsBackupRestoreModalOpen] = useState(false)
  const [isStageSettingsModalOpen, setIsStageSettingsModalOpen] = useState(false)
  const [isStageToolsModalOpen, setIsStageToolsModalOpen] = useState(false)
  const [isThemeModalOpen, setIsThemeModalOpen] = useState(false)
  const [isJsonModalOpen, setIsJsonModalOpen] = useState(false)
  const [isHeaderKeyPickerOpen, setIsHeaderKeyPickerOpen] = useState(false)
  const [isSetlistDrawerOpen, setIsSetlistDrawerOpen] = useState(false)
  const [isCheckingUpdates, setIsCheckingUpdates] = useState(false)
  const [showUpdateSuccessModal, setShowUpdateSuccessModal] = useState(false)
  const [toastMessage, setToastMessage] = useState<string | null>(null)
  // True when StageView enters fullscreen or focus mode — hides the global Header
  const [isStagePerformanceMode, setIsStagePerformanceMode] = useState(false)

  // Band Sync: listen to leader song sync events when client
  useEffect(() => {
    const unsub = bandSync.onMessage((msg) => {
      if (bandSync.getRole() === 'CLIENT') {
        if (msg.type === 'SONG_SYNC' && msg.payload) {
          // Auto Route: immediately switch directly to Stage View without needing to open setlist drawer
          setActiveView('stage')
          setIsSetlistDrawerOpen(false)

          const title: string = msg.payload.title || msg.payload.songTitle || ''
          const artist: string = msg.payload.artist || ''
          const queueType: string = (msg.payload.queueType || 'LIBRARY').toUpperCase()
          const queueIndex: number =
            msg.payload.queueIndex ?? msg.payload.setlistIndex ?? msg.payload.songIndex ?? 0
          const transpose: number = msg.payload.transpose ?? msg.payload.transposeOffset ?? 0
          const rawContent: string = msg.payload.rawContent || msg.payload.content || ''

          const normTitle = title.trim().toLowerCase()
          const normArtist = artist.trim().toLowerCase()

          if (queueType === 'SETLIST') {
            setQueueMode('setlist')
            let matched = false

            // Try matching in active setlist songs
            if (activeSetlist && activeSetlistSongs.length > 0) {
              const idx = activeSetlistSongs.findIndex(
                (s) =>
                  s.title.trim().toLowerCase() === normTitle &&
                  (!normArtist || s.artist.trim().toLowerCase() === normArtist)
              )
              if (idx !== -1) {
                setActiveSetlistSongIndex(idx)
                matched = true
              } else if (queueIndex >= 0 && queueIndex < activeSetlistSongs.length) {
                setActiveSetlistSongIndex(queueIndex)
                matched = true
              }
            }

            // If not found in active setlist, check other setlists
            if (!matched) {
              for (const sl of setlists) {
                const slIdx = sl.songs.findIndex(
                  (s: any) =>
                    s.title.trim().toLowerCase() === normTitle &&
                    (!normArtist || (s.artist || '').trim().toLowerCase() === normArtist)
                )
                if (slIdx !== -1) {
                  setActiveSetlistId(sl.id)
                  setActiveSetlistSongIndex(slIdx)
                  matched = true
                  break
                }
              }
            }

            // If still not matched, check songs library
            if (!matched) {
              const libIdx = songs.findIndex(
                (s) =>
                  s.title.trim().toLowerCase() === normTitle &&
                  (!normArtist || s.artist.trim().toLowerCase() === normArtist)
              )
              if (libIdx !== -1) {
                setActiveSongIndex(libIdx)
              } else {
                const effectiveContent = rawContent || `{title: ${title || 'Synced Song'}}\n{artist: ${artist || ''}}\n\n[Verse]\n`
                const newSong: ActiveSongState = {
                  id: Date.now(),
                  title: title || 'Synced Song',
                  artist: artist || '',
                  key: msg.payload.key || 'G',
                  capo: msg.payload.capo || 'No Capo',
                  bpm: msg.payload.bpm || '120',
                  format: msg.payload.format || 'CHORD_PRO',
                  transposeOffset: transpose,
                  rawContent: effectiveContent,
                }
                setSongs((prev) => {
                  const updated = [...prev, newSong]
                  try {
                    localStorage.setItem('gtar_songs_store', JSON.stringify(updated))
                  } catch (_) {}
                  return updated
                })
                setActiveSongIndex(songs.length)
              }
            }
          } else {
            // LIBRARY mode
            setQueueMode('library')
            const libIdx = songs.findIndex(
              (s) =>
                s.title.trim().toLowerCase() === normTitle &&
                (!normArtist || s.artist.trim().toLowerCase() === normArtist)
            )
            if (libIdx !== -1) {
              setActiveSongIndex(libIdx)
            } else {
              const effectiveContent = rawContent || `{title: ${title || 'Synced Song'}}\n{artist: ${artist || ''}}\n\n[Verse]\n`
              const newSong: ActiveSongState = {
                id: Date.now(),
                title: title || 'Synced Song',
                artist: artist || '',
                key: msg.payload.key || 'G',
                capo: msg.payload.capo || 'No Capo',
                bpm: msg.payload.bpm || '120',
                format: msg.payload.format || 'CHORD_PRO',
                transposeOffset: transpose,
                rawContent: effectiveContent,
              }
              setSongs((prev) => {
                const updated = [...prev, newSong]
                try {
                  localStorage.setItem('gtar_songs_store', JSON.stringify(updated))
                } catch (_) {}
                return updated
              })
              setActiveSongIndex(songs.length)
            }
          }

          if (typeof transpose === 'number') {
            handleTransposeChange(transpose)
          }
        } else if (msg.type === 'SETLIST_SYNC' && msg.payload) {
          const incomingSetlistName = msg.payload.setlistName || 'Band Setlist'
          const incomingSongs: any[] = Array.isArray(msg.payload.songs) ? msg.payload.songs : []

          // Smart Merge: do not overwrite or duplicate existing (match title + artist)
          setSongs((prevSongs) => {
            const songMap = new Map(
              prevSongs.map((s) => [
                `${s.title.trim().toLowerCase()}::${(s.artist || '').trim().toLowerCase()}`,
                s,
              ])
            )

            const newSongsToAppend: ActiveSongState[] = []
            for (const item of incomingSongs) {
              const key = `${(item.title || '').trim().toLowerCase()}::${(item.artist || '').trim().toLowerCase()}`
              if (!songMap.has(key)) {
                const newSong: ActiveSongState = {
                  id: Date.now() + Math.floor(Math.random() * 10000) + newSongsToAppend.length,
                  title: item.title || 'Untitled Song',
                  artist: item.artist || '',
                  key: item.key || 'G',
                  capo: item.capo || 'No Capo',
                  bpm: item.bpm || '120',
                  format: item.format || 'CHORD_PRO',
                  transposeOffset: 0,
                  rawContent: item.rawContent || '',
                }
                songMap.set(key, newSong)
                newSongsToAppend.push(newSong)
              }
            }
            const updated = [...prevSongs, ...newSongsToAppend]
            try {
              localStorage.setItem('gtar_songs_store', JSON.stringify(updated))
            } catch (_) {}
            return updated
          })

          // Reconstruct/activate received setlist on Member device immediately
          const newSetlistId = `synced-set-${Date.now()}`
          const syncedSetlist: WebSetlist = {
            id: newSetlistId,
            name: incomingSetlistName,
            songs: incomingSongs.map((s) => ({ title: s.title, artist: s.artist })),
          }

          setSetlists((prevSetlists) => {
            const filtered = prevSetlists.filter(
              (sl) => sl.name.trim().toLowerCase() !== incomingSetlistName.trim().toLowerCase()
            )
            const updated = [...filtered, syncedSetlist]
            try {
              localStorage.setItem('gtar_setlists_store', JSON.stringify(updated))
            } catch (_) {}
            return updated
          })

          setActiveSetlistId(newSetlistId)
          setActiveSetlistSongIndex(0)
          setQueueMode('setlist')
          setActiveView('stage')

          // Toast: "Received new setlist from Leader"
          setToastMessage('Received new setlist from Leader')
          setTimeout(() => setToastMessage(null), 5000)
        }
      }
    })
    return unsub
  }, [songs.length, activeSetlist, activeSetlistSongs, setlists])

  // Transpose handler
  const handleTransposeChange = (newOffset: number) => {
    if (isInSetlistMode) {
      const target = activeSetlistSongs[activeSetlistSongIndex]
      if (target) {
        setSongs((prev) =>
          prev.map((s) => (s.id === target.id ? { ...s, transposeOffset: newOffset } : s))
        )
      }
    } else {
      setSongs((prev) =>
        prev.map((s, idx) => (idx === activeSongIndex ? { ...s, transposeOffset: newOffset } : s))
      )
    }
  }

  // Select a song from library (switches queue scope to full library)
  const handleSelectLibrarySong = (idx: number) => {
    setActiveSongIndex(idx)
    setQueueMode('library')
  }

  // Select a song from a setlist (switches queue scope to active setlist)
  const handleSelectSetlistSong = (setlistId: string | number, songIdx: number) => {
    setActiveSetlistId(setlistId)
    setActiveSetlistSongIndex(songIdx)
    setQueueMode('setlist')
  }

  // Toggle stage playback queue mode between Library and Setlist
  const handleToggleQueueMode = (mode: 'library' | 'setlist') => {
    if (mode === 'setlist') {
      if (!activeSetlistId && setlists.length > 0) {
        setActiveSetlistId(setlists[0].id)
        setActiveSetlistSongIndex(0)
      }
      setQueueMode('setlist')
    } else {
      setQueueMode('library')
    }
  }

  // Direct select active setlist
  const handleSelectSetlist = (setlistId: string | number) => {
    setActiveSetlistId(setlistId)
    setActiveSetlistSongIndex(0)
    setQueueMode('setlist')
  }

  // Reorder song in a setlist
  const handleReorderSetlistSong = (
    setlistId: string | number,
    songIndex: number,
    moveUp: boolean
  ) => {
    setSetlists((prev) =>
      prev.map((sl) => {
        if (sl.id !== setlistId) return sl
        const targetIndex = moveUp ? songIndex - 1 : songIndex + 1
        if (targetIndex < 0 || targetIndex >= sl.songs.length) return sl
        const updatedSongs = [...sl.songs]
        const [moved] = updatedSongs.splice(songIndex, 1)
        updatedSongs.splice(targetIndex, 0, moved)
        return { ...sl, songs: updatedSongs }
      })
    )
    if (activeSetlistId === setlistId) {
      if (activeSetlistSongIndex === songIndex) {
        setActiveSetlistSongIndex(moveUp ? songIndex - 1 : songIndex + 1)
      } else if (activeSetlistSongIndex === (moveUp ? songIndex - 1 : songIndex + 1)) {
        setActiveSetlistSongIndex(songIndex)
      }
    }
  }

  // Remove song from a setlist
  const handleRemoveSetlistSong = (setlistId: string | number, songIndex: number) => {
    setSetlists((prev) =>
      prev.map((sl) => {
        if (sl.id !== setlistId) return sl
        return {
          ...sl,
          songs: sl.songs.filter((_, idx) => idx !== songIndex),
        }
      })
    )
    if (activeSetlistId === setlistId) {
      if (activeSetlistSongIndex >= songIndex && activeSetlistSongIndex > 0) {
        setActiveSetlistSongIndex(activeSetlistSongIndex - 1)
      }
    }
  }

  // Delete setlist
  const handleDeleteSetlist = (setlistId: string | number) => {
    setSetlists((prev) => prev.filter((sl) => sl.id !== setlistId))
    if (activeSetlistId === setlistId) {
      setActiveSetlistId(null)
      setQueueMode('library')
    }
  }

  // Band Leader Action: Push Setlist to Members
  const handlePushSetlistToMembers = (
    targetSetlistId?: string | number
  ): { success: boolean; message: string } => {
    const targetSetlist = targetSetlistId
      ? setlists.find((s) => String(s.id) === String(targetSetlistId)) || activeSetlist || setlists[0]
      : activeSetlist || setlists[0]

    if (bandSync.getState().role !== 'HOST') {
      const msg = 'Followers cannot broadcast setlists. Only the Band Leader can push setlists.'
      setToastMessage(msg)
      setTimeout(() => setToastMessage(null), 4000)
      return { success: false, message: msg }
    }

    if (!targetSetlist || targetSetlist.songs.length === 0) {
      const msg = 'No setlist available to push. Please create or select a setlist first.'
      setToastMessage(msg)
      setTimeout(() => setToastMessage(null), 4000)
      return { success: false, message: msg }
    }

    // Resolve complete song data from library
    const payloadSongs = targetSetlist.songs.map((ref) => {
      const matched = songs.find(
        (s) =>
          s.title.trim().toLowerCase() === ref.title.trim().toLowerCase() &&
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

    bandSync.broadcastSetlist(targetSetlist.name, payloadSongs)
    const successMsg = `Pushed setlist '${targetSetlist.name}' (${payloadSongs.length} songs) to band members via BandSync!`
    setToastMessage(successMsg)
    setTimeout(() => setToastMessage(null), 4000)
    return { success: true, message: successMsg }
  }

  // Export / Share Setlist (.json download & clipboard copy)
  const handleShareSetlist = (setlist: WebSetlist) => {
    try {
      const exportData = {
        app: 'GTAR',
        version: import.meta.env.DEV ? GTAR_DEV_VERSION : GTAR_APP_VERSION,
        type: 'SETLIST_EXPORT',
        exportedAt: new Date().toISOString(),
        setlist: {
          id: setlist.id,
          name: setlist.name,
          songs: setlist.songs.map((ref) => {
            const matched = songs.find(
              (s) =>
                s.title.trim().toLowerCase() === ref.title.trim().toLowerCase() &&
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
          }),
        },
      }

      const jsonStr = JSON.stringify(exportData, null, 2)

      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard
          .writeText(jsonStr)
          .then(() => {
            setToastMessage(`Setlist "${setlist.name}" copied to clipboard as JSON!`)
            setTimeout(() => setToastMessage(null), 3500)
          })
          .catch(() => {
            triggerSetlistDownload(setlist.name, jsonStr)
          })
      } else {
        triggerSetlistDownload(setlist.name, jsonStr)
      }
    } catch (e) {
      console.error('Failed to share setlist', e)
    }
  }

  const triggerSetlistDownload = (name: string, content: string) => {
    const blob = new Blob([content], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${name.replace(/\s+/g, '_')}_setlist.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    setToastMessage(`Downloaded "${name}" setlist .json file!`)
    setTimeout(() => setToastMessage(null), 3500)
  }

  // Import online chord sheet directly into songbook library
  const handleImportOnlineChordSheet = (
    sheet: FetchedChordSheet,
    openInStage = false
  ) => {
    const existingIdx = songs.findIndex(
      (s) =>
        s.title.trim().toLowerCase() === sheet.title.trim().toLowerCase() &&
        (!sheet.artist || (s.artist || '').trim().toLowerCase() === sheet.artist.trim().toLowerCase())
    )

    if (existingIdx !== -1) {
      setToastMessage(`"${sheet.title}" is already in your library.`)
      setTimeout(() => setToastMessage(null), 3000)
      if (openInStage) {
        setActiveSongIndex(existingIdx)
        setQueueMode('library')
        setActiveView('stage')
      }
      return
    }

    const newSong: ActiveSongState = {
      id: Date.now(),
      title: sheet.title,
      artist: sheet.artist,
      key: sheet.key || 'G',
      capo: sheet.capo || 'No Capo',
      bpm: sheet.bpm || '120',
      format: sheet.format,
      transposeOffset: 0,
      rawContent: sheet.rawContent,
    }

    setSongs((prev) => [newSong, ...prev])
    setToastMessage(`Imported "${sheet.title}" to Songbook Library!`)
    setTimeout(() => setToastMessage(null), 4000)

    if (openInStage) {
      setActiveSongIndex(0)
      setQueueMode('library')
      setActiveView('stage')
    }
  }

  // Soft-delete song from library (moves to Trash bin)
  const handleDeleteSong = (indexToDelete: number) => {
    const songToDelete = songs[indexToDelete]
    if (!songToDelete) return

    // Move to deletedSongs (Trash)
    setDeletedSongs((prev) => [{ ...songToDelete, isDeleted: true }, ...prev])

    if (songs.length <= 1) {
      // If last remaining song is deleted, create blank song template
      const blankSong: ActiveSongState = {
        id: Date.now(),
        title: 'New Song',
        artist: '',
        key: 'G',
        capo: 'No Capo',
        bpm: '120',
        format: 'CHORD_PRO',
        transposeOffset: 0,
        rawContent: `{title: New Song}\n{artist: }\n{key: G}\n{capo: No Capo}\n{tempo: 120}\n\n[Intro]\n\n[Verse 1]\n\n[Chorus]\n`,
      }
      setSongs([blankSong])
      setActiveSongIndex(0)
    } else {
      const updatedSongs = songs.filter((_, idx) => idx !== indexToDelete)
      setSongs(updatedSongs)
      if (activeSongIndex === indexToDelete) {
        setActiveSongIndex(Math.min(indexToDelete, updatedSongs.length - 1))
      } else if (activeSongIndex > indexToDelete) {
        setActiveSongIndex(activeSongIndex - 1)
      }
    }

    setToastMessage(`Moved "${songToDelete.title}" to Trash.`)
    setTimeout(() => setToastMessage(null), 3500)
  }

  // Restore song from Trash back to library
  const handleRestoreSong = (id: number | string) => {
    const songToRestore = deletedSongs.find((s) => String(s.id) === String(id))
    if (!songToRestore) return

    setDeletedSongs((prev) => prev.filter((s) => String(s.id) !== String(id)))
    const restoredSong: ActiveSongState = { ...songToRestore, isDeleted: false }
    setSongs((prev) => [restoredSong, ...prev])

    setToastMessage(`Restored "${restoredSong.title}" to Songbook Library!`)
    setTimeout(() => setToastMessage(null), 3500)
  }

  // Permanently delete song from Trash
  const handlePermanentDeleteSong = (id: number | string) => {
    const target = deletedSongs.find((s) => String(s.id) === String(id))
    setDeletedSongs((prev) => prev.filter((s) => String(s.id) !== String(id)))
    setToastMessage(`Permanently deleted ${target ? `"${target.title}"` : 'song'}.`)
    setTimeout(() => setToastMessage(null), 3500)
  }

  // Permanently delete all songs in Trash
  const handleEmptyTrash = () => {
    const count = deletedSongs.length
    setDeletedSongs([])
    setToastMessage(`Trash emptied (${count} songs permanently deleted).`)
    setTimeout(() => setToastMessage(null), 3500)
  }

  // Create new blank song template and switch to Desktop Editor
  const handleNewSong = () => {
    const blankSong: ActiveSongState = {
      id: Date.now(),
      title: 'New Song',
      artist: '',
      key: 'G',
      capo: 'No Capo',
      bpm: '120',
      format: 'CHORD_PRO',
      transposeOffset: 0,
      rawContent: `{title: New Song}\n{artist: }\n{key: G}\n{capo: No Capo}\n{tempo: 120}\n\n[Intro]\n\n[Verse 1]\n\n[Chorus]\n`,
    }
    setSongs((prev) => [blankSong, ...prev])
    setActiveSongIndex(0)
    setActiveSetlistId(null)
    setActiveView('editor')
    setIsSetlistDrawerOpen(false)
  }

  // Create a new setlist
  const handleNewSetlist = () => {
    const newId = `setlist-${Date.now()}`
    const newSetlistName = `Setlist ${setlists.length + 1}`
    const created: WebSetlist = {
      id: newId,
      name: newSetlistName,
      createdAt: Date.now(),
      songs: [],
    }
    setSetlists((prev) => [...prev, created])
    setActiveSetlistId(newId)
    setActiveSetlistSongIndex(0)
    setQueueMode('setlist')
    setToastMessage(`Created new setlist "${newSetlistName}"`)
    setTimeout(() => setToastMessage(null), 3000)
  }

  // Navigate Home (Logo click / Songbook tab opens Songbook Library)
  const handleNavigateHome = () => {
    setQueueMode('library')
    setActiveView('songbook')
    setSearchQuery('')
    setIsSetlistDrawerOpen(false)
  }

  // Update song fields in editor
  const handleUpdateSong = (updated: Partial<ActiveSongState>) => {
    setSongs((prev) =>
      prev.map((s, idx) => {
        if (idx !== activeSongIndex) return s
        const next = { ...s, ...updated }
        if (updated.rawContent !== undefined) {
          const meta = extractDirectives(updated.rawContent)
          if (meta.title) next.title = meta.title
          if (meta.artist) next.artist = meta.artist
          if (meta.key) next.key = meta.key
          if (meta.capo) next.capo = meta.capo
          if (meta.bpm) next.bpm = meta.bpm
          if (meta.tags) next.tags = meta.tags
        }
        return next
      })
    )
  }

  // Explicit save action from DesktopEditor
  const handleSaveSongFromEditor = (updatedSong: ActiveSongState) => {
    setSongs((prev) => {
      const nextSongs = prev.map((s, idx) => (idx === activeSongIndex ? updatedSong : s))
      try {
        localStorage.setItem('gtar_songs_store', JSON.stringify(nextSongs))
      } catch (err) {
        console.error('Failed to persist songs store:', err)
      }
      return nextSongs
    })
    setToastMessage('Song saved successfully')
    setTimeout(() => setToastMessage(null), 3500)
  }

  // Import single song
  const handleImportSong = (imported: Partial<ActiveSongState>) => {
    const newSong: ActiveSongState = {
      id: Date.now(),
      title: imported.title || 'Imported Song',
      artist: imported.artist || '',
      key: imported.key || 'G',
      capo: imported.capo || '',
      bpm: imported.bpm || '120',
      rawContent: imported.rawContent || '',
      format: imported.format || 'CHORD_PRO',
      transposeOffset: imported.transposeOffset || 0,
    }
    setSongs((prev) => [newSong, ...prev])
    setActiveSongIndex(0)
    setActiveSetlistId(null)
  }

  // Batch import all detected songs
  const handleImportAllSongs = (importedSongs: Array<Partial<ActiveSongState>>) => {
    if (importedSongs.length === 0) return
    const completeSongs: ActiveSongState[] = importedSongs.map((s, idx) => ({
      id: Date.now() + idx,
      title: s.title || 'Imported Song',
      artist: s.artist || '',
      key: s.key || 'G',
      capo: s.capo || 'No Capo',
      bpm: s.bpm || '120',
      format: s.format || 'CHORD_PRO',
      transposeOffset: s.transposeOffset || 0,
      rawContent: s.rawContent || '',
    }))

    setSongs((prev) => [...prev, ...completeSongs])
  }

  // Full Restore (Wipe & Replace): Clear -> Insert songs -> Insert setlists -> Reset active playback queue
  const handleFullRestore = (
    importedSongs: Array<Partial<ActiveSongState>>,
    importedSetlists: WebSetlist[]
  ) => {
    if (importedSongs.length === 0) return
    const completeSongs: ActiveSongState[] = importedSongs.map((s, idx) => ({
      id: s.id || Date.now() + idx,
      title: s.title || 'Imported Song',
      artist: s.artist || '',
      key: s.key || 'G',
      capo: s.capo || 'No Capo',
      bpm: s.bpm || '120',
      format: s.format || 'CHORD_PRO',
      transposeOffset: s.transposeOffset || 0,
      rawContent: s.rawContent || '',
    }))

    // 1. songs array ONLY populates the Songbook Library
    setSongs(completeSongs)
    // 2. setlists array restores into setlists table/state
    setSetlists(importedSetlists || [])
    // 3. Reset active playback queue to first song or empty (never create an all-songs setlist)
    setActiveSetlistId(null)
    setActiveSongIndex(0)
    setActiveSetlistSongIndex(0)
  }

  // Smart Merge: Upsert songs by match (title + artist) -> Append setlists uniquely
  const handleSmartMerge = (
    importedSongs: Array<Partial<ActiveSongState>>,
    importedSetlists: WebSetlist[]
  ) => {
    // 1. Upsert songs by match (title + artist)
    setSongs((prev) => {
      const existingMap = new Map(
        prev.map((s) => [`${s.title.trim().toLowerCase()}::${(s.artist || '').trim().toLowerCase()}`, s])
      )
      for (const item of importedSongs) {
        const key = `${(item.title || '').trim().toLowerCase()}::${(item.artist || '').trim().toLowerCase()}`
        if (!existingMap.has(key)) {
          existingMap.set(key, {
            id: item.id || Date.now() + existingMap.size,
            title: item.title || 'Imported Song',
            artist: item.artist || '',
            key: item.key || 'G',
            capo: item.capo || 'No Capo',
            bpm: item.bpm || '120',
            format: item.format || 'CHORD_PRO',
            transposeOffset: item.transposeOffset || 0,
            rawContent: item.rawContent || '',
          })
        }
      }
      return Array.from(existingMap.values())
    })

    // 2. Append setlists uniquely by name
    if (importedSetlists && importedSetlists.length > 0) {
      setSetlists((prev) => {
        const existingNames = new Set(prev.map((sl) => sl.name.trim().toLowerCase()))
        const newSetlists = importedSetlists.filter(
          (sl) => !existingNames.has(sl.name.trim().toLowerCase())
        )
        return [...prev, ...newSetlists]
      })
    }
  }

  // Direct import single setlist (.json)
  const handleImportSingleSetlist = (
    importedSetlist: WebSetlist,
    newSongs: ActiveSongState[]
  ) => {
    if (newSongs && newSongs.length > 0) {
      setSongs((prev) => {
        const existing = new Set(
          prev.map((s) => `${s.title.trim().toLowerCase()}::${(s.artist || '').trim().toLowerCase()}`)
        )
        const toAdd = newSongs.filter(
          (s) => !existing.has(`${s.title.trim().toLowerCase()}::${(s.artist || '').trim().toLowerCase()}`)
        )
        return [...toAdd, ...prev]
      })
    }
    setSetlists((prev) => {
      const finalSl: WebSetlist = {
        ...importedSetlist,
        id: prev.some((s) => String(s.id) === String(importedSetlist.id))
          ? `sl_${Date.now()}`
          : importedSetlist.id,
        name: importedSetlist.name || 'Imported Setlist',
        createdAt: importedSetlist.createdAt || Date.now(),
        songs: importedSetlist.songs || [],
      }
      return [...prev, finalSl]
    })
    setToastMessage(`Imported setlist "${importedSetlist.name}" (${importedSetlist.songs?.length || 0} songs)!`)
    setTimeout(() => setToastMessage(null), 4000)
  }

  // Check for updates simulation
  const handleCheckForUpdates = () => {
    setIsCheckingUpdates(true)
    setTimeout(() => {
      setIsCheckingUpdates(false)
      setShowUpdateSuccessModal(true)
    }, 850)
  }

  // Filter songs if searchQuery is active
  const filteredSongs = searchQuery.trim()
    ? songs.filter(
        (s) =>
          s.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
          s.artist?.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : songs

  if (!isUnlocked) {
    return <LoginWall onUnlock={() => setIsUnlocked(true)} />
  }

  return (
    <div className="min-h-screen flex flex-col bg-[#002B36] text-[#EEE8D5]">
      {/* Unified Android v1.0.44 Top Bar — hidden in stage performance mode */}
      {!isStagePerformanceMode && (
        <Header
          activeView={activeView}
          onViewChange={setActiveView}
          song={currentSong}
          allSongs={songs}
          songsCount={filteredSongs.length > 0 ? filteredSongs.length : songs.length}
          deletedSongsCount={deletedSongs.length}
          activeSongIndex={activeSongIndex}
          queueMode={queueMode}
          activeSetlistSongsCount={activeSetlistSongs.length}
          activeSetlistSongIndex={activeSetlistSongIndex}
          searchQuery={searchQuery}
          onSearchQueryChange={setSearchQuery}
          onSelectSearchSong={(songIdx) => {
            handleSelectLibrarySong(songIdx)
            setActiveView('stage')
          }}
          onSearchWebExternal={(query) => {
            setSearchQuery(query)
            setIsWebsiteUrlModalOpen(true)
          }}
          onNavigateHome={handleNavigateHome}
          onOpenWebsiteUrlSource={() => setIsWebsiteUrlModalOpen(true)}
          onOpenStageTools={() => setIsStageToolsModalOpen(true)}
          onToggleTheme={() => setIsThemeModalOpen(true)}
          onOpenStageSettings={() => setIsStageSettingsModalOpen(true)}
          onOpenImportModal={() => setIsImportModalOpen(true)}
          onOpenBackupRestoreModal={() => setIsBackupRestoreModalOpen(true)}
          onCheckForUpdates={handleCheckForUpdates}
          isCheckingUpdates={isCheckingUpdates}
          onOpenSetlistDrawer={() => setIsSetlistDrawerOpen(true)}
          setlists={setlists}
          activeSetlistId={activeSetlistId}
          activeSetlistName={activeSetlist?.name}
          activeSetlistSongs={activeSetlistSongs}
          onSelectSetlistSong={handleSelectSetlistSong}
          onSelectSetlist={handleSelectSetlist}
          onPushSetlistToBandSync={handlePushSetlistToMembers}
          onShareSetlist={handleShareSetlist}
          onDirectImportOnlineSong={handleImportOnlineChordSheet}
        />
      )}

      {/* Main Workspace: Songbook Library vs Split Desktop Editor vs Trash vs 1:1 Stage View */}
      <main className="flex-1 flex overflow-hidden">
        {activeView === 'songbook' ? (
          <SongbookHomeView
            songs={filteredSongs.length > 0 ? filteredSongs : songs}
            activeSongIndex={activeSongIndex}
            onSelectSong={(songIdx) => {
              handleSelectLibrarySong(songIdx)
              setActiveView('stage')
            }}
            onNewSong={handleNewSong}
            onNewSetlist={handleNewSetlist}
            onOpenImportModal={() => setIsImportModalOpen(true)}
            onOpenWebsiteUrlSource={() => setIsWebsiteUrlModalOpen(true)}
            onOpenSetlists={() => setIsSetlistDrawerOpen(true)}
            onDeleteSong={handleDeleteSong}
            setlists={setlists}
            onSelectSetlistSong={(setlistId, songIdx) => {
              handleSelectSetlistSong(setlistId, songIdx)
              setActiveView('stage')
            }}
            onPushSetlistToBandSync={handlePushSetlistToMembers}
            onShareSetlist={handleShareSetlist}
            onImportSingleSetlist={handleImportSingleSetlist}
          />
        ) : activeView === 'editor' ? (
          <DesktopEditor
            song={currentSong}
            onUpdateSong={handleUpdateSong}
            onSaveSong={handleSaveSongFromEditor}
            onClose={() => setActiveView('stage')}
            transposeOffset={currentSong.transposeOffset || 0}
          />
        ) : activeView === 'trash' ? (
          <TrashView
            deletedSongs={deletedSongs}
            onRestoreSong={handleRestoreSong}
            onPermanentDeleteSong={handlePermanentDeleteSong}
            onEmptyTrash={handleEmptyTrash}
            onBackToSongbook={() => setActiveView('songbook')}
          />
        ) : (
          <StageView
            song={currentSong}
            songs={filteredSongs.length > 0 ? filteredSongs : songs}
            activeSongIndex={activeSongIndex}
            onSelectSongIndex={handleSelectLibrarySong}
            queueMode={queueMode}
            onToggleQueueMode={handleToggleQueueMode}
            isInSetlistMode={isInSetlistMode}
            activeSetlistSongs={activeSetlistSongs}
            activeSetlistSongIndex={activeSetlistSongIndex}
            onSelectSetlistSongIndex={setActiveSetlistSongIndex}
            activeSetlistName={activeSetlist?.name}
            setlists={setlists}
            onSelectSetlist={handleSelectSetlist}
            onOpenSetlistDrawer={() => setIsSetlistDrawerOpen(true)}
            onBack={() => setActiveView('songbook')}
            transposeOffset={currentSong.transposeOffset || 0}
            onTransposeChange={handleTransposeChange}
            fontStyle={fontStyle}
            onSelectFontStyle={setFontStyle}
            isTwoColumn={isTwoColumn}
            onToggleTwoColumn={setIsTwoColumn}
            onOpenBandSync={() => setIsStageToolsModalOpen(true)}
            onPerformanceModeChange={setIsStagePerformanceMode}
          />
        )}
      </main>

      {/* Slide-over Setlist / Library Drawer */}
      <SetlistDrawer
        isOpen={isSetlistDrawerOpen}
        onClose={() => setIsSetlistDrawerOpen(false)}
        songs={filteredSongs.length > 0 ? filteredSongs : songs}
        activeSongIndex={activeSongIndex}
        onSelectSongIndex={(idx) => {
          handleSelectLibrarySong(idx)
          setIsSetlistDrawerOpen(false)
        }}
        setlists={setlists}
        activeSetlistId={activeSetlistId}
        activeSetlistSongIndex={activeSetlistSongIndex}
        onSelectSetlistSong={(setlistId, songIdx) => {
          handleSelectSetlistSong(setlistId, songIdx)
          setIsSetlistDrawerOpen(false)
        }}
        onReorderSetlistSong={handleReorderSetlistSong}
        onRemoveSetlistSong={handleRemoveSetlistSong}
        onDeleteSetlist={handleDeleteSetlist}
        onDeleteSong={handleDeleteSong}
        onNewSong={handleNewSong}
        onNewSetlist={handleNewSetlist}
        onImportSingleSetlist={handleImportSingleSetlist}
        onSmartMerge={handleSmartMerge}
        onExportAllData={() => exportAllDataJson(songs, setlists)}
      />

      {/* Stage Color Theme Modal */}
      <ThemeModal
        isOpen={isThemeModalOpen}
        onClose={() => setIsThemeModalOpen(false)}
        currentTheme={stageTheme}
        customColors={customThemeColors}
        onApplyTheme={(theme, colors) => {
          setStageTheme(theme)
          if (colors) {
            setCustomThemeColors(colors)
          }
        }}
        onSelectTheme={(theme) => setStageTheme(theme)}
      />

      {/* Website URL Source Modal */}
      <WebsiteUrlSourceModal
        isOpen={isWebsiteUrlModalOpen}
        onClose={() => setIsWebsiteUrlModalOpen(false)}
      />

      {/* Import Modal Dialog (File Import & Folder Batch) */}
      <ImportDialogModal
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        onImportSong={handleImportSong}
        onImportAllSongs={handleImportAllSongs}
      />

      {/* Backup & Restore Modal Dialog (Export Backup & Restore Backup Smart Merge) */}
      <BackupRestoreDialogModal
        isOpen={isBackupRestoreModalOpen}
        onClose={() => setIsBackupRestoreModalOpen(false)}
        currentSong={currentSong}
        allSongs={songs}
        setlists={setlists}
        onImportAllSongs={handleImportAllSongs}
        onFullRestore={handleFullRestore}
        onSmartMerge={handleSmartMerge}
        onOpenAdvancedBridge={() => setIsJsonModalOpen(true)}
      />

      {/* Stage Settings Modal */}
      <StageSettingsModal
        isOpen={isStageSettingsModalOpen}
        onClose={() => setIsStageSettingsModalOpen(false)}
        fontStyle={fontStyle}
        onSelectFontStyle={setFontStyle}
        isTwoColumn={isTwoColumn}
        onToggleTwoColumn={setIsTwoColumn}
        onOpenStageTools={() => {
          setIsStageSettingsModalOpen(false)
          setIsStageToolsModalOpen(true)
        }}
        onToggleTheme={() => {
          setIsStageSettingsModalOpen(false)
          setIsThemeModalOpen(true)
        }}
        onCheckForUpdates={handleCheckForUpdates}
        onExportAllData={() => exportAllDataJson(songs, setlists)}
        onOpenBackupRestoreModal={() => {
          setIsStageSettingsModalOpen(false)
          setIsBackupRestoreModalOpen(true)
        }}
      />

      {/* Stage Tools & Band Sync Modal */}
      <BandSyncModal
        isOpen={isStageToolsModalOpen}
        onClose={() => setIsStageToolsModalOpen(false)}
        initialTab="sync"
        onPushSetlist={handlePushSetlistToMembers}
        activeSetlistName={activeSetlist?.name}
        activeSetlistSongCount={activeSetlistSongs.length}
      />

      {/* Advanced JSON Bridge Modal */}
      <JsonBridgeModal
        isOpen={isJsonModalOpen}
        initialTab="export"
        onClose={() => setIsJsonModalOpen(false)}
        song={currentSong}
        allSongs={songs}
        onImportSong={handleImportSong}
        onImportAllSongs={handleImportAllSongs}
      />

      {/* Header Key Picker Modal */}
      <KeyPickerModal
        isOpen={isHeaderKeyPickerOpen}
        onClose={() => setIsHeaderKeyPickerOpen(false)}
        originalKey={currentSong.key}
        currentOffset={currentSong.transposeOffset || 0}
        capoText={currentSong.capo}
        onSelectOffset={handleTransposeChange}
        onReset={() => handleTransposeChange(0)}
      />

      {/* Check for Updates Confirmation Modal */}
      {showUpdateSuccessModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in">
          <div className="w-full max-w-sm rounded-2xl bg-[#073642] border border-[#2AA198] p-6 shadow-2xl text-center space-y-4">
            <div className="w-12 h-12 rounded-full bg-[#2AA198]/20 border border-[#2AA198]/40 flex items-center justify-center text-[#2AA198] mx-auto">
              <Sparkles className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <h3 className="text-base font-extrabold text-[#FDF6E3]">You're Up to Date!</h3>
              <p className="text-xs text-[#2AA198] font-mono font-bold">
                GTAR Web App {import.meta.env.DEV ? `v${GTAR_DEV_VERSION}` : `v${GTAR_APP_VERSION}`}
              </p>
            </div>
            <div className="p-3 rounded-xl bg-[#002B36] text-left text-[11px] text-[#93A1A1] space-y-1 border border-[#1A4A55]">
              <div className="font-bold text-[#EEE8D5] flex items-center gap-1.5">
                <Check className="w-3.5 h-3.5 text-[#2AA198]" />
                <span>1:1 Parity with Android v{GTAR_APP_VERSION}</span>
              </div>
              <p>• Unified TopAppBar with 4-Action 3-Dot Menu</p>
              <p>• Band Sync multi-screen stage sync (Leader / Member)</p>
              <p>• Classic chord-over-lyric layout (no inline brackets)</p>
              <p>• Clean floating intro chords without keypad boxes</p>
              <p>• Monospace, Sans, Serif font selector & shortcuts</p>
            </div>
            <button
              type="button"
              onClick={() => setShowUpdateSuccessModal(false)}
              className="w-full py-2.5 rounded-xl bg-[#2AA198] text-[#002B36] font-bold text-xs hover:bg-[#35B8AD] transition-colors cursor-pointer"
            >
              Great!
            </button>
          </div>
        </div>
      )}

      {/* Global Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 animate-in fade-in slide-in-from-bottom-3 duration-200">
          <div className="px-4 py-2.5 rounded-xl bg-[#002B36] border border-[#2AA198] text-[#FDF6E3] text-xs font-bold shadow-2xl flex items-center gap-2 max-w-md text-center">
            <span className="w-2 h-2 rounded-full bg-[#10B981] shrink-0 animate-pulse" />
            <span>{toastMessage}</span>
          </div>
        </div>
      )}
    </div>
  )
}

export default App
