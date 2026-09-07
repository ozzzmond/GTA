import React, { useState, useEffect } from 'react'
import {
  Guitar,
  FileEdit,
  Eye,
  ArrowDownUp,
  RotateCcw,
  Download,
  Upload,
  BookOpen,
  Lock,
  ChevronDown,
  ListMusic
} from 'lucide-react'
import { transposeKey, formatTransposeOffset } from '../utils/chordTransposer'
import { SAMPLE_SONGS } from '../utils/chordSheetParser'
import type { ActiveSongState } from '../types/gtar'

interface HeaderProps {
  activeView: 'editor' | 'stage'
  onViewChange: (view: 'editor' | 'stage') => void
  song: ActiveSongState
  songsCount?: number
  activeSongIndex?: number
  transposeOffset: number
  onTransposeChange: (offset: number) => void
  onOpenKeyPicker?: () => void
  onOpenSetlistDrawer?: () => void
  onOpenExportModal: () => void
  onOpenImportModal: () => void
  onLoadSong: (song: typeof SAMPLE_SONGS.standByMe) => void
  onLockApp: () => void
}

export const Header: React.FC<HeaderProps> = ({
  activeView,
  onViewChange,
  song,
  songsCount,
  activeSongIndex,
  transposeOffset,
  onTransposeChange,
  onOpenKeyPicker,
  onOpenSetlistDrawer,
  onOpenExportModal,
  onOpenImportModal,
  onLoadSong,
  onLockApp,
}) => {
  const [currentTime, setCurrentTime] = useState('')
  const [showSamplesMenu, setShowSamplesMenu] = useState(false)

  useEffect(() => {
    const updateClock = () => {
      const now = new Date()
      setCurrentTime(
        now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
      )
    }
    updateClock()
    const timer = setInterval(updateClock, 1000)
    return () => clearInterval(timer)
  }, [])

  const effectiveKey = song.key ? transposeKey(song.key, transposeOffset) : '-'

  return (
    <header className="h-14 border-b border-[#1A4A55] bg-[#073642] px-4 flex items-center justify-between select-none z-30 sticky top-0">
      {/* Left: Branding & Clock */}
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-[#002B36] border border-[#2AA198]/40 flex items-center justify-center text-[#2AA198]">
            <Guitar className="w-4 h-4" />
          </div>
          <div>
            <div className="flex items-center gap-1.5 leading-none">
              <span className="font-bold text-sm text-[#FDF6E3] tracking-wide">GTAR</span>
              <span className="text-[10px] font-mono uppercase bg-[#2AA198]/15 text-[#2AA198] px-1.5 py-0.5 rounded border border-[#2AA198]/30">
                v1.0.41
              </span>
            </div>
          </div>
        </div>


        <div className="h-5 w-[1px] bg-[#1A4A55] mx-1 hidden sm:block" />

        <div className="hidden sm:flex items-center font-mono text-xs text-[#93A1A1] bg-[#002B36] px-2.5 py-1 rounded-md border border-[#1A4A55]/70">
          <span className="text-[#2AA198] mr-1.5">STAGE CLOCK</span>
          <span className="text-[#FDF6E3] font-semibold">{currentTime}</span>
        </div>

        {onOpenSetlistDrawer && (
          <button
            type="button"
            onClick={onOpenSetlistDrawer}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#002B36] border border-[#1A4A55] text-[#EEE8D5] hover:border-[#2AA198] hover:text-[#2AA198] text-xs font-bold transition-all cursor-pointer shadow-sm"
            title="Open Setlist & Song Library Drawer"
          >
            <ListMusic className="w-4 h-4 text-[#2AA198]" />
            <span className="hidden md:inline">Library</span>
            {songsCount !== undefined && (
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[#2AA198]/20 text-[#2AA198]">
                {activeSongIndex !== undefined ? activeSongIndex + 1 : 1}/{songsCount}
              </span>
            )}
          </button>
        )}
      </div>

      {/* Middle: View Mode Switcher */}
      <div className="flex items-center bg-[#002B36] p-1 rounded-xl border border-[#1A4A55]">
        <button
          type="button"
          onClick={() => onViewChange('editor')}
          className={`flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-medium transition-all cursor-pointer ${
            activeView === 'editor'
              ? 'bg-[#2AA198] text-[#002B36] font-bold shadow-sm'
              : 'text-[#93A1A1] hover:text-[#FDF6E3]'
          }`}
        >
          <FileEdit className="w-3.5 h-3.5" />
          <span>Desktop Editor</span>
        </button>
        <button
          type="button"
          onClick={() => onViewChange('stage')}
          className={`flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-medium transition-all cursor-pointer ${
            activeView === 'stage'
              ? 'bg-[#B58900] text-[#002B36] font-bold shadow-sm'
              : 'text-[#93A1A1] hover:text-[#FDF6E3]'
          }`}
        >
          <Eye className="w-3.5 h-3.5" />
          <span>Stage View (1:1)</span>
        </button>
      </div>

      {/* Right: Transposition, Samples, Export/Import, Lock */}
      <div className="flex items-center gap-2">
        {/* Quick Transpose Cluster */}
        <div className="flex items-center bg-[#002B36] rounded-lg border border-[#1A4A55] p-0.5">
          <button
            type="button"
            title="Transpose Down 1 Semitone (-1)"
            onClick={() => onTransposeChange(transposeOffset - 1)}
            className="px-2 py-1 text-xs font-mono font-bold text-[#93A1A1] hover:text-[#2AA198] hover:bg-[#073642] rounded transition-colors cursor-pointer"
          >
            -1
          </button>

          <button
            type="button"
            onClick={onOpenKeyPicker}
            title={`Click to open Key Picker | Offset: ${formatTransposeOffset(transposeOffset)} | Original: ${song.key || 'N/A'} -> Transposed: ${effectiveKey}`}
            className="flex items-center gap-1 px-2 py-0.5 rounded text-xs font-mono text-[#FDF6E3] hover:bg-[#073642] cursor-pointer transition-colors"
          >
            <ArrowDownUp className="w-3 h-3 text-[#2AA198]" />
            <span className="text-[#B58900] font-bold">{effectiveKey}</span>
            {transposeOffset !== 0 && (
              <span className="text-[10px] text-[#93A1A1]">({formatTransposeOffset(transposeOffset)})</span>
            )}
          </button>


          {transposeOffset !== 0 && (
            <button
              type="button"
              title="Reset Transposition (0)"
              onClick={() => onTransposeChange(0)}
              className="p-1 text-[#93A1A1] hover:text-[#DC6E67] hover:bg-[#073642] rounded transition-colors cursor-pointer"
            >
              <RotateCcw className="w-3 h-3" />
            </button>
          )}

          <button
            type="button"
            title="Transpose Up 1 Semitone (+1)"
            onClick={() => onTransposeChange(transposeOffset + 1)}
            className="px-2 py-1 text-xs font-mono font-bold text-[#93A1A1] hover:text-[#2AA198] hover:bg-[#073642] rounded transition-colors cursor-pointer"
          >
            +1
          </button>
        </div>

        {/* Sample Songs Dropdown */}
        <div className="relative">
          <button
            type="button"
            onClick={() => setShowSamplesMenu(!showSamplesMenu)}
            className="hidden md:flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-[#1A4A55] bg-[#002B36] text-xs text-[#EEE8D5] hover:border-[#2AA198] transition-colors cursor-pointer"
          >
            <BookOpen className="w-3.5 h-3.5 text-[#2AA198]" />
            <span>Samples</span>
            <ChevronDown className="w-3 h-3 text-[#93A1A1]" />
          </button>

          {showSamplesMenu && (
            <div className="absolute right-0 mt-1 w-56 rounded-xl border border-[#1A4A55] bg-[#073642] shadow-xl py-1 z-50">
              <div className="px-3 py-1.5 text-[10px] font-mono text-[#93A1A1] border-b border-[#1A4A55]/50 uppercase tracking-wider">
                GTAR Default Songs
              </div>
              <button
                type="button"
                onClick={() => {
                  onLoadSong(SAMPLE_SONGS.standByMe)
                  setShowSamplesMenu(false)
                }}
                className="w-full text-left px-3 py-2 text-xs text-[#FDF6E3] hover:bg-[#002B36] transition-colors flex flex-col cursor-pointer"
              >
                <span className="font-semibold text-[#2AA198]">Stand By Me</span>
                <span className="text-[11px] text-[#93A1A1]">Ben E. King (ChordPro)</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  onLoadSong(SAMPLE_SONGS.elBimbo)
                  setShowSamplesMenu(false)
                }}
                className="w-full text-left px-3 py-2 text-xs text-[#FDF6E3] hover:bg-[#002B36] transition-colors flex flex-col cursor-pointer"
              >
                <span className="font-semibold text-[#B58900]">Ang Huling El Bimbo</span>
                <span className="text-[11px] text-[#93A1A1]">Eraserheads (2-Line Tabs)</span>
              </button>
            </div>
          )}
        </div>

        {/* JSON Bridge Import & Export Buttons */}
        <button
          type="button"
          onClick={onOpenImportModal}
          title="Import JSON (Room Song / GTAR Setlist)"
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-[#1A4A55] bg-[#002B36] text-xs text-[#EEE8D5] hover:border-[#2AA198] hover:text-[#2AA198] transition-colors cursor-pointer"
        >
          <Upload className="w-3.5 h-3.5 text-[#2AA198]" />
          <span className="hidden lg:inline">Import</span>
        </button>

        <button
          type="button"
          onClick={onOpenExportModal}
          title="Export JSON (Room Song / GTAR Setlist)"
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-[#1A4A55] bg-[#002B36] text-xs text-[#EEE8D5] hover:border-[#B58900] hover:text-[#B58900] transition-colors cursor-pointer"
        >
          <Download className="w-3.5 h-3.5 text-[#B58900]" />
          <span className="hidden lg:inline">Export</span>
        </button>

        {/* Lock App Button */}
        <button
          type="button"
          onClick={onLockApp}
          title="Lock Workspace"
          className="p-1.5 rounded-lg border border-[#1A4A55] bg-[#002B36] text-[#93A1A1] hover:text-[#DC6E67] hover:border-[#DC6E67]/50 transition-colors cursor-pointer ml-1"
        >
          <Lock className="w-3.5 h-3.5" />
        </button>
      </div>
    </header>
  )
}
