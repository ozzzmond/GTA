import React from 'react'
import {
  Music,
  Plus,
  Play,
  Trash2,
  AlertTriangle,
  Sparkles,
  Layers,
  ArrowRight,
  Radio,
  Share2,
} from 'lucide-react'
import type { ActiveSongState, WebSetlist } from '../types/gtar'

interface SongbookHomeViewProps {
  songs: ActiveSongState[]
  activeSongIndex: number
  onSelectSong: (index: number) => void
  onNewSong: () => void
  onNewSetlist?: () => void
  onOpenImportModal: () => void
  onOpenWebsiteUrlSource: () => void
  onOpenSetlists: () => void
  onDeleteSong: (index: number) => void
  setlists?: WebSetlist[]
  onSelectSetlistSong?: (setlistId: string | number, songIdx: number) => void
  onPushSetlistToBandSync?: (setlistId: string | number) => void
  onShareSetlist?: (setlist: WebSetlist) => void
}

export const SongbookHomeView: React.FC<SongbookHomeViewProps> = ({
  songs,
  activeSongIndex,
  onSelectSong,
  onNewSong,
  onNewSetlist,
  onOpenImportModal: _onOpenImportModal,
  onOpenWebsiteUrlSource: _onOpenWebsiteUrlSource,
  onOpenSetlists,
  onDeleteSong,
  setlists = [],
  onSelectSetlistSong,
  onPushSetlistToBandSync,
  onShareSetlist,
}) => {
  const [confirmDeleteIdx, setConfirmDeleteIdx] = React.useState<number | null>(null)

  return (
    <div className="flex-1 overflow-y-auto bg-[#002B36] px-4 sm:px-8 py-6 max-w-7xl mx-auto w-full select-none">
      {/* 1. Hero / Welcome Banner */}
      <div className="rounded-3xl bg-gradient-to-r from-[#073642] via-[#002B36] to-[#073642] border border-[#1A4A55] p-6 sm:p-8 mb-8 shadow-2xl relative overflow-hidden">
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#2AA198]/15 border border-[#2AA198]/30 text-[#2AA198] text-xs font-mono font-bold mb-3">
            <Sparkles className="w-3.5 h-3.5" />
            <span>GTAR Live Stage Companion v1.0.47</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-black text-[#FDF6E3] tracking-tight">
            Songbook & Gig Library
          </h1>
          <p className="text-sm text-[#93A1A1] mt-2 leading-relaxed">
            Select any song below to launch into the live Stage View teleprompter, or create and manage gig setlists.
          </p>

          <div className="flex flex-wrap items-center gap-3 mt-5">
            <button
              type="button"
              onClick={onNewSetlist || onOpenSetlists}
              className="px-5 py-2.5 rounded-xl bg-amber-500 hover:bg-amber-600 text-black font-semibold text-xs sm:text-sm flex items-center gap-2 transition-all cursor-pointer shadow-lg active:scale-95"
              title="Create new empty gig setlist"
            >
              <Plus className="w-4 h-4 stroke-[3]" />
              <span>New Setlist</span>
            </button>
          </div>
        </div>

        {/* Decorative Guitar Icon background */}
        <div className="absolute right-4 -bottom-6 opacity-5 pointer-events-none hidden md:block">
          <svg className="w-64 h-64 text-[#2AA198] fill-current" viewBox="0 0 24 24">
            <path d="M12 2C7.5 2 3.5 5.5 3.5 11c0 4.5 4.5 9.5 8.5 11 4-1.5 8.5-6.5 8.5-11 0-5.5-4-9-8.5-9zm0 4a3 3 0 1 1 0 6 3 3 0 0 1 0-6zm0 13.5c-2.8-1.5-6-5.4-6-8.5 0-3.9 2.7-6.5 6-6.5s6 2.6 6 6.5c0 3.1-3.2 7-6 8.5z"/>
          </svg>
        </div>
      </div>

      {/* 2. Active Setlists Quick Row (if available) */}
      {setlists.length > 0 && (
        <div className="mb-8">
          <div className="flex items-center justify-between mb-3 px-1">
            <div className="flex items-center gap-2">
              <Layers className="w-4 h-4 text-[#B58900]" />
              <h2 className="text-sm font-bold text-[#FDF6E3] uppercase tracking-wider font-mono">
                Gig Setlists ({setlists.length})
              </h2>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onOpenSetlists}
                className="text-xs font-bold text-[#2AA198] hover:underline flex items-center gap-1 cursor-pointer"
              >
                <span>Manage Setlists</span>
                <ArrowRight className="w-3 h-3" />
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {setlists.map((sl) => (
              <div
                key={sl.id}
                onClick={() => {
                  if (onSelectSetlistSong && sl.songs.length > 0) {
                    onSelectSetlistSong(sl.id, 0)
                  } else {
                    onOpenSetlists()
                  }
                }}
                className="p-4 rounded-2xl bg-[#073642] border border-[#1A4A55] hover:border-[#B58900] transition-all cursor-pointer shadow-md group flex flex-col justify-between"
              >
                <div className="flex items-center justify-between">
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-bold text-[#FDF6E3] group-hover:text-[#B58900] truncate transition-colors">
                      {sl.name}
                    </div>
                    <div className="text-[11px] font-mono text-[#93A1A1] mt-0.5">
                      {sl.songs.length} {sl.songs.length === 1 ? 'song' : 'songs'} in queue
                    </div>
                  </div>
                  <div className="w-8 h-8 rounded-xl bg-[#002B36] text-[#B58900] flex items-center justify-center group-hover:bg-[#B58900] group-hover:text-[#002B36] transition-colors shrink-0 shadow-inner">
                    <Play className="w-3.5 h-3.5 fill-current" />
                  </div>
                </div>

                {/* Setlist Action Controls: Push to BandSync & Share/Export */}
                <div className="flex items-center justify-between gap-1.5 mt-3 pt-2.5 border-t border-[#1A4A55]/60">
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      onPushSetlistToBandSync?.(sl.id)
                    }}
                    className="px-2 py-1 rounded-lg bg-[#2AA198]/15 hover:bg-[#2AA198] text-[#2AA198] hover:text-[#002B36] text-[10px] font-bold font-mono flex items-center gap-1 transition-colors cursor-pointer border border-[#2AA198]/30"
                    title="Broadcast setlist to connected band members via BandSync"
                  >
                    <Radio className="w-3 h-3" />
                    <span>Push BandSync</span>
                  </button>

                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      onShareSetlist?.(sl)
                    }}
                    className="px-2 py-1 rounded-lg bg-[#002B36] hover:bg-[#1A4A55] text-[#93A1A1] hover:text-[#FDF6E3] text-[10px] font-bold font-mono flex items-center gap-1 transition-colors cursor-pointer border border-[#1A4A55]"
                    title="Share / Export setlist as JSON"
                  >
                    <Share2 className="w-3 h-3" />
                    <span>Share</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 3. Main Songs Grid / List */}
      <div>
        <div className="flex items-center justify-between mb-4 px-1">
          <div className="flex items-center gap-2">
            <Music className="w-4 h-4 text-[#2AA198]" />
            <h2 className="text-sm font-bold text-[#FDF6E3] uppercase tracking-wider font-mono">
              Songs Library ({songs.length})
            </h2>
          </div>
          <span className="text-xs text-[#93A1A1] font-mono">
            Click any song to launch Stage View
          </span>
        </div>

        {songs.length === 0 ? (
          <div className="p-12 text-center rounded-3xl border border-[#1A4A55] bg-[#073642]/50 text-[#93A1A1] space-y-3">
            <Music className="w-8 h-8 mx-auto text-[#2AA198]" />
            <div className="text-base font-bold text-[#FDF6E3]">Your Songbook is Empty</div>
            <p className="text-xs max-w-sm mx-auto">
              Create your first song template or import chord charts from files or online web sources.
            </p>
            <button
              type="button"
              onClick={onNewSong}
              className="mt-2 px-4 py-2 rounded-xl bg-[#2AA198] text-[#002B36] font-bold text-xs"
            >
              + Create First Song
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3.5">
            {songs.map((song, idx) => {
              const isSelected = idx === activeSongIndex
              const isDeleting = confirmDeleteIdx === idx

              return (
                <div
                  key={song.id || idx}
                  onClick={() => onSelectSong(idx)}
                  className={`relative p-4 rounded-2xl border transition-all cursor-pointer select-none group flex flex-col justify-between min-h-[110px] ${
                    isSelected
                      ? 'border-[#2AA198] bg-[#073642] ring-1 ring-[#2AA198] shadow-lg shadow-[#2AA198]/10'
                      : 'border-[#1A4A55] bg-[#073642]/70 hover:border-[#2AA198] hover:bg-[#073642]'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0 flex-1">
                      <div
                        className={`w-9 h-9 rounded-xl flex items-center justify-center font-mono text-xs font-bold shrink-0 transition-colors shadow-inner ${
                          isSelected
                            ? 'bg-[#2AA198] text-[#002B36]'
                            : 'bg-[#002B36] text-[#93A1A1] group-hover:text-[#2AA198]'
                        }`}
                      >
                        {String(idx + 1).padStart(2, '0')}
                      </div>
                      <div className="min-w-0 flex-1">
                        <h3 className="font-bold text-sm text-[#FDF6E3] group-hover:text-[#2AA198] transition-colors truncate">
                          {song.title || 'Untitled Song'}
                        </h3>
                        <p className="text-xs text-[#93A1A1] truncate mt-0.5">
                          {song.artist || 'Unknown Artist'}
                        </p>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation()
                        setConfirmDeleteIdx(idx)
                      }}
                      className="p-1.5 rounded-lg text-[#93A1A1] hover:text-[#DC6E67] hover:bg-[#DC6E67]/15 transition-colors cursor-pointer"
                      title="Delete song"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  <div className="flex items-center justify-between mt-3 pt-3 border-t border-[#1A4A55]/60 text-[10px] font-mono">
                    <div className="flex items-center gap-1.5">
                      {song.key && (
                        <span className="px-2 py-0.5 rounded-md bg-[#002B36] text-[#B58900] font-bold border border-[#1A4A55]">
                          KEY: {song.key}
                        </span>
                      )}
                      {song.capo && song.capo.toLowerCase() !== 'no capo' && (
                        <span className="px-1.5 py-0.5 rounded-md bg-[#002B36] text-[#2AA198] border border-[#1A4A55]">
                          {song.capo}
                        </span>
                      )}
                      {song.bpm && (
                        <span className="px-1.5 py-0.5 rounded-md bg-[#002B36] text-[#93A1A1] border border-[#1A4A55]">
                          {song.bpm} BPM
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-1 text-[#2AA198] font-bold opacity-0 group-hover:opacity-100 transition-opacity">
                      <span>OPEN STAGE</span>
                      <Play className="w-2.5 h-2.5 fill-current" />
                    </div>
                  </div>

                  {/* Inline Delete Confirmation Popover */}
                  {isDeleting && (
                    <div
                      onClick={(e) => e.stopPropagation()}
                      className="absolute inset-0 bg-[#073642] border border-[#DC6E67] rounded-2xl p-4 flex items-center justify-between z-20 animate-in fade-in zoom-in-95 duration-150"
                    >
                      <div className="flex items-center gap-2 text-xs text-[#DC6E67] font-semibold">
                        <AlertTriangle className="w-4 h-4 shrink-0" />
                        <span>Delete this song?</span>
                      </div>
                      <div className="flex items-center gap-2 font-mono text-xs font-bold">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation()
                            onDeleteSong(idx)
                            setConfirmDeleteIdx(null)
                          }}
                          className="px-3 py-1 rounded-lg bg-[#DC6E67] text-white hover:bg-[#DC6E67]/90 transition-colors cursor-pointer"
                        >
                          Delete
                        </button>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation()
                            setConfirmDeleteIdx(null)
                          }}
                          className="px-2.5 py-1 rounded-lg bg-[#002B36] text-[#93A1A1] hover:text-[#FDF6E3] transition-colors cursor-pointer"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
