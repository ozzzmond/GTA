import React, { useState, useRef, useEffect } from 'react'
import {
  Guitar,
  Search,
  X,
  Globe,
  SlidersHorizontal,
  Palette,
  MoreVertical,
  Settings,
  FolderOpen,
  CloudUpload,
  RefreshCw,
  Eye,
  FileEdit,
  ListMusic,
} from 'lucide-react'
import type { ActiveSongState } from '../types/gtar'

interface HeaderProps {
  activeView: 'editor' | 'stage'
  onViewChange: (view: 'editor' | 'stage') => void
  song: ActiveSongState
  songsCount?: number
  activeSongIndex?: number
  queueMode?: 'library' | 'setlist'
  activeSetlistSongsCount?: number
  activeSetlistSongIndex?: number
  searchQuery: string
  onSearchQueryChange: (query: string) => void
  onOpenWebsiteUrlSource: () => void
  onOpenStageTools: () => void
  onToggleTheme: () => void
  onOpenStageSettings: () => void
  onOpenImportModal: () => void
  onOpenBackupRestoreModal: () => void
  onCheckForUpdates: () => void
  isCheckingUpdates?: boolean
  onOpenSetlistDrawer?: () => void
}

export const Header: React.FC<HeaderProps> = ({
  activeView,
  onViewChange,
  songsCount,
  activeSongIndex,
  queueMode = 'library',
  activeSetlistSongsCount,
  activeSetlistSongIndex,
  searchQuery,
  onSearchQueryChange,
  onOpenWebsiteUrlSource,
  onOpenStageTools,
  onToggleTheme,
  onOpenStageSettings,
  onOpenImportModal,
  onOpenBackupRestoreModal,
  onCheckForUpdates,
  isCheckingUpdates = false,
  onOpenSetlistDrawer,
}) => {
  const [showOverflowMenu, setShowOverflowMenu] = useState(false)
  const overflowMenuRef = useRef<HTMLDivElement>(null)

  // Close overflow dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (overflowMenuRef.current && !overflowMenuRef.current.contains(e.target as Node)) {
        setShowOverflowMenu(false)
      }
    }
    if (showOverflowMenu) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [showOverflowMenu])

  return (
    <header className="h-16 border-b border-[#1A4A55] bg-[#073642] px-3 sm:px-5 flex items-center justify-between gap-2 sm:gap-4 select-none z-30 sticky top-0 shadow-md">
      {/* =================================================================== */}
      {/* 1. LEFT: App Branding & v1.0.45 Badge (1:1 Android TopAppBar)        */}
      {/* =================================================================== */}
      <div className="flex items-center gap-2 sm:gap-3 shrink-0">
        <div className="flex items-center gap-2">
          <div className="w-9 h-9 rounded-xl bg-[#002B36] border border-[#2AA198]/40 flex items-center justify-center text-[#2AA198] shadow-inner">
            <Guitar className="w-5 h-5" />
          </div>
          <div className="flex flex-col">
            <div className="flex items-center gap-1.5 leading-none">
              <span className="font-black text-base text-[#FDF6E3] tracking-wide">GTAR</span>
              <button
                type="button"
                onClick={onCheckForUpdates}
                title="Click to check for updates (v1.0.45)"
                className="text-[10px] font-mono font-bold uppercase bg-[#002B36] text-[#2AA198] px-1.5 py-0.5 rounded border border-[#1A4A55] hover:border-[#2AA198] transition-colors cursor-pointer flex items-center gap-1"
              >
                {isCheckingUpdates && (
                  <RefreshCw className="w-2.5 h-2.5 animate-spin text-[#B58900]" />
                )}
                <span>v1.0.45</span>
              </button>
            </div>
            <span className="hidden md:inline text-[10px] text-[#93A1A1] mt-0.5 font-medium leading-none">
              Guitar Tool App Republic
            </span>
          </div>
        </div>

        {/* Setlist Library Quick Trigger */}
        {onOpenSetlistDrawer && (
          <button
            type="button"
            onClick={onOpenSetlistDrawer}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl bg-[#002B36] border text-xs font-bold transition-all cursor-pointer shadow-sm ml-1 ${
              queueMode === 'setlist'
                ? 'border-[#B58900] text-[#B58900] hover:bg-[#B58900]/10'
                : 'border-[#1A4A55] text-[#EEE8D5] hover:border-[#2AA198] hover:text-[#2AA198]'
            }`}
            title={
              queueMode === 'setlist'
                ? 'Active Setlist (Open Drawer)'
                : 'Songbook Library (Open Drawer)'
            }
          >
            <ListMusic
              className={`w-4 h-4 ${queueMode === 'setlist' ? 'text-[#B58900]' : 'text-[#2AA198]'}`}
            />
            <span className="hidden lg:inline">{queueMode === 'setlist' ? 'Setlist' : 'Library'}</span>
            {queueMode === 'setlist' && activeSetlistSongsCount !== undefined ? (
              <span className="text-[10px] font-mono px-1 py-0.5 rounded bg-[#B58900]/25 text-[#B58900] font-bold">
                {activeSetlistSongIndex !== undefined ? activeSetlistSongIndex + 1 : 1}/
                {activeSetlistSongsCount}
              </span>
            ) : songsCount !== undefined ? (
              <span className="text-[10px] font-mono px-1 py-0.5 rounded bg-[#2AA198]/20 text-[#2AA198] font-bold">
                {activeSongIndex !== undefined ? activeSongIndex + 1 : 1}/{songsCount}
              </span>
            ) : null}
          </button>
        )}

        {/* View Mode Toggle: Desktop Editor vs Stage View */}
        <div className="hidden xl:flex items-center bg-[#002B36] p-0.5 rounded-xl border border-[#1A4A55] text-xs font-semibold">
          <button
            type="button"
            onClick={() => onViewChange('editor')}
            className={`flex items-center gap-1 px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
              activeView === 'editor'
                ? 'bg-[#2AA198] text-[#002B36] font-extrabold shadow-sm'
                : 'text-[#93A1A1] hover:text-[#FDF6E3]'
            }`}
          >
            <FileEdit className="w-3.5 h-3.5" />
            <span>Editor</span>
          </button>
          <button
            type="button"
            onClick={() => onViewChange('stage')}
            className={`flex items-center gap-1 px-2.5 py-1 rounded-lg transition-all cursor-pointer ${
              activeView === 'stage'
                ? 'bg-[#B58900] text-[#002B36] font-extrabold shadow-sm'
                : 'text-[#93A1A1] hover:text-[#FDF6E3]'
            }`}
          >
            <Eye className="w-3.5 h-3.5" />
            <span>Stage</span>
          </button>
        </div>
      </div>

      {/* =================================================================== */}
      {/* 2. CENTER: Main Search Bar with Website URL Source Icon Button      */}
      {/* =================================================================== */}
      <div className="flex-1 max-w-xl mx-2 flex items-center gap-1.5 sm:gap-2">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-[#93A1A1] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchQueryChange(e.target.value)}
            placeholder="Search songs & chords online..."
            className="w-full bg-[#002B36] border border-[#1A4A55] focus:border-[#2AA198] rounded-xl pl-9 pr-8 py-2 text-xs sm:text-sm text-[#FDF6E3] placeholder-[#93A1A1]/70 focus:outline-none transition-colors"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => onSearchQueryChange('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[#93A1A1] hover:text-[#FDF6E3] cursor-pointer"
              title="Clear search"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Dedicated Website URL Source Icon Button (1:1 Android HomeScreen.kt) */}
        <button
          type="button"
          onClick={onOpenWebsiteUrlSource}
          className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-[#002B36] border border-[#1A4A55] hover:border-[#2AA198] text-[#2AA198] hover:bg-[#073642] flex items-center justify-center transition-all cursor-pointer shrink-0 shadow-sm"
          title="Website URL Source - Browse song repositories"
        >
          <Globe className="w-4 h-4 sm:w-5 sm:h-5" />
        </button>
      </div>

      {/* =================================================================== */}
      {/* 3. RIGHT: Stage Tools, Palette/Theme, & 3-Dot Overflow Menu         */}
      {/* =================================================================== */}
      <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
        {/* Stage Tools (Metronome / Tuner / Band Sync) */}
        <button
          type="button"
          onClick={onOpenStageTools}
          className="p-2 sm:p-2.5 rounded-full bg-[#002B36] border border-[#1A4A55] text-[#2AA198] hover:border-[#2AA198] hover:bg-[#073642] transition-colors cursor-pointer"
          title="Stage Tools (Metronome / Tuner / Band Sync)"
        >
          <SlidersHorizontal className="w-4 h-4" />
        </button>

        {/* Theme Palette Switcher */}
        <button
          type="button"
          onClick={onToggleTheme}
          className="p-2 sm:p-2.5 rounded-full bg-[#002B36] border border-[#1A4A55] text-[#B58900] hover:border-[#B58900] hover:bg-[#073642] transition-colors cursor-pointer"
          title="Toggle Theme Palette"
        >
          <Palette className="w-4 h-4" />
        </button>

        {/* 3-Dot Overflow Menu (Strictly 4 consolidated actions from v1.0.42) */}
        <div className="relative" ref={overflowMenuRef}>
          <button
            type="button"
            onClick={() => setShowOverflowMenu(!showOverflowMenu)}
            className="p-2 sm:p-2.5 rounded-full bg-[#002B36] border border-[#1A4A55] text-[#EEE8D5] hover:text-[#2AA198] hover:border-[#2AA198] transition-colors cursor-pointer"
            title="Options & Settings"
          >
            <MoreVertical className="w-4 h-4" />
          </button>

          {showOverflowMenu && (
            <div className="absolute right-0 mt-2 w-56 rounded-2xl border border-[#1A4A55] bg-[#073642] shadow-2xl py-1.5 z-50 animate-scale-in">
              {/* 1. Stage Settings */}
              <button
                type="button"
                onClick={() => {
                  setShowOverflowMenu(false)
                  onOpenStageSettings()
                }}
                className="w-full text-left px-4 py-2.5 text-xs text-[#FDF6E3] hover:bg-[#002B36] hover:text-[#2AA198] transition-colors flex items-center gap-3 cursor-pointer"
              >
                <Settings className="w-4 h-4 text-[#2AA198]" />
                <span className="font-semibold">Stage Settings</span>
              </button>

              <div className="h-[1px] bg-[#1A4A55]/60 my-1" />

              {/* 2. Import... */}
              <button
                type="button"
                onClick={() => {
                  setShowOverflowMenu(false)
                  onOpenImportModal()
                }}
                className="w-full text-left px-4 py-2.5 text-xs text-[#FDF6E3] hover:bg-[#002B36] hover:text-[#2AA198] transition-colors flex items-center gap-3 cursor-pointer"
              >
                <FolderOpen className="w-4 h-4 text-[#2AA198]" />
                <span className="font-semibold">Import...</span>
              </button>

              <div className="h-[1px] bg-[#1A4A55]/60 my-1" />

              {/* 3. Backup & Restore... */}
              <button
                type="button"
                onClick={() => {
                  setShowOverflowMenu(false)
                  onOpenBackupRestoreModal()
                }}
                className="w-full text-left px-4 py-2.5 text-xs text-[#FDF6E3] hover:bg-[#002B36] hover:text-[#B58900] transition-colors flex items-center gap-3 cursor-pointer"
              >
                <CloudUpload className="w-4 h-4 text-[#B58900]" />
                <span className="font-semibold">Backup & Restore...</span>
              </button>

              <div className="h-[1px] bg-[#1A4A55]/60 my-1" />

              {/* 4. Check for Updates */}
              <button
                type="button"
                onClick={() => {
                  setShowOverflowMenu(false)
                  onCheckForUpdates()
                }}
                className="w-full text-left px-4 py-2.5 text-xs text-[#FDF6E3] hover:bg-[#002B36] hover:text-[#2AA198] transition-colors flex items-center gap-3 cursor-pointer"
              >
                <RefreshCw className="w-4 h-4 text-[#2AA198]" />
                <span className="font-semibold">Check for Updates</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
