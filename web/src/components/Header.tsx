import React, { useState, useRef, useEffect, useMemo } from 'react'
import {
  Search,
  X,
  Globe,
  Palette,
  MoreVertical,
  Settings,
  FolderOpen,
  CloudUpload,
  RefreshCw,
  Eye,
  FileEdit,
  ListMusic,
  ChevronDown,
  Music,
  Check,
  Layers,
  Radio,
  Share2,
  Plus,
  Loader2,
  Trash2,
} from 'lucide-react'
import type { ActiveSongState, WebSetlist } from '../types/gtar'
import {
  searchOnlineChords,
  fetchOnlineChordSheet,
  type OnlineChordResult,
  type FetchedChordSheet,
} from '../utils/onlineSearch'
import { ChordPreviewModal } from './ChordPreviewModal'

interface HeaderProps {
  activeView: 'songbook' | 'editor' | 'stage' | 'trash'
  onViewChange: (view: 'songbook' | 'editor' | 'stage' | 'trash') => void
  song: ActiveSongState
  allSongs?: ActiveSongState[]
  songsCount?: number
  deletedSongsCount?: number
  activeSongIndex?: number
  queueMode?: 'library' | 'setlist'
  activeSetlistSongsCount?: number
  activeSetlistSongIndex?: number
  searchQuery: string
  onSearchQueryChange: (query: string) => void
  onSelectSearchSong?: (songIndex: number) => void
  onSearchWebExternal?: (query: string) => void
  onNavigateHome?: () => void
  onOpenWebsiteUrlSource: () => void
  onOpenStageTools: () => void
  onToggleTheme: () => void
  onOpenStageSettings: () => void
  onOpenImportModal: () => void
  onOpenBackupRestoreModal: () => void
  onCheckForUpdates?: () => void
  isCheckingUpdates?: boolean
  onOpenSetlistDrawer?: () => void
  setlists?: WebSetlist[]
  activeSetlistId?: string | number | null
  activeSetlistName?: string
  activeSetlistSongs?: Array<{ title: string; artist?: string; key?: string }>
  onSelectSetlistSong?: (setlistId: string | number, songIdx: number) => void
  onSelectSetlist?: (setlistId: string | number) => void
  onPushSetlistToBandSync?: (setlistId?: string | number) => void
  onShareSetlist?: (setlist: WebSetlist) => void
  onDirectImportOnlineSong?: (sheet: FetchedChordSheet, openStage?: boolean) => void
}

export const Header: React.FC<HeaderProps> = ({
  activeView,
  onViewChange,
  allSongs = [],
  songsCount,
  deletedSongsCount = 0,
  activeSongIndex: _activeSongIndex,
  queueMode = 'library',
  activeSetlistSongsCount: _activeSetlistSongsCount,
  activeSetlistSongIndex,
  searchQuery,
  onSearchQueryChange,
  onSelectSearchSong,
  onSearchWebExternal,
  onNavigateHome,
  onOpenWebsiteUrlSource,
  onOpenStageTools,
  onToggleTheme,
  onOpenStageSettings,
  onOpenImportModal,
  onOpenBackupRestoreModal,
  onCheckForUpdates,
  isCheckingUpdates = false,
  onOpenSetlistDrawer,
  setlists = [],
  activeSetlistId,
  activeSetlistName,
  activeSetlistSongs = [],
  onSelectSetlistSong,
  onSelectSetlist,
  onPushSetlistToBandSync,
  onShareSetlist,
  onDirectImportOnlineSong,
}) => {
  const [showOverflowMenu, setShowOverflowMenu] = useState(false)
  const [isSetlistDropdownOpen, setIsSetlistDropdownOpen] = useState(false)
  const [isSearchFocused, setIsSearchFocused] = useState(false)
  const [onlineResults, setOnlineResults] = useState<OnlineChordResult[]>([])
  const [isSearchingOnline, setIsSearchingOnline] = useState(false)
  const [previewResult, setPreviewResult] = useState<OnlineChordResult | null>(null)
  const [importingId, setImportingId] = useState<string | number | null>(null)

  const overflowMenuRef = useRef<HTMLDivElement>(null)
  const setlistDropdownRef = useRef<HTMLDivElement>(null)
  const searchContainerRef = useRef<HTMLDivElement>(null)
  const hoverTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const handleMouseEnterSetlists = () => {
    if (hoverTimeoutRef.current) {
      clearTimeout(hoverTimeoutRef.current)
      hoverTimeoutRef.current = null
    }
    setIsSetlistDropdownOpen(true)
  }

  const handleMouseLeaveSetlists = () => {
    hoverTimeoutRef.current = setTimeout(() => {
      setIsSetlistDropdownOpen(false)
    }, 200)
  }

  // Active Setlist context with fallback to first setlist
  const currentActiveSetlist = useMemo(() => {
    if (!setlists.length) return null
    return (
      setlists.find((s) => String(s.id) === String(activeSetlistId)) ||
      setlists[0] ||
      null
    )
  }, [setlists, activeSetlistId])

  // Resolve active setlist songs reliably so it NEVER says "No songs in active setlist" when the setlist has songs
  const displaySetlistSongs = useMemo(() => {
    if (activeSetlistSongs && activeSetlistSongs.length > 0) return activeSetlistSongs
    if (currentActiveSetlist && currentActiveSetlist.songs && currentActiveSetlist.songs.length > 0) {
      return currentActiveSetlist.songs.map((ref) => {
        const match = allSongs.find(
          (s) => s.title.trim().toLowerCase() === ref.title.trim().toLowerCase()
        )
        return {
          title: ref.title,
          artist: ref.artist || match?.artist || '',
          key: match?.key || (ref as any).key || '',
        }
      })
    }
    return []
  }, [activeSetlistSongs, currentActiveSetlist, allSongs])

  // Filter matching songs for real-time search dropdown overlay
  const matchingSearchSongs = useMemo(() => {
    const q = searchQuery.trim().toLowerCase()
    if (!q) return []
    return allSongs
      .map((s, originalIdx) => ({ ...s, originalIdx }))
      .filter(
        (s) =>
          s.title.toLowerCase().includes(q) ||
          (s.artist && s.artist.toLowerCase().includes(q))
      )
  }, [allSongs, searchQuery])

  // Real Online Chord Search (debounced 350ms)
  useEffect(() => {
    const trimmed = searchQuery.trim()
    if (!trimmed || trimmed.length < 2) {
      setOnlineResults([])
      setIsSearchingOnline(false)
      return
    }

    setIsSearchingOnline(true)
    const timeout = setTimeout(() => {
      searchOnlineChords(trimmed)
        .then((res) => {
          setOnlineResults(res)
          setIsSearchingOnline(false)
        })
        .catch(() => {
          setIsSearchingOnline(false)
        })
    }, 350)

    return () => clearTimeout(timeout)
  }, [searchQuery])

  // Direct 1-Click Import from Online Results
  const handleDirectImportOnline = async (
    e: React.MouseEvent,
    item: OnlineChordResult
  ) => {
    e.stopPropagation()
    setImportingId(item.id)
    try {
      const sheet = await fetchOnlineChordSheet(item)
      if (onDirectImportOnlineSong) {
        onDirectImportOnlineSong(sheet, false)
      }
    } catch (err) {
      console.error('Failed to import online chord sheet:', err)
    } finally {
      setTimeout(() => setImportingId(null), 1200)
    }
  }

  // Close dropdowns when clicking outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (overflowMenuRef.current && !overflowMenuRef.current.contains(e.target as Node)) {
        setShowOverflowMenu(false)
      }
      if (setlistDropdownRef.current && !setlistDropdownRef.current.contains(e.target as Node)) {
        setIsSetlistDropdownOpen(false)
      }
      if (searchContainerRef.current && !searchContainerRef.current.contains(e.target as Node)) {
        setIsSearchFocused(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      if (hoverTimeoutRef.current) {
        clearTimeout(hoverTimeoutRef.current)
      }
    }
  }, [])

  return (
    <>
      <header className="h-16 border-b border-[#1A4A55] bg-[#073642] px-3 sm:px-5 flex items-center justify-between gap-2 sm:gap-4 select-none z-30 sticky top-0 shadow-md">
        {/* =================================================================== */}
        {/* 1. LEFT: App Branding, Badges & Unified Navigation Tabs             */}
        {/* =================================================================== */}
        <div className="flex items-center gap-2 sm:gap-3 shrink-0">
          {/* Clickable Brand Logo + Title: Takes user directly back to Songbook Library */}
          <div
            onClick={onNavigateHome}
            className="flex items-center gap-2 cursor-pointer group select-none transition-transform active:scale-95"
            title="Return to Songbook Library Home"
          >
            <div className="w-9 h-9 rounded-xl bg-[#002B36] border border-[#2AA198]/40 group-hover:border-[#2AA198] flex items-center justify-center text-[#2AA198] group-hover:text-[#35B8AD] shadow-inner transition-colors">
              <svg
                className="w-5 h-5 fill-current"
                viewBox="0 0 24 24"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path d="M12 2C7.5 2 3.5 5.5 3.5 11c0 4.5 4.5 9.5 8.5 11 4-1.5 8.5-6.5 8.5-11 0-5.5-4-9-8.5-9zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6zm0 13.5c-2.8-1.5-6-5.4-6-8.5 0-3.9 2.7-6.5 6-6.5s6 2.6 6 6.5c0 3.1-3.2 7-6 8.5z" />
              </svg>
            </div>
            <div className="flex flex-col">
              <div className="flex items-center gap-1.5 leading-none">
                <span className="font-black text-base text-[#FDF6E3] group-hover:text-[#2AA198] tracking-wide transition-colors">
                  GTAR
                </span>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation()
                    onCheckForUpdates?.()
                  }}
                  title="Click to check for updates (v1.0.47)"
                  className="text-[10px] font-mono font-bold uppercase bg-[#002B36] text-[#2AA198] px-1.5 py-0.5 rounded border border-[#1A4A55] hover:border-[#2AA198] transition-colors cursor-pointer flex items-center gap-1"
                >
                  {isCheckingUpdates && (
                    <RefreshCw className="w-2.5 h-2.5 animate-spin text-[#B58900]" />
                  )}
                  <span>v1.0.47</span>
                </button>
              </div>
              <span className="hidden md:inline text-[10px] text-[#93A1A1] group-hover:text-[#EEE8D5] mt-0.5 font-medium leading-none transition-colors">
                Guitar Tool App Republic
              </span>
            </div>
          </div>

          {/* Unified Navigation Tabs: Library | Setlists ▼ | Editor | Stage */}
          <div className="flex items-center bg-[#002B36] p-0.5 rounded-xl border border-[#1A4A55] text-xs font-semibold ml-1">
            {/* 1. Library / Songbook Tab Button */}
            <button
              type="button"
              onClick={() => onViewChange('songbook')}
              className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg transition-all cursor-pointer ${
                activeView === 'songbook'
                  ? 'bg-[#2AA198] text-[#002B36] font-extrabold shadow-sm'
                  : 'text-[#93A1A1] hover:text-[#FDF6E3] hover:bg-[#073642]'
              }`}
              title="Open Songbook Library Grid"
            >
              <Music className="w-3.5 h-3.5" />
              <span className="hidden md:inline">Library</span>
              {songsCount !== undefined && (
                <span
                  className={`text-[10px] font-mono px-1 py-0.2 rounded font-bold ${
                    activeView === 'songbook'
                      ? 'bg-[#002B36]/30 text-[#002B36]'
                      : 'bg-[#2AA198]/20 text-[#2AA198]'
                  }`}
                >
                  {songsCount}
                </span>
              )}
            </button>

            {/* 2. Setlists Dedicated Cascading Dropdown Tab */}
            <div
              ref={setlistDropdownRef}
              className="relative"
              onMouseEnter={handleMouseEnterSetlists}
              onMouseLeave={handleMouseLeaveSetlists}
            >
              <button
                type="button"
                onClick={() => setIsSetlistDropdownOpen((prev) => !prev)}
                className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg transition-all cursor-pointer ${
                  queueMode === 'setlist' || isSetlistDropdownOpen
                    ? 'bg-[#B58900]/25 text-[#B58900] font-bold'
                    : 'text-[#93A1A1] hover:text-[#FDF6E3] hover:bg-[#073642]'
                }`}
                title="Setlist Navigation & 1-Click Song Queue"
              >
                <ListMusic className="w-3.5 h-3.5" />
                <span className="hidden md:inline">Setlists</span>
                {displaySetlistSongs.length > 0 && (
                  <span className="text-[10px] font-mono px-1 py-0.2 rounded bg-[#B58900]/25 text-[#B58900] font-bold">
                    {activeSetlistSongIndex !== undefined ? activeSetlistSongIndex + 1 : 1}/
                    {displaySetlistSongs.length}
                  </span>
                )}
                <ChevronDown
                  className={`w-3 h-3 transition-transform duration-200 ${
                    isSetlistDropdownOpen ? 'rotate-180 text-[#B58900]' : 'text-[#93A1A1]'
                  }`}
                />
              </button>

              {/* Cascading Dropdown Menu */}
              {isSetlistDropdownOpen && (
                <div className="absolute left-0 top-full mt-2 w-80 rounded-2xl border border-[#1A4A55] bg-[#073642] shadow-2xl p-2.5 z-50 animate-scale-in text-xs select-none">
                  {/* Active Setlist Header & Quick Actions */}
                  <div className="px-2 py-1.5 border-b border-[#1A4A55]/60 mb-1 flex items-center justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <span className="text-[10px] font-mono text-[#93A1A1] uppercase tracking-wider block">
                        Active Setlist
                      </span>
                      <span className="font-extrabold text-[#FDF6E3] text-xs truncate block">
                        {activeSetlistName || currentActiveSetlist?.name || 'Active Setlist'}
                      </span>
                    </div>

                    <div className="flex items-center gap-1 shrink-0">
                      {/* Push to BandSync Button */}
                      {onPushSetlistToBandSync && currentActiveSetlist && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation()
                            onPushSetlistToBandSync(currentActiveSetlist.id)
                          }}
                          className="px-2 py-0.5 rounded-lg bg-[#2AA198]/20 hover:bg-[#2AA198] text-[#2AA198] hover:text-[#002B36] font-bold text-[10px] flex items-center gap-1 border border-[#2AA198]/40 transition-colors cursor-pointer"
                          title="Broadcast this setlist directly to connected band members via BandSync"
                        >
                          <Radio className="w-3 h-3" />
                          <span>Sync Band</span>
                        </button>
                      )}

                      {/* Share / Export Setlist */}
                      {onShareSetlist && currentActiveSetlist && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation()
                            onShareSetlist(currentActiveSetlist)
                          }}
                          className="px-2 py-0.5 rounded-lg bg-[#002B36] hover:bg-[#1A4A55] text-[#EEE8D5] hover:text-[#B58900] font-bold text-[10px] flex items-center gap-1 border border-[#1A4A55] transition-colors cursor-pointer"
                          title="Export or copy setlist JSON"
                        >
                          <Share2 className="w-3 h-3" />
                          <span>Share</span>
                        </button>
                      )}

                      <span className="text-[10px] font-mono font-bold text-[#B58900] bg-[#B58900]/15 px-1.5 py-0.5 rounded border border-[#B58900]/30 shrink-0">
                        {displaySetlistSongs.length} SONGS
                      </span>
                    </div>
                  </div>

                  {/* Song List with Direct 1-Click Selection */}
                  <div className="max-h-60 overflow-y-auto py-1 space-y-0.5 px-1">
                    {displaySetlistSongs && displaySetlistSongs.length > 0 ? (
                      displaySetlistSongs.map((s, idx) => {
                        const isCurrent =
                          queueMode === 'setlist' && activeSetlistSongIndex === idx
                        return (
                          <button
                            key={`${s.title}-${idx}`}
                            type="button"
                            onClick={() => {
                              if (onSelectSetlistSong && currentActiveSetlist) {
                                onSelectSetlistSong(currentActiveSetlist.id, idx)
                              }
                              onViewChange('stage')
                              setIsSetlistDropdownOpen(false)
                            }}
                            className={`w-full text-left px-2.5 py-1.5 rounded-xl transition-all flex items-center justify-between gap-2 group cursor-pointer ${
                              isCurrent
                                ? 'bg-[#B58900]/20 text-[#FDF6E3] border border-[#B58900]/40'
                                : 'hover:bg-[#002B36] text-[#EEE8D5]'
                            }`}
                          >
                            <div className="flex items-center gap-2 min-w-0">
                              <span
                                className={`w-5 h-5 rounded-lg flex items-center justify-center font-mono text-[10px] font-bold shrink-0 ${
                                  isCurrent
                                    ? 'bg-[#B58900] text-[#002B36]'
                                    : 'bg-[#002B36] text-[#93A1A1] group-hover:text-[#2AA198]'
                                }`}
                              >
                                {idx + 1}
                              </span>
                              <div className="truncate">
                                <p
                                  className={`text-xs font-bold truncate leading-tight ${
                                    isCurrent
                                      ? 'text-[#B58900]'
                                      : 'text-[#FDF6E3] group-hover:text-[#2AA198]'
                                  }`}
                                >
                                  {s.title}
                                </p>
                                {s.artist && (
                                  <p className="text-[10px] text-[#93A1A1] truncate leading-tight">
                                    {s.artist}
                                  </p>
                                )}
                              </div>
                            </div>

                            <div className="flex items-center gap-1 shrink-0">
                              {s.key && (
                                <span className="text-[10px] font-mono px-1 py-0.5 rounded bg-[#002B36] text-[#2AA198] font-bold">
                                  {s.key}
                                </span>
                              )}
                              {isCurrent && (
                                <Check className="w-3.5 h-3.5 text-[#B58900] shrink-0" />
                              )}
                            </div>
                          </button>
                        )
                      })
                    ) : (
                      <div className="px-3 py-4 text-center text-xs text-[#93A1A1]">
                        No songs in active setlist
                      </div>
                    )}
                  </div>

                  {/* Footer Switcher / Drawer Trigger */}
                  <div className="border-t border-[#1A4A55]/60 pt-1.5 mt-1 px-1.5 flex items-center justify-between gap-1">
                    {setlists.length > 1 && (
                      <div className="flex items-center gap-1 overflow-x-auto max-w-[180px] py-0.5">
                        {setlists.map((sl) => (
                          <button
                            key={sl.id}
                            type="button"
                            onClick={() => {
                              if (onSelectSetlist) {
                                onSelectSetlist(sl.id)
                              }
                            }}
                            className={`text-[10px] px-2 py-0.5 rounded-lg whitespace-nowrap transition-colors cursor-pointer ${
                              String(sl.id) === String(activeSetlistId)
                                ? 'bg-[#B58900] text-[#002B36] font-bold'
                                : 'bg-[#002B36] text-[#93A1A1] hover:text-[#FDF6E3]'
                            }`}
                            title={`Switch to setlist: ${sl.name}`}
                          >
                            {sl.name}
                          </button>
                        ))}
                      </div>
                    )}
                    {onOpenSetlistDrawer && (
                      <button
                        type="button"
                        onClick={() => {
                          setIsSetlistDropdownOpen(false)
                          onOpenSetlistDrawer()
                        }}
                        className="ml-auto text-[10px] font-bold text-[#2AA198] hover:underline px-2 py-1 flex items-center gap-1 cursor-pointer"
                      >
                        <Layers className="w-3 h-3" />
                        <span>Manage All...</span>
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>

            <div className="w-[1px] h-4 bg-[#1A4A55] mx-1" />

            {/* 3. Editor View Button */}
            <button
              type="button"
              onClick={() => onViewChange('editor')}
              className={`flex items-center gap-1 px-2.5 py-1.5 rounded-lg transition-all cursor-pointer ${
                activeView === 'editor'
                  ? 'bg-[#2AA198] text-[#002B36] font-extrabold shadow-sm'
                  : 'text-[#93A1A1] hover:text-[#FDF6E3] hover:bg-[#073642]'
              }`}
            >
              <FileEdit className="w-3.5 h-3.5" />
              <span className="hidden md:inline">Editor</span>
            </button>

            {/* 4. Stage View Button */}
            <button
              type="button"
              onClick={() => onViewChange('stage')}
              className={`flex items-center gap-1 px-2.5 py-1.5 rounded-lg transition-all cursor-pointer ${
                activeView === 'stage'
                  ? 'bg-[#B58900] text-[#002B36] font-extrabold shadow-sm'
                  : 'text-[#93A1A1] hover:text-[#FDF6E3] hover:bg-[#073642]'
              }`}
            >
              <Eye className="w-3.5 h-3.5" />
              <span className="hidden md:inline">Stage</span>
            </button>

            {/* 5. Trash Bin View Button */}
            <button
              type="button"
              onClick={() => onViewChange('trash')}
              className={`flex items-center gap-1 px-2.5 py-1.5 rounded-lg transition-all cursor-pointer ${
                activeView === 'trash'
                  ? 'bg-[#DC6E67] text-[#002B36] font-extrabold shadow-sm'
                  : 'text-[#93A1A1] hover:text-[#DC6E67] hover:bg-[#073642]'
              }`}
              title="Trash Bin / Basurahan"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span className="hidden md:inline">Trash</span>
              {deletedSongsCount > 0 && (
                <span
                  className={`text-[10px] font-mono px-1.5 py-0.2 rounded font-bold ${
                    activeView === 'trash'
                      ? 'bg-[#002B36]/30 text-[#002B36]'
                      : 'bg-[#DC6E67]/20 text-[#DC6E67]'
                  }`}
                >
                  {deletedSongsCount}
                </span>
              )}
            </button>
          </div>
        </div>

        {/* =================================================================== */}
        {/* 2. CENTER: Main Search Bar with Real-Time Local & Online Results   */}
        {/* =================================================================== */}
        <div
          ref={searchContainerRef}
          className="flex-1 max-w-xl mx-2 flex items-center gap-1.5 sm:gap-2 relative"
        >
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-[#93A1A1] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              id="search-input"
              type="text"
              value={searchQuery}
              onFocus={() => setIsSearchFocused(true)}
              onChange={(e) => {
                onSearchQueryChange(e.target.value)
                setIsSearchFocused(true)
              }}
              placeholder="Search local songbook & online chords..."
              className="w-full bg-[#002B36] border border-[#1A4A55] focus:border-[#2AA198] rounded-xl pl-9 pr-8 py-2 text-xs sm:text-sm text-[#FDF6E3] placeholder-[#93A1A1]/70 focus:outline-none transition-colors"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => {
                  onSearchQueryChange('')
                  setIsSearchFocused(false)
                }}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[#93A1A1] hover:text-[#FDF6E3] cursor-pointer"
                title="Clear search"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}

            {/* Real-time search results dropdown overlay: Local + Online Results */}
            {isSearchFocused && searchQuery.trim().length > 0 && (
              <div className="absolute left-0 right-0 top-full mt-2 rounded-2xl border border-[#1A4A55] bg-[#073642] shadow-2xl py-2 z-50 animate-scale-in max-h-96 overflow-y-auto">
                {/* ----------------------------------------------------------- */}
                {/* A. LOCAL SONGBOOK SECTION                                   */}
                {/* ----------------------------------------------------------- */}
                <div className="px-3 py-1 text-[10px] font-mono font-bold text-[#93A1A1] uppercase tracking-wider flex items-center justify-between border-b border-[#1A4A55]/60 mb-1">
                  <span>Local Songbook ({matchingSearchSongs.length})</span>
                  <span className="text-[#2AA198]">Click to View on Stage</span>
                </div>

                {matchingSearchSongs.length > 0 ? (
                  matchingSearchSongs.map((song) => (
                    <button
                      key={`local-${song.title}-${song.originalIdx}`}
                      type="button"
                      onClick={() => {
                        if (onSelectSearchSong) {
                          onSelectSearchSong(song.originalIdx)
                        }
                        onViewChange('stage')
                        setIsSearchFocused(false)
                      }}
                      className="w-full text-left px-3 py-2 rounded-xl transition-all flex items-center justify-between gap-3 group cursor-pointer hover:bg-[#002B36] text-[#EEE8D5]"
                    >
                      <div className="min-w-0 flex items-center gap-2.5">
                        <div className="w-6 h-6 rounded-lg bg-[#002B36] text-[#2AA198] flex items-center justify-center text-xs font-mono font-bold group-hover:bg-[#2AA198] group-hover:text-[#002B36] transition-colors shrink-0">
                          {song.originalIdx + 1}
                        </div>
                        <div className="truncate">
                          <div className="text-xs font-bold text-[#FDF6E3] group-hover:text-[#2AA198] transition-colors truncate">
                            {song.title}
                          </div>
                          {song.artist && (
                            <div className="text-[10px] text-[#93A1A1] truncate">
                              {song.artist}
                            </div>
                          )}
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 shrink-0 font-mono text-[10px]">
                        {song.key && (
                          <span className="px-1.5 py-0.5 rounded bg-[#002B36] text-[#B58900] font-bold">
                            {song.key}
                          </span>
                        )}
                        {song.bpm && (
                          <span className="px-1.5 py-0.5 rounded bg-[#002B36] text-[#93A1A1]">
                            {song.bpm} BPM
                          </span>
                        )}
                      </div>
                    </button>
                  ))
                ) : (
                  <div className="px-3 py-2 text-center text-xs text-[#93A1A1]">
                    No local songs matching &quot;{searchQuery}&quot;
                  </div>
                )}

                {/* ----------------------------------------------------------- */}
                {/* B. ONLINE RESULTS SECTION (Parity with Android)             */}
                {/* ----------------------------------------------------------- */}
                <div className="px-3 py-1.5 text-[10px] font-mono font-bold text-[#B58900] uppercase tracking-wider flex items-center justify-between border-t border-b border-[#1A4A55]/60 mt-2 mb-1 bg-[#002B36]/60">
                  <div className="flex items-center gap-1.5">
                    <Globe className="w-3.5 h-3.5 text-[#2AA198]" />
                    <span>Online Results ({onlineResults.length})</span>
                  </div>
                  {isSearchingOnline && (
                    <div className="flex items-center gap-1 text-[#2AA198]">
                      <Loader2 className="w-3 h-3 animate-spin" />
                      <span className="text-[9px]">Searching...</span>
                    </div>
                  )}
                </div>

                {onlineResults.length > 0 ? (
                  onlineResults.map((onlineItem) => {
                    const isImporting = importingId === onlineItem.id
                    return (
                      <div
                        key={`online-${onlineItem.id}`}
                        className="px-3 py-2 rounded-xl transition-all flex items-center justify-between gap-2.5 hover:bg-[#002B36] text-[#EEE8D5] group"
                      >
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-1.5">
                            <span className="text-xs font-bold text-[#FDF6E3] group-hover:text-[#2AA198] truncate">
                              {onlineItem.songName}
                            </span>
                            <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-[#B58900]/20 text-[#B58900] font-bold border border-[#B58900]/30 shrink-0">
                              {onlineItem.type} v{onlineItem.version}
                            </span>
                          </div>

                          <div className="flex items-center gap-2 text-[10px] text-[#93A1A1] mt-0.5 font-medium">
                            <span className="truncate">{onlineItem.artistName}</span>
                            <span>•</span>
                            <span className="text-[#B58900] font-bold">
                              ★ {onlineItem.rating.toFixed(1)}
                            </span>
                            <span>({onlineItem.votes.toLocaleString()} votes)</span>
                            {onlineItem.tonality && (
                              <span className="px-1 py-0.2 rounded bg-[#002B36] text-[#2AA198] font-mono font-bold">
                                Key: {onlineItem.tonality}
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Inline Action Controls: [Preview] and [+ Import] */}
                        <div className="flex items-center gap-1.5 shrink-0">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation()
                              setIsSearchFocused(false)
                              setPreviewResult(onlineItem)
                            }}
                            className="px-2.5 py-1 rounded-lg bg-[#073642] hover:bg-[#1A4A55] text-[#EEE8D5] text-[11px] font-bold flex items-center gap-1 border border-[#1A4A55] transition-colors cursor-pointer"
                            title="Preview chord sheet"
                          >
                            <Eye className="w-3 h-3 text-[#2AA198]" />
                            <span>Preview</span>
                          </button>

                          <button
                            type="button"
                            disabled={isImporting}
                            onClick={(e) => handleDirectImportOnline(e, onlineItem)}
                            className="px-2.5 py-1 rounded-lg bg-[#2AA198] hover:bg-[#35B8AD] text-[#002B36] text-[11px] font-bold flex items-center gap-1 transition-all cursor-pointer shadow-sm active:scale-95 disabled:opacity-50"
                            title="Fetch and import chord sheet directly to library"
                          >
                            {isImporting ? (
                              <Loader2 className="w-3 h-3 animate-spin" />
                            ) : (
                              <Plus className="w-3 h-3 stroke-[3]" />
                            )}
                            <span>{isImporting ? 'Importing...' : 'Import'}</span>
                          </button>
                        </div>
                      </div>
                    )
                  })
                ) : !isSearchingOnline ? (
                  <div className="p-3 text-center space-y-2">
                    <p className="text-xs text-[#93A1A1]">
                      Search online sources for &quot;{searchQuery}&quot;
                    </p>
                    <button
                      type="button"
                      onClick={() => {
                        setIsSearchFocused(false)
                        if (onSearchWebExternal) {
                          onSearchWebExternal(searchQuery)
                        } else {
                          onOpenWebsiteUrlSource()
                        }
                      }}
                      className="w-full py-1.5 px-3 rounded-xl bg-[#2AA198]/20 hover:bg-[#2AA198] text-[#2AA198] hover:text-[#002B36] font-bold text-xs flex items-center justify-center gap-2 border border-[#2AA198]/40 transition-all cursor-pointer shadow-sm"
                    >
                      <Globe className="w-3.5 h-3.5" />
                      <span>Search on Web Sources / Ultimate-Guitar</span>
                    </button>
                  </div>
                ) : null}
              </div>
            )}
          </div>

          {/* Dedicated Website URL Source Icon Button */}
          <button
            type="button"
            onClick={onOpenWebsiteUrlSource}
            title="Browse Website Sources (SongSelect, UG, Chordie, OPMTunes)"
            className="p-2 rounded-xl bg-[#002B36] border border-[#1A4A55] text-[#2AA198] hover:border-[#2AA198] hover:text-[#35B8AD] transition-all cursor-pointer shrink-0"
          >
            <Globe className="w-4 h-4" />
          </button>
        </div>

        {/* =================================================================== */}
        {/* 3. RIGHT: Stage Controls & Unified 3-Dots Overflow Menu             */}
        {/* =================================================================== */}
        <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
          {/* Stage Tools / Band Sync Button */}
          <button
            type="button"
            onClick={onOpenStageTools}
            title="Band Sync & Metronome Engine"
            className="px-2.5 py-1.5 rounded-xl bg-[#002B36] border border-[#1A4A55] hover:border-[#2AA198] text-[#2AA198] hover:text-[#35B8AD] text-xs font-bold transition-all cursor-pointer flex items-center gap-1.5"
          >
            <Radio className="w-3.5 h-3.5" />
            <span className="hidden lg:inline">Band Sync</span>
          </button>

          {/* Theme Palette Switcher */}
          <button
            type="button"
            onClick={onToggleTheme}
            title="Change Stage Color Theme (Amber, OLED Black, Solarized, Paper Cream)"
            className="p-2 rounded-xl bg-[#002B36] border border-[#1A4A55] hover:border-[#B58900] text-[#B58900] hover:text-[#FDF6E3] transition-all cursor-pointer"
          >
            <Palette className="w-4 h-4" />
          </button>

          {/* 3-Dots Overflow Menu (Cleaned: No Redundant Update Checker) */}
          <div className="relative" ref={overflowMenuRef}>
            <button
              type="button"
              onClick={() => setShowOverflowMenu(!showOverflowMenu)}
              title="More Options"
              className="p-2 rounded-xl bg-[#002B36] border border-[#1A4A55] hover:border-[#2AA198] text-[#EEE8D5] hover:text-[#2AA198] transition-all cursor-pointer"
            >
              <MoreVertical className="w-4 h-4" />
            </button>

            {showOverflowMenu && (
              <div className="absolute right-0 top-full mt-2 w-52 rounded-2xl border border-[#1A4A55] bg-[#073642] shadow-2xl py-2 z-50 animate-scale-in">
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

                {/* 4. Trash Bin (Basurahan) */}
                <button
                  type="button"
                  onClick={() => {
                    setShowOverflowMenu(false)
                    onViewChange('trash')
                  }}
                  className="w-full text-left px-4 py-2.5 text-xs text-[#FDF6E3] hover:bg-[#002B36] hover:text-[#DC6E67] transition-colors flex items-center justify-between gap-3 cursor-pointer"
                >
                  <div className="flex items-center gap-3">
                    <Trash2 className="w-4 h-4 text-[#DC6E67]" />
                    <span className="font-semibold">Trash Bin (Basurahan)</span>
                  </div>
                  {deletedSongsCount > 0 && (
                    <span className="text-[10px] font-mono px-1.5 py-0.5 rounded-full bg-[#DC6E67]/20 text-[#DC6E67] font-bold">
                      {deletedSongsCount}
                    </span>
                  )}
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Real Chord Preview & Direct Import Modal */}
      <ChordPreviewModal
        isOpen={Boolean(previewResult)}
        onClose={() => setPreviewResult(null)}
        result={previewResult}
        onImportSong={(sheet, openStage) => {
          if (onDirectImportOnlineSong) {
            onDirectImportOnlineSong(sheet, openStage)
          }
        }}
      />
    </>
  )
}
