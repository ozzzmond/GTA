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
  SlidersHorizontal,
  SkipBack,
  SkipForward
} from 'lucide-react'
import { transposeKey, formatTransposeOffset } from '../utils/chordTransposer'
import { parseGtarSong, splitSongLinesForColumns } from '../utils/songParser'
import { metronome, type MetronomeState } from '../utils/metronome'
import { getChordVoicing, type ChordVoicing } from '../utils/chordDictionary'
import { SongLineRenderer } from './SongLineRenderer'
import { KeyPickerModal } from './KeyPickerModal'
import { FretboardDiagramModal } from './FretboardDiagramModal'
import type { ActiveSongState } from '../types/gtar'

interface StageViewProps {
  song: ActiveSongState
  songs: ActiveSongState[]
  activeSongIndex: number
  onSelectSongIndex: (index: number) => void
  onOpenSetlistDrawer: () => void
  transposeOffset: number
  onTransposeChange: (offset: number) => void
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
  onOpenSetlistDrawer,
  transposeOffset,
  onTransposeChange,
}) => {
  // Stage view configuration & controls (matching Jetpack Compose SongViewerScreen.kt)
  const [isAutoScrolling, setIsAutoScrolling] = useState(false)
  const [scrollSpeed, setScrollSpeed] = useState(35) // continuous dp/s / px/s (10 to 150)
  const [fontSizePx, setFontSizePx] = useState(20) // 13px to 36px
  const [fontStyle, setFontStyle] = useState<'mono' | 'sans' | 'serif'>('mono')
  const [isTwoColumn, setIsTwoColumn] = useState(false)
  const [isFullscreen, setIsFullscreen] = useState(false)

  // Modals & Drawers
  const [isKeyPickerOpen, setIsKeyPickerOpen] = useState(false)
  const [selectedVoicing, setSelectedVoicing] = useState<ChordVoicing | null>(null)
  const [isSpeedPromptOpen, setIsSpeedPromptOpen] = useState(false)
  const [speedInputText, setSpeedInputText] = useState('35')

  // Metronome State & Beat pulse (matching MetronomeEngine state in Android)
  const [metroState, setMetroState] = useState<MetronomeState>(() => metronome.getState())
  const [activeBeat, setActiveBeat] = useState<number>(1)
  const [isBeatFlash, setIsBeatFlash] = useState(false)

  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const scrollAnimRef = useRef<number | null>(null)

  // Parse song with native v1.0.40 parser and active transpose offset
  const parsedSong = parseGtarSong(song.rawContent, transposeOffset)

  // Metronome subscription & sync with song BPM
  useEffect(() => {
    const songBpmNum = parseInt(song.bpm, 10)
    if (!isNaN(songBpmNum) && songBpmNum >= 30 && songBpmNum <= 300) {
      metronome.setBpm(songBpmNum)
    }

    const unsubState = metronome.subscribe((st) => {
      setMetroState(st)
    })

    const unsubBeat = metronome.onBeat((beat) => {
      setActiveBeat(beat)
      setIsBeatFlash(true)
      setTimeout(() => setIsBeatFlash(false), 120)
    })

    return () => {
      unsubState()
      unsubBeat()
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
        setIsAutoScrolling((prev) => !prev)
        return
      }

      // ArrowRight or 'n': Next song in setlist
      if (e.key === 'ArrowRight' || e.key === 'n' || e.key === 'N') {
        e.preventDefault()
        if (activeSongIndex < songs.length - 1) {
          onSelectSongIndex(activeSongIndex + 1)
        }
        return
      }

      // ArrowLeft or 'p': Previous song in setlist
      if (e.key === 'ArrowLeft' || e.key === 'p' || e.key === 'P') {
        e.preventDefault()
        if (activeSongIndex > 0) {
          onSelectSongIndex(activeSongIndex - 1)
        }
        return
      }

      // ArrowUp: Manually nudge scroll up or adjust scroll speed (with Shift)
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        if (e.shiftKey) {
          setScrollSpeed((prev) => Math.min(180, prev + 5))
        } else if (scrollContainerRef.current) {
          scrollContainerRef.current.scrollBy({ top: -80, behavior: 'smooth' })
        }
        return
      }

      // ArrowDown: Manually nudge scroll down or adjust scroll speed (with Shift)
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        if (e.shiftKey) {
          setScrollSpeed((prev) => Math.max(5, prev - 5))
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
  }, [activeSongIndex, songs.length, onSelectSongIndex])

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
      setScrollSpeed(val)
    }
    setIsSpeedPromptOpen(false)
  }

  return (
    <div className="flex-1 flex flex-col h-[calc(100vh-3.5rem)] bg-[#002B36] select-none relative overflow-hidden">
      {/* =================================================================== */}
      {/* 1. TOP APP BAR (Exact 1:1 Jetpack Compose SongViewerScreen.kt)       */}
      {/* =================================================================== */}
      <div className="border-b border-[#1A4A55] bg-[#073642] px-4 sm:px-6 py-2 flex flex-wrap items-center justify-between gap-3 z-20 shadow-md">
        {/* Left Side: Navigation / Setlist Trigger + Title & Artist */}
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onOpenSetlistDrawer}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#002B36] border border-[#1A4A55] text-[#EEE8D5] hover:border-[#2AA198] hover:text-[#2AA198] text-xs font-bold transition-all cursor-pointer shadow-sm"
            title="Open Stage Setlist Drawer"
          >
            <ListMusic className="w-4 h-4 text-[#2AA198]" />
            <span className="hidden sm:inline">Setlist</span>
            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[#2AA198]/20 text-[#2AA198]">
              {activeSongIndex + 1}/{songs.length}
            </span>
          </button>

          {/* Title & Artist (Compose typography) */}
          <div className="min-w-0">
            <h1 className="text-base sm:text-lg font-extrabold text-[#EEE8D5] tracking-tight leading-tight truncate max-w-[200px] sm:max-w-xs md:max-w-md">
              {song.title || 'Untitled Song'}
            </h1>
            {song.artist && (
              <p className="text-[11px] sm:text-xs text-[#2AA198] font-semibold truncate leading-none mt-0.5">
                {song.artist}
              </p>
            )}
          </div>
        </div>

        {/* Right Side: Font Family, Font Size A-/A+, Column Reflow, Transpose Stepper, Stage Tools, Fullscreen */}
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
              onClick={() => setFontSizePx((prev) => Math.max(13, prev - 1))}
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
              onClick={() => setFontSizePx((prev) => Math.min(36, prev + 1))}
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

          {/* Stage Tools (Metronome & Guitar Tuner with pulsing green LED dot) */}
          <button
            type="button"
            onClick={() => metronome.toggle()}
            className="relative p-2 rounded-lg bg-[#002B36] border border-[#1A4A55] text-[#EEE8D5] hover:text-[#2AA198] transition-colors cursor-pointer"
            title="Toggle Stage Metronome (Audio & Visual)"
          >
            <SlidersHorizontal
              className={`w-4 h-4 ${metroState.isRunning ? 'text-[#10B981]' : 'text-[#B58900]'}`}
            />
            {metroState.isRunning && (
              <span
                className={`absolute top-1 right-1 w-2 h-2 rounded-full transition-all ${
                  isBeatFlash
                    ? activeBeat === 1
                      ? 'bg-[#B58900] scale-125 shadow-sm shadow-[#B58900]'
                      : 'bg-[#10B981] scale-110 shadow-sm shadow-[#10B981]'
                    : 'bg-[#10B981]'
                }`}
              />
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
            <MetaBadge label={isTwoColumn ? '2 COLUMNS' : parsedSong.format.replace('_', ' ')} />
            {song.bpm && <MetaBadge label={`${song.bpm} BPM`} />}
          </div>

          {/* HorizontalDivider (color = customColors.divider, thickness = 1.dp) */}
          <div className="h-[1px] bg-[#1A4A55] mb-4" />

          {/* Song Lines Rendering: 1 Column or 2 Columns */}
          {isTwoColumn && col2Lines.length > 0 ? (
            <div className="flex gap-6 lg:gap-10 items-start">
              <div className="flex-1 min-w-0">
                <SongLineRenderer
                  lines={col1Lines}
                  fontSizePx={fontSizePx}
                  fontFamily={fontStyle}
                  onChordClick={handleChordClick}
                />
              </div>

              {/* VerticalDivider (color = divider.copy(alpha = 0.5f), thickness = 1.dp) */}
              <div className="w-[1px] bg-[#1A4A55]/50 self-stretch my-1" />

              <div className="flex-1 min-w-0">
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
        {/* Gig Performance Navigation Strip (Only shown when multiple songs exist) */}
        {songs.length > 1 && (
          <div className="pointer-events-auto flex items-center justify-between gap-3 px-3 py-1.5 rounded-2xl bg-[#073642]/95 border border-[#B58900]/40 backdrop-blur-md shadow-xl text-xs font-mono">
            <button
              type="button"
              disabled={activeSongIndex <= 0}
              onClick={() => onSelectSongIndex(activeSongIndex - 1)}
              className="flex items-center gap-1 text-[#B58900] disabled:opacity-30 disabled:hover:text-[#B58900] font-bold hover:text-[#2AA198] transition-colors cursor-pointer"
              title="Previous Song in Setlist (Alt + Left)"
            >
              <SkipBack className="w-3.5 h-3.5" />
              <span>PREV</span>
            </button>

            <button
              type="button"
              onClick={onOpenSetlistDrawer}
              className="px-2 py-0.5 rounded-md bg-[#B58900]/15 text-[#B58900] font-extrabold text-[11px] flex items-center gap-1.5 hover:bg-[#B58900]/25 transition-colors cursor-pointer"
              title="Open Setlist Drawer"
            >
              <ListMusic className="w-3.5 h-3.5" />
              SETLIST {activeSongIndex + 1}/{songs.length}
            </button>

            <button
              type="button"
              disabled={activeSongIndex >= songs.length - 1}
              onClick={() => onSelectSongIndex(activeSongIndex + 1)}
              className="flex items-center gap-1 text-[#B58900] disabled:opacity-30 disabled:hover:text-[#B58900] font-bold hover:text-[#2AA198] transition-colors cursor-pointer"
              title="Next Song in Setlist (Alt + Right)"
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
            onClick={() => setIsAutoScrolling(!isAutoScrolling)}
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
              onClick={() => setScrollSpeed((prev) => Math.max(5, prev - 2))}
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
              onClick={() => setScrollSpeed((prev) => Math.min(180, prev + 2))}
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
    </div>
  )
}
