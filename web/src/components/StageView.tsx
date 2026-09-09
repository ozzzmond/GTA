import React, { useState, useEffect, useRef } from 'react'
import {
  Play,
  Pause,
  Maximize2,
  Minimize2,
  ChevronDown,
  RotateCcw,
  ListMusic,
  Columns2,
  Square,
  Minus,
  Plus,
  SkipBack,
  SkipForward,
  Radio,
  Users,
  Wifi,
  BookOpen,
  ArrowLeft,
  Cast,
} from 'lucide-react'
import { transposeKey, formatTransposeOffset } from '../utils/chordTransposer'
import { parseGtarSong, splitSongLinesForColumns } from '../utils/songParser'
import { metronome } from '../utils/metronome'
import { bandSync, type BandSyncState } from '../utils/bandSync'
import { stageCast } from '../utils/stageCast'
import { getChordVoicing, type ChordVoicing } from '../utils/chordDictionary'
import { SongLineRenderer } from './SongLineRenderer'
import { KeyPickerModal } from './KeyPickerModal'
import { FretboardDiagramModal } from './FretboardDiagramModal'
import { BandSyncModal } from './BandSyncModal'
import type { ActiveSongState } from '../types/gtar'

interface StageViewProps {
  song: ActiveSongState
  songs: ActiveSongState[]
  activeSongIndex: number
  onSelectSongIndex: (index: number) => void
  queueMode?: 'library' | 'setlist'
  onToggleQueueMode?: (mode: 'library' | 'setlist') => void
  isInSetlistMode?: boolean
  activeSetlistSongs?: ActiveSongState[]
  activeSetlistSongIndex?: number
  onSelectSetlistSongIndex?: (index: number) => void
  activeSetlistName?: string
  setlists?: Array<{ id: string | number; name: string; songs: any[] }>
  onSelectSetlist?: (setlistId: string | number) => void
  onOpenSetlistDrawer: () => void
  transposeOffset: number
  onTransposeChange: (offset: number) => void
  fontStyle?: 'mono' | 'sans' | 'serif'
  onSelectFontStyle?: (style: 'mono' | 'sans' | 'serif') => void
  isTwoColumn?: boolean
  onToggleTwoColumn?: (enabled: boolean) => void
  onOpenBandSync?: () => void
  onBack?: () => void
}

/**
 * MetaBadge component matching Android SongViewerScreen.kt:
 * Surface(shape = RoundedCornerShape(6.dp), color = surfaceBackground, border = 1.dp divider)
 */
const MetaBadge: React.FC<{ label: string }> = ({ label }) => (
  <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-[#073642] border border-[#1A4A55] text-[#B58900] font-mono font-semibold text-xs uppercase tracking-wide select-none">
    {label}
  </span>
)

export const StageView: React.FC<StageViewProps> = ({
  song,
  songs,
  activeSongIndex,
  onSelectSongIndex,
  queueMode = 'library',
  isInSetlistMode: propIsInSetlistMode = false,
  activeSetlistSongs = [],
  activeSetlistSongIndex = 0,
  onSelectSetlistSongIndex,
  onOpenSetlistDrawer,
  transposeOffset,
  onTransposeChange,
  fontStyle: externalFontStyle,
  onSelectFontStyle: externalOnSelectFontStyle,
  isTwoColumn: externalIsTwoColumn,
  onToggleTwoColumn: externalOnToggleTwoColumn,
  onOpenBandSync,
  onBack,
}) => {
  const isInSetlistMode = propIsInSetlistMode || queueMode === 'setlist'
  // Stage view configuration & controls (matching Jetpack Compose SongViewerScreen.kt)
  const [isAutoScrolling, setIsAutoScrolling] = useState(false)
  const [scrollSpeed, setScrollSpeed] = useState<number>(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('gtar_stage_scroll_speed')
      if (saved) {
        const val = parseInt(saved, 10)
        if (!isNaN(val) && val >= 10 && val <= 150) return val
      }
    }
    return 35
  })
  const [fontSizePx, setFontSizePx] = useState<number>(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('gtar_stage_font_size')
      if (saved) {
        const val = parseInt(saved, 10)
        if (!isNaN(val) && val >= 12 && val <= 38) return val
      }
    }
    return 20
  })
  const [localFontStyle, setLocalFontStyle] = useState<'mono' | 'sans' | 'serif'>('mono')
  const [localIsTwoColumn, setLocalIsTwoColumn] = useState<boolean>(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('gtar_stage_two_column') === 'true'
    }
    return false
  })
  const [isFullscreen, setIsFullscreen] = useState(false)

  const fontStyle = externalFontStyle !== undefined ? externalFontStyle : localFontStyle
  const setFontStyle = externalOnSelectFontStyle || setLocalFontStyle

  const isTwoColumn = externalIsTwoColumn !== undefined ? externalIsTwoColumn : localIsTwoColumn
  const setIsTwoColumn = (enabled: boolean) => {
    if (externalOnToggleTwoColumn) {
      externalOnToggleTwoColumn(enabled)
    } else {
      setLocalIsTwoColumn(enabled)
    }
    if (typeof window !== 'undefined') {
      localStorage.setItem('gtar_stage_two_column', String(enabled))
    }
  }

  // Modals & Drawers
  const [isKeyPickerOpen, setIsKeyPickerOpen] = useState(false)
  const [selectedVoicing, setSelectedVoicing] = useState<ChordVoicing | null>(null)
  const [isSpeedPromptOpen, setIsSpeedPromptOpen] = useState(false)
  const [speedInputText, setSpeedInputText] = useState('35')
  const [isBandSyncModalOpen, setIsBandSyncModalOpen] = useState(false)

  // Band Sync State
  const [syncState, setSyncState] = useState<BandSyncState>(() => bandSync.getState())

  // Stage Cast Active Presentation State
  const [isCastActive, setIsCastActive] = useState(() => stageCast.isPresentationActive())

  useEffect(() => {
    const unsubscribe = stageCast.subscribeSessionState((active) => {
      setIsCastActive(active)
    })
    return unsubscribe
  }, [])

  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const scrollAnimRef = useRef<number | null>(null)

  // Parse song with native v1.0.42 parser and active transpose offset
  const parsedSong = parseGtarSong(song.rawContent, transposeOffset)

  // Band Sync subscriptions
  useEffect(() => {
    const unsub = bandSync.subscribe((st) => setSyncState(st))
    return unsub
  }, [])

  // Listen to incoming messages for Band Member (Client)
  useEffect(() => {
    const unsubMsg = bandSync.onMessage((msg) => {
      if (syncState.role === 'CLIENT') {
        if (msg.type === 'SONG_SYNC' && msg.payload) {
          if (typeof msg.payload.transposeOffset === 'number') {
            onTransposeChange(msg.payload.transposeOffset)
          }
          if (typeof msg.payload.scrollProgress === 'number') {
            const fraction = msg.payload.scrollProgress
            const container = scrollContainerRef.current
            if (container) {
              const maxScroll = container.scrollHeight - container.clientHeight
              if (maxScroll > 0) {
                container.scrollTo({
                  top: fraction * maxScroll,
                  behavior: 'smooth',
                })
              }
            }
            if (typeof window !== 'undefined') {
              const winMax = document.documentElement.scrollHeight - window.innerHeight
              if (winMax > 0) {
                window.scrollTo({ top: fraction * winMax, behavior: 'smooth' })
              }
            }
          }
        } else if (msg.type === 'SCROLL_SYNC' && msg.payload) {
          const fraction =
            typeof msg.payload.scrollFraction === 'number'
              ? msg.payload.scrollFraction
              : typeof msg.payload.scrollProgress === 'number'
              ? msg.payload.scrollProgress
              : typeof msg.payload.scroll === 'number'
              ? msg.payload.scroll
              : 0

          const container = scrollContainerRef.current
          if (container) {
            const maxScroll = container.scrollHeight - container.clientHeight
            if (maxScroll > 0) {
              const targetTop =
                msg.payload.scrollTop !== undefined && msg.payload.scrollTop > 0
                  ? msg.payload.scrollTop
                  : fraction * maxScroll
              container.scrollTo({ top: targetTop, behavior: 'smooth' })
            }
          }
          if (typeof window !== 'undefined') {
            const winMax = document.documentElement.scrollHeight - window.innerHeight
            if (winMax > 0) {
              window.scrollTo({ top: fraction * winMax, behavior: 'smooth' })
            }
          }
        } else if (msg.type === 'AUTOSCROLL_SYNC' && msg.payload) {
          setIsAutoScrolling(Boolean(msg.payload.isAutoScrolling))
          if (msg.payload.scrollSpeed) {
            setScrollSpeed(msg.payload.scrollSpeed)
          }
        }
      }
    })
    return unsubMsg
  }, [syncState.role, onTransposeChange])

  // Broadcast song change when role is HOST
  useEffect(() => {
    if (syncState.role === 'HOST') {
      const container = scrollContainerRef.current
      const maxScroll = container ? container.scrollHeight - container.clientHeight : 1
      const progress = container && maxScroll > 0 ? container.scrollTop / maxScroll : 0
      bandSync.broadcastSong(
        isInSetlistMode ? activeSetlistSongIndex : activeSongIndex,
        song.title,
        transposeOffset,
        {
          artist: song.artist,
          queueType: isInSetlistMode ? 'SETLIST' : 'LIBRARY',
          scrollProgress: progress,
          rawContent: song.rawContent,
          key: song.key,
          capo: song.capo,
        }
      )
    }
  }, [
    activeSongIndex,
    activeSetlistSongIndex,
    isInSetlistMode,
    song.title,
    song.artist,
    song.rawContent,
    song.key,
    song.capo,
    transposeOffset,
    syncState.role,
  ])

  // Metronome sync with song BPM
  useEffect(() => {
    const songBpmNum = parseInt(song.bpm, 10)
    if (!isNaN(songBpmNum) && songBpmNum >= 30 && songBpmNum <= 300) {
      metronome.setBpm(songBpmNum)
    }
  }, [song.bpm])

  // Continuous smooth auto-scroll loop
  useEffect(() => {
    if (!isAutoScrolling) {
      if (scrollAnimRef.current) {
        cancelAnimationFrame(scrollAnimRef.current)
        scrollAnimRef.current = null
      }
      return
    }

    const container = scrollContainerRef.current
    if (!container) return

    let lastTimestamp = performance.now()

    const scrollStep = (currentTimestamp: number) => {
      const elapsed = (currentTimestamp - lastTimestamp) / 1000
      lastTimestamp = currentTimestamp

      if (container) {
        container.scrollTop += scrollSpeed * elapsed
        if (container.scrollTop + container.clientHeight >= container.scrollHeight - 5) {
          setIsAutoScrolling(false)
          return
        }
      }

      scrollAnimRef.current = requestAnimationFrame(scrollStep)
    }

    scrollAnimRef.current = requestAnimationFrame(scrollStep)

    return () => {
      if (scrollAnimRef.current) {
        cancelAnimationFrame(scrollAnimRef.current)
      }
    }
  }, [isAutoScrolling, scrollSpeed])

  // Broadcast scroll position to Stage Cast teleprompter & BandSync when HOST
  const handleContainerScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget
    const maxScroll = target.scrollHeight - target.clientHeight
    const fraction = maxScroll > 0 ? target.scrollTop / maxScroll : 0

    // Mirror to Stage Cast teleprompter screen in real time
    stageCast.broadcastScroll(target.scrollTop, fraction)

    if (syncState.role === 'HOST') {
      if (maxScroll > 0) {
        bandSync.broadcastScroll(fraction, target.scrollTop)
      }
    }
  }

  // Toggle autoscroll and broadcast if HOST
  const handleToggleAutoScroll = () => {
    const nextVal = !isAutoScrolling
    setIsAutoScrolling(nextVal)
    if (syncState.role === 'HOST') {
      bandSync.broadcastAutoScroll(nextVal, scrollSpeed)
    }
  }

  // Adjust scroll speed and broadcast if HOST
  const handleAdjustSpeed = (newSpeed: number) => {
    const clamped = Math.max(10, Math.min(150, newSpeed))
    setScrollSpeed(clamped)
    if (typeof window !== 'undefined') {
      localStorage.setItem('gtar_stage_scroll_speed', String(clamped))
    }
    if (syncState.role === 'HOST') {
      bandSync.broadcastAutoScroll(isAutoScrolling, clamped)
    }
  }

  // Keyboard stage controls:
  // - Spacebar: Toggle Auto-Scroll (Play / Pause)
  // - ArrowRight or 'n': Next song in setlist
  // - ArrowLeft or 'p': Previous song in setlist
  // - ArrowUp / ArrowDown: Manually nudge scroll (or Shift + Arrow to adjust scroll speed)
  // - '+' / '-': Adjust font size
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (
        e.target instanceof HTMLInputElement ||
        e.target instanceof HTMLTextAreaElement ||
        (e.target as HTMLElement)?.isContentEditable
      ) {
        return
      }

      // Spacebar: Toggle Auto-Scroll (Play / Pause)
      if (e.code === 'Space' || e.key === ' ') {
        e.preventDefault()
        handleToggleAutoScroll()
        return
      }

      // ArrowRight or 'n': Next song in setlist or library
      if (e.key === 'ArrowRight' || e.key === 'n' || e.key === 'N') {
        e.preventDefault()
        if (isInSetlistMode && onSelectSetlistSongIndex) {
          if (activeSetlistSongIndex < activeSetlistSongs.length - 1) {
            onSelectSetlistSongIndex(activeSetlistSongIndex + 1)
          }
        } else if (activeSongIndex < songs.length - 1) {
          onSelectSongIndex(activeSongIndex + 1)
        }
        return
      }

      // ArrowLeft or 'p': Previous song in setlist or library
      if (e.key === 'ArrowLeft' || e.key === 'p' || e.key === 'P') {
        e.preventDefault()
        if (isInSetlistMode && onSelectSetlistSongIndex) {
          if (activeSetlistSongIndex > 0) {
            onSelectSetlistSongIndex(activeSetlistSongIndex - 1)
          }
        } else if (activeSongIndex > 0) {
          onSelectSongIndex(activeSongIndex - 1)
        }
        return
      }

      // ArrowUp: Manually nudge scroll up or adjust scroll speed (with Shift)
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        if (e.shiftKey) {
          handleAdjustSpeed(Math.min(180, scrollSpeed + 5))
        } else if (scrollContainerRef.current) {
          scrollContainerRef.current.scrollBy({ top: -80, behavior: 'smooth' })
        }
        return
      }

      // ArrowDown: Manually nudge scroll down or adjust scroll speed (with Shift)
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        if (e.shiftKey) {
          handleAdjustSpeed(Math.max(5, scrollSpeed - 5))
        } else if (scrollContainerRef.current) {
          scrollContainerRef.current.scrollBy({ top: 80, behavior: 'smooth' })
        }
        return
      }

      // Font size steppers
      if (e.key === '+' || e.key === '=') {
        setFontSizePx((prev) => Math.min(36, prev + 1))
      } else if (e.key === '-') {
        setFontSizePx((prev) => Math.max(13, prev - 1))
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [
    activeSongIndex,
    songs.length,
    onSelectSongIndex,
    isInSetlistMode,
    activeSetlistSongIndex,
    activeSetlistSongs.length,
    onSelectSetlistSongIndex,
    isAutoScrolling,
    scrollSpeed,
    syncState.role,
  ])

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().then(() => setIsFullscreen(true)).catch(() => {})
    } else {
      document.exitFullscreen().then(() => setIsFullscreen(false)).catch(() => {})
    }
  }

  const effectiveKey = song.key ? transposeKey(song.key, transposeOffset) : ''
  const offsetStr = formatTransposeOffset(transposeOffset)

  // Two-column split calculation matching splitSongLinesForColumns in Android SongViewerScreen.kt
  const [col1Lines, col2Lines] = isTwoColumn
    ? splitSongLinesForColumns(parsedSong.lines)
    : [parsedSong.lines, []]

  // Mirror stage state to secondary screen / Cast Presentation window in real-time
  useEffect(() => {
    stageCast.broadcastState({
      song,
      effectiveKey,
      transposeOffset,
      fontSizePx,
      fontStyle,
      isTwoColumn,
    })
  }, [song, effectiveKey, transposeOffset, fontSizePx, fontStyle, isTwoColumn])

  useEffect(() => {
    // Listen for REQUEST_STATE from external teleprompter window
    return stageCast.subscribe(
      () => {},
      () => {},
      () => {
        stageCast.broadcastState({
          song,
          effectiveKey,
          transposeOffset,
          fontSizePx,
          fontStyle,
          isTwoColumn,
        })
      }
    )
  }, [song, effectiveKey, transposeOffset, fontSizePx, fontStyle, isTwoColumn])

  const handleChordClick = (chordName: string) => {
    const voicing = getChordVoicing(chordName)
    if (voicing) {
      setSelectedVoicing(voicing)
    }
  }

  const handleCustomSpeedSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const val = parseInt(speedInputText, 10)
    if (!isNaN(val) && val >= 5 && val <= 180) {
      handleAdjustSpeed(val)
    }
    setIsSpeedPromptOpen(false)
  }

  return (
    <div className="flex-1 flex flex-col h-[calc(100vh-4rem)] bg-[#002B36] select-none relative overflow-hidden">
      {/* =================================================================== */}
      {/* 1. TOP APP BAR (Exact 1:1 Jetpack Compose SongViewerScreen.kt)       */}
      {/* =================================================================== */}
      <div className="border-b border-[#1A4A55] bg-[#073642] px-4 sm:px-6 py-2 flex flex-wrap items-center justify-between gap-3 z-20 shadow-md">
        {/* Left Side: Back Navigation Button + Song Title & Artist */}
        <div className="flex items-center gap-2 sm:gap-3">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              className="p-2 hover:bg-white/10 rounded-full transition-colors cursor-pointer text-[#EEE8D5] hover:text-[#2AA198] flex items-center justify-center -ml-1 select-none active:scale-95"
              title="Back to Songbook Library"
              aria-label="Back to Songbook Library"
            >
              <ArrowLeft className="w-5 h-5 stroke-[2.2]" />
            </button>
          )}
          <div className="min-w-0">
            <h1 className="text-base sm:text-lg font-extrabold text-[#EEE8D5] tracking-tight leading-tight truncate max-w-[240px] sm:max-w-xs md:max-w-md">
              {song.title || 'Untitled Song'}
            </h1>
            {song.artist && (
              <p className="text-[11px] sm:text-xs text-[#2AA198] font-semibold truncate leading-none mt-0.5">
                {song.artist}
              </p>
            )}
          </div>
        </div>

        {/* Right Side: Font Family, Font Size A-/A+, Column Reflow, Transpose Stepper, Band Sync, Stage Tools, Fullscreen */}
        <div className="flex items-center gap-2 sm:gap-2.5 flex-wrap">
          {/* Font Family Selector (Mono / Sans / Serif matching Android SongFontStyle) */}
          <div className="flex items-center bg-[#002B36] rounded-lg border border-[#1A4A55] p-0.5 text-xs font-semibold">
            {(
              [
                { id: 'mono', label: 'Mono', title: 'Monospace (Recommended for stage chord alignment)' },
                { id: 'sans', label: 'Sans', title: 'Sans-Serif (Clean modern look)' },
                { id: 'serif', label: 'Serif', title: 'Stage Serif (High contrast bold stage style)' },
              ] as const
            ).map(({ id, label, title }) => (
              <button
                key={id}
                type="button"
                onClick={() => setFontStyle(id)}
                className={`px-2 py-1 rounded transition-all cursor-pointer select-none ${
                  fontStyle === id
                    ? 'bg-[#2AA198] text-[#002B36] font-extrabold shadow-sm'
                    : 'text-[#EEE8D5] hover:text-[#2AA198]'
                }`}
                title={title}
              >
                {label}
              </button>
            ))}
          </div>

          {/* A- / A+ Font Size Stepper (matching Compose onAdjustFontSize) */}
          <div className="flex items-center bg-[#002B36] rounded-lg border border-[#1A4A55] p-0.5">
            <button
              type="button"
              onClick={() => {
                const next = Math.max(12, fontSizePx - 1)
                setFontSizePx(next)
                if (typeof window !== 'undefined') {
                  localStorage.setItem('gtar_stage_font_size', String(next))
                }
              }}
              className="px-2 py-1 text-xs font-extrabold text-[#EEE8D5] hover:text-[#2AA198] rounded cursor-pointer select-none"
              title="Decrease Font Size (A-)"
            >
              A-
            </button>
            <span className="text-[11px] font-mono text-[#93A1A1] px-1 font-semibold">
              {fontSizePx}
            </span>
            <button
              type="button"
              onClick={() => {
                const next = Math.min(38, fontSizePx + 1)
                setFontSizePx(next)
                if (typeof window !== 'undefined') {
                  localStorage.setItem('gtar_stage_font_size', String(next))
                }
              }}
              className="px-2 py-1 text-xs font-extrabold text-[#EEE8D5] hover:text-[#2AA198] rounded cursor-pointer select-none"
              title="Increase Font Size (A+)"
            >
              A+
            </button>
          </div>

          {/* Two-Column Reflow Toggle (Compose ViewStream vs ViewColumn) */}
          <button
            type="button"
            onClick={() => setIsTwoColumn(!isTwoColumn)}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-mono font-semibold transition-all cursor-pointer ${
              isTwoColumn
                ? 'bg-[#B58900]/20 text-[#B58900] border-[#B58900] font-bold shadow-sm'
                : 'bg-[#002B36] text-[#EEE8D5] border-[#1A4A55] hover:text-[#2AA198]'
            }`}
            title={isTwoColumn ? 'Switch to 1 Column' : 'Switch to 2 Columns'}
          >
            {isTwoColumn ? <Columns2 className="w-3.5 h-3.5 text-[#B58900]" /> : <Square className="w-3.5 h-3.5" />}
            <span className="hidden sm:inline">{isTwoColumn ? '2-Col' : '1-Col'}</span>
          </button>

          {/* Standard Transpose Stepper: [ - ] Key: G (+1) [ + ] (Compose lines 523-589) */}
          <div
            className={`flex items-center rounded-lg border transition-colors p-0.5 ${
              transposeOffset !== 0
                ? 'bg-[#B58900]/15 border-[#B58900]'
                : 'bg-[#002B36] border-[#1A4A55]'
            }`}
          >
            <button
              type="button"
              onClick={() => onTransposeChange(transposeOffset - 1)}
              className="p-1 text-[#EEE8D5] hover:text-[#2AA198] rounded cursor-pointer"
              title="Transpose Down (-1)"
            >
              <Minus className="w-3.5 h-3.5" />
            </button>

            <button
              type="button"
              onClick={() => setIsKeyPickerOpen(true)}
              className={`flex items-center gap-1 px-2 py-1 text-xs font-mono font-extrabold rounded cursor-pointer transition-colors ${
                transposeOffset !== 0 ? 'text-[#B58900]' : 'text-[#EEE8D5] hover:bg-[#073642]'
              }`}
              title="Select Target Key"
            >
              <span>
                {transposeOffset !== 0
                  ? `Key: ${effectiveKey} (${offsetStr})`
                  : `Key: ${effectiveKey || 'Orig'}`}
              </span>
              <ChevronDown className="w-3 h-3 opacity-75" />
            </button>

            {transposeOffset !== 0 && (
              <button
                type="button"
                onClick={() => onTransposeChange(0)}
                className="p-1 text-[#93A1A1] hover:text-[#DC6E67] cursor-pointer"
                title="Reset Transposition to Original Key"
              >
                <RotateCcw className="w-3 h-3" />
              </button>
            )}

            <button
              type="button"
              onClick={() => onTransposeChange(transposeOffset + 1)}
              className="p-1 text-[#EEE8D5] hover:text-[#2AA198] rounded cursor-pointer"
              title="Transpose Up (+1)"
            >
              <Plus className="w-3.5 h-3.5" />
            </button>
          </div>

          {/* Band Sync Status Indicator & Modal Trigger */}
          <button
            type="button"
            onClick={onOpenBandSync || (() => setIsBandSyncModalOpen(true))}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-xs font-mono font-bold transition-all cursor-pointer shadow-sm ${
              syncState.role === 'HOST'
                ? 'bg-[#10B981]/20 border-[#10B981] text-[#10B981]'
                : syncState.role === 'CLIENT'
                ? 'bg-[#2AA198]/20 border-[#2AA198] text-[#2AA198]'
                : 'bg-[#002B36] border-[#1A4A55] text-[#93A1A1] hover:text-[#EEE8D5]'
            }`}
            title={
              syncState.role === 'HOST'
                ? `Band Leader (Active) • ${syncState.connectedPeers} member(s) connected`
                : syncState.role === 'CLIENT'
                ? 'Band Member (Synced to Leader)'
                : 'Band Sync (Click to connect devices)'
            }
          >
            {syncState.role === 'HOST' ? (
              <>
                <Radio className="w-3.5 h-3.5 animate-pulse text-[#10B981]" />
                <span className="hidden md:inline">LEADER</span>
                <span className="text-[10px] px-1 py-0.2 rounded bg-[#10B981]/30">
                  {syncState.connectedPeers}
                </span>
              </>
            ) : syncState.role === 'CLIENT' ? (
              <>
                <Users className="w-3.5 h-3.5 animate-pulse text-[#2AA198]" />
                <span className="hidden md:inline">SYNCED</span>
              </>
            ) : (
              <>
                <Wifi className="w-3.5 h-3.5" />
                <span className="hidden md:inline">Sync</span>
              </>
            )}
          </button>
          {/* Cast / Pop-out Screen (Mirror distraction-free stage teleprompter to external display) */}
          <button
            type="button"
            onClick={() => {
              if (isCastActive) {
                stageCast.stopPresentation()
              } else {
                stageCast.broadcastState({
                  song,
                  effectiveKey,
                  transposeOffset,
                  fontSizePx,
                  fontStyle,
                  isTwoColumn,
                })
                stageCast.openPresentationWindow()
              }
            }}
            className={`p-2 rounded-lg border transition-all cursor-pointer flex items-center gap-1.5 ${
              isCastActive
                ? 'bg-[#DC6E67]/20 border-[#DC6E67] text-[#DC6E67] hover:bg-[#DC6E67]/30 shadow-sm animate-pulse'
                : 'bg-[#002B36] border-[#1A4A55] text-[#EEE8D5] hover:text-[#2AA198] hover:border-[#2AA198]'
            }`}
            title={
              isCastActive
                ? 'Disconnect / Stop Presenting (Session Active - click to terminate)'
                : 'Cast / Pop-out Screen (Open distraction-free fullscreen teleprompter on secondary monitor or TV)'
            }
          >
            <Cast className="w-4 h-4" />
            {isCastActive && (
              <span className="hidden xl:inline text-[10px] font-mono font-bold uppercase tracking-wider">
                Stop
              </span>
            )}
          </button>

          {/* Stage Focus Mode (Fullscreen) */}
          <button
            type="button"
            onClick={toggleFullscreen}
            className="p-2 rounded-lg bg-[#002B36] border border-[#1A4A55] text-[#EEE8D5] hover:text-[#2AA198] transition-colors cursor-pointer"
            title="Toggle Fullscreen"
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {/* =================================================================== */}
      {/* 2. MAIN SCROLLING CANVAS (Exact SongLinesColumn from Compose)        */}
      {/* =================================================================== */}
      <div
        ref={scrollContainerRef}
        onScroll={handleContainerScroll}
        className="flex-1 overflow-y-auto px-4 sm:px-6 md:px-8 py-4 select-text scroll-smooth"
      >
        <div className={`mx-auto transition-all ${isTwoColumn ? 'max-w-[95vw]' : 'max-w-4xl'}`}>
          {/* Metadata Header Badges (Key, Capo, 2 Columns / Format) */}
          <div className="flex items-center gap-2 pb-3 flex-wrap">
            {effectiveKey && <MetaBadge label={`KEY: ${effectiveKey}`} />}
            {song.capo &&
              song.capo.toLowerCase() !== 'no capo' &&
              song.capo.toLowerCase() !== 'none' && (
                <MetaBadge
                  label={
                    song.capo.toLowerCase().startsWith('capo')
                      ? song.capo.toUpperCase()
                      : `CAPO: ${song.capo}`
                  }
                />
              )}
            <MetaBadge label={isTwoColumn ? '2 COLUMNS' : '1 COLUMN'} />
          </div>

          {/* HorizontalDivider (color = customColors.divider, thickness = 1.dp) */}
          <div className="h-[1px] bg-[#1A4A55] mb-4" />

          {/* Song Lines Rendering: 1 Column or 2 Columns */}
          {isTwoColumn && col2Lines.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 lg:gap-10 items-start">
              <div className="min-w-0">
                <SongLineRenderer
                  lines={col1Lines}
                  fontSizePx={fontSizePx}
                  fontFamily={fontStyle}
                  onChordClick={handleChordClick}
                />
              </div>

              <div className="min-w-0 md:border-l md:border-[#1A4A55]/60 md:pl-6 lg:pl-10">
                <SongLineRenderer
                  lines={col2Lines}
                  fontSizePx={fontSizePx}
                  fontFamily={fontStyle}
                  onChordClick={handleChordClick}
                />
              </div>
            </div>
          ) : (
            <SongLineRenderer
              lines={parsedSong.lines}
              fontSizePx={fontSizePx}
              fontFamily={fontStyle}
              onChordClick={handleChordClick}
            />
          )}

          {/* Bottom Padding for scroll clearance (Spacer(height = 140.dp)) */}
          <div className="h-44 flex items-center justify-center text-xs font-mono text-[#1A4A55] select-none">
            — End of Song —
          </div>
        </div>
      </div>

      {/* =================================================================== */}
      {/* 3. BOTTOM BAR (Gig Navigation Strip & Floating Glassmorphic Stage)   */}
      {/* =================================================================== */}
      <div className="absolute bottom-4 sm:bottom-6 right-4 sm:right-6 z-30 flex flex-col items-end gap-2.5 pointer-events-none">
        {/* Dynamic Gig Performance Navigation Strip (Strictly navigates within current active scope) */}
        {((isInSetlistMode && activeSetlistSongs.length > 1) ||
          (!isInSetlistMode && songs.length > 1)) && (
          <div
            className="pointer-events-auto flex items-center justify-between gap-2.5 px-3 py-1.5 rounded-2xl bg-[#073642]/95 border backdrop-blur-md shadow-xl text-xs font-mono select-none"
            style={{
              borderColor: isInSetlistMode ? 'rgba(181, 137, 0, 0.45)' : 'rgba(42, 161, 152, 0.45)',
            }}
          >
            <button
              type="button"
              disabled={isInSetlistMode ? activeSetlistSongIndex <= 0 : activeSongIndex <= 0}
              onClick={() => {
                if (isInSetlistMode && onSelectSetlistSongIndex) {
                  onSelectSetlistSongIndex(activeSetlistSongIndex - 1)
                } else if (!isInSetlistMode) {
                  onSelectSongIndex(activeSongIndex - 1)
                }
              }}
              className={`flex items-center gap-1 font-bold transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed ${
                isInSetlistMode
                  ? 'text-[#B58900] hover:text-[#2AA198]'
                  : 'text-[#2AA198] hover:text-[#FDF6E3]'
              }`}
              title={
                isInSetlistMode
                  ? "Previous Song in Setlist (ArrowLeft or 'p')"
                  : "Previous Song in Library (ArrowLeft or 'p')"
              }
            >
              <SkipBack className="w-3.5 h-3.5" />
              <span>PREV</span>
            </button>

            <button
              type="button"
              onClick={onOpenSetlistDrawer}
              className={`px-2.5 py-1 rounded-lg font-extrabold text-[11px] flex items-center gap-1.5 transition-colors cursor-pointer ${
                isInSetlistMode
                  ? 'bg-[#B58900]/15 text-[#B58900] hover:bg-[#B58900]/25'
                  : 'bg-[#2AA198]/20 text-[#2AA198] hover:bg-[#2AA198]/30'
              }`}
              title="Click to open setlists and library drawer"
            >
              {isInSetlistMode ? (
                <ListMusic className="w-3.5 h-3.5" />
              ) : (
                <BookOpen className="w-3.5 h-3.5" />
              )}
              <span>
                {isInSetlistMode
                  ? `SETLIST ${activeSetlistSongIndex + 1}/${activeSetlistSongs.length || 1}`
                  : `LIBRARY ${activeSongIndex + 1}/${songs.length}`}
              </span>
            </button>

            <button
              type="button"
              disabled={
                isInSetlistMode
                  ? activeSetlistSongIndex >= activeSetlistSongs.length - 1
                  : activeSongIndex >= songs.length - 1
              }
              onClick={() => {
                if (isInSetlistMode && onSelectSetlistSongIndex) {
                  onSelectSetlistSongIndex(activeSetlistSongIndex + 1)
                } else if (!isInSetlistMode) {
                  onSelectSongIndex(activeSongIndex + 1)
                }
              }}
              className={`flex items-center gap-1 font-bold transition-colors cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed ${
                isInSetlistMode
                  ? 'text-[#B58900] hover:text-[#2AA198]'
                  : 'text-[#2AA198] hover:text-[#FDF6E3]'
              }`}
              title={
                isInSetlistMode
                  ? "Next Song in Setlist (ArrowRight or 'n')"
                  : "Next Song in Library (ArrowRight or 'n')"
              }
            >
              <span>NEXT</span>
              <SkipForward className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        {/* Floating Glassmorphic Stage Controller (Compose lines 742-848) */}
        <div className="pointer-events-auto flex items-center gap-2.5 sm:gap-3 bg-[#073642]/95 backdrop-blur-md px-3 sm:px-4 py-2 rounded-2xl border border-[#1A4A55] shadow-2xl shadow-black/80">
          {/* Primary Stage Play/Pause Action Button */}
          <button
            type="button"
            onClick={handleToggleAutoScroll}
            className={`flex items-center gap-2 px-4 sm:px-5 py-2.5 rounded-xl font-black text-xs tracking-wider transition-all cursor-pointer shadow-lg active:scale-95 select-none ${
              isAutoScrolling
                ? 'bg-[#EF4444] text-white hover:bg-[#DC2626]'
                : 'bg-[#B58900] text-black hover:bg-[#C89600]'
            }`}
          >
            {isAutoScrolling ? (
              <>
                <Pause className="w-4 h-4 fill-current" />
                <span>PAUSE</span>
              </>
            ) : (
              <>
                <Play className="w-4 h-4 fill-current" />
                <span>SCROLL</span>
              </>
            )}
          </button>

          {/* Pro dp/s (px/s) Speed Controls: (-) [35 dp/s] (+) */}
          <div className="flex items-center bg-[#002B36] rounded-xl px-1.5 py-1 border border-[#1A4A55]/60">
            <button
              type="button"
              onClick={() => handleAdjustSpeed(Math.max(5, scrollSpeed - 2))}
              className="p-1.5 text-[#EEE8D5] hover:text-[#2AA198] rounded cursor-pointer"
              title="Slower (-2 dp/s)"
            >
              <Minus className="w-3.5 h-3.5" />
            </button>

            {/* Center Speed Badge: Tap to type */}
            <div
              onClick={() => {
                setSpeedInputText(scrollSpeed.toString())
                setIsSpeedPromptOpen(true)
              }}
              className="px-2.5 py-0.5 rounded-lg bg-[#073642] border border-[#B58900]/50 text-center cursor-pointer hover:border-[#2AA198] transition-colors mx-1 select-none"
              title="Click to type exact scroll speed"
            >
              <div className="text-xs font-mono font-extrabold text-[#B58900] leading-tight">
                {scrollSpeed} dp/s
              </div>
              <div className="text-[9px] text-[#93A1A1] leading-none">Tap to type</div>
            </div>

            <button
              type="button"
              onClick={() => handleAdjustSpeed(Math.min(180, scrollSpeed + 2))}
              className="p-1.5 text-[#EEE8D5] hover:text-[#2AA198] rounded cursor-pointer"
              title="Faster (+2 dp/s)"
            >
              <Plus className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* Tap to type custom scroll speed popup */}
      {isSpeedPromptOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
          onClick={() => setIsSpeedPromptOpen(false)}
        >
          <form
            onSubmit={handleCustomSpeedSubmit}
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-xs rounded-2xl bg-[#073642] border border-[#1A4A55] p-5 shadow-2xl text-[#EEE8D5]"
          >
            <h3 className="text-base font-bold text-[#B58900] mb-2">Set Scroll Speed</h3>
            <p className="text-xs text-[#93A1A1] mb-4">
              Enter scroll speed in dp/s (pixels per second, 5 - 180):
            </p>
            <input
              type="number"
              min="5"
              max="180"
              autoFocus
              value={speedInputText}
              onChange={(e) => setSpeedInputText(e.target.value)}
              className="w-full px-3 py-2 rounded-xl bg-[#002B36] border border-[#1A4A55] text-center text-lg font-mono font-bold text-[#B58900] focus:outline-none focus:border-[#2AA198]"
            />
            <div className="flex items-center justify-end gap-2 mt-4">
              <button
                type="button"
                onClick={() => setIsSpeedPromptOpen(false)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold text-[#93A1A1] hover:text-[#EEE8D5]"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-1.5 rounded-lg bg-[#2AA198] text-[#002B36] text-xs font-bold hover:bg-[#35B8AD]"
              >
                Apply
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Fretboard Diagram Modal (1:1 Android FretboardDiagramDialog.kt) */}
      <FretboardDiagramModal
        voicing={selectedVoicing}
        onClose={() => setSelectedVoicing(null)}
      />

      {/* Key & Transpose Picker Modal */}
      <KeyPickerModal
        isOpen={isKeyPickerOpen}
        onClose={() => setIsKeyPickerOpen(false)}
        originalKey={song.key}
        currentOffset={transposeOffset}
        capoText={song.capo}
        onSelectOffset={onTransposeChange}
        onReset={() => onTransposeChange(0)}
      />

      {/* Stage Tools & Band Sync Modal */}
      <BandSyncModal
        isOpen={isBandSyncModalOpen}
        onClose={() => setIsBandSyncModalOpen(false)}
        initialTab="sync"
      />
    </div>
  )
}
