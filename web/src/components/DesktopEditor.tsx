import React, { useRef } from 'react'
import {
  Music,
  Hash,
  Activity,
  Plus,
  Type,
  FileCode
} from 'lucide-react'
import { parseGtarSong } from '../utils/songParser'
import { SongLineRenderer } from './SongLineRenderer'
import type { ActiveSongState } from '../types/gtar'

interface DesktopEditorProps {
  song: ActiveSongState
  onUpdateSong: (updated: Partial<ActiveSongState>) => void
  transposeOffset: number
}

export const DesktopEditor: React.FC<DesktopEditorProps> = ({
  song,
  onUpdateSong,
  transposeOffset,
}) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // Parse song with native v1.0.42 parser and active transpose offset
  const parsedSong = parseGtarSong(song.rawContent, transposeOffset)

  // Quick insertion of section tags at cursor position
  const insertTextAtCursor = (insertText: string) => {
    const textarea = textareaRef.current
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const text = song.rawContent
    const before = text.substring(0, start)
    const after = text.substring(end)

    // Ensure leading and trailing newlines if inserting a block header
    const prefix = before.endsWith('\n') || before.length === 0 ? '' : '\n'
    const suffix = after.startsWith('\n') || after.length === 0 ? '\n' : '\n'
    const updated = `${before}${prefix}${insertText}${suffix}${after}`

    onUpdateSong({ rawContent: updated })

    setTimeout(() => {
      textarea.focus()
      const newCursorPos = start + prefix.length + insertText.length + suffix.length
      textarea.setSelectionRange(newCursorPos, newCursorPos)
    }, 10)
  }

  return (
    <div className="flex-1 flex flex-col h-[calc(100vh-3.5rem)] overflow-hidden bg-[#002B36]">
      {/* Top Metadata Strip */}
      <div className="border-b border-[#1A4A55] bg-[#073642] px-4 py-2.5 flex flex-wrap items-center gap-3 text-xs">
        {/* Title */}
        <div className="flex-1 min-w-[200px] flex items-center gap-2 bg-[#002B36] px-2.5 py-1.5 rounded-lg border border-[#1A4A55]">
          <Type className="w-3.5 h-3.5 text-[#2AA198]" />
          <input
            type="text"
            value={song.title}
            onChange={(e) => onUpdateSong({ title: e.target.value })}
            placeholder="Song Title"
            className="w-full bg-transparent text-[#FDF6E3] font-semibold focus:outline-none placeholder-[#93A1A1]/60"
          />
        </div>

        {/* Artist */}
        <div className="flex-1 min-w-[160px] flex items-center gap-2 bg-[#002B36] px-2.5 py-1.5 rounded-lg border border-[#1A4A55]">
          <span className="text-[#93A1A1] font-mono">ARTIST:</span>
          <input
            type="text"
            value={song.artist}
            onChange={(e) => onUpdateSong({ artist: e.target.value })}
            placeholder="Artist / Band"
            className="w-full bg-transparent text-[#EEE8D5] focus:outline-none placeholder-[#93A1A1]/60"
          />
        </div>

        {/* Key */}
        <div className="w-28 flex items-center gap-1.5 bg-[#002B36] px-2.5 py-1.5 rounded-lg border border-[#1A4A55]">
          <Music className="w-3.5 h-3.5 text-[#B58900]" />
          <span className="text-[#93A1A1] font-mono">KEY:</span>
          <input
            type="text"
            value={song.key}
            onChange={(e) => onUpdateSong({ key: e.target.value })}
            placeholder="e.g. G"
            className="w-full bg-transparent text-[#B58900] font-bold font-mono focus:outline-none text-center"
          />
        </div>

        {/* Capo */}
        <div className="w-32 flex items-center gap-1.5 bg-[#002B36] px-2.5 py-1.5 rounded-lg border border-[#1A4A55]">
          <Hash className="w-3.5 h-3.5 text-[#2AA198]" />
          <span className="text-[#93A1A1] font-mono">CAPO:</span>
          <input
            type="text"
            value={song.capo}
            onChange={(e) => onUpdateSong({ capo: e.target.value })}
            placeholder="No Capo"
            className="w-full bg-transparent text-[#EEE8D5] font-mono focus:outline-none text-center"
          />
        </div>

        {/* BPM */}
        <div className="w-28 flex items-center gap-1.5 bg-[#002B36] px-2.5 py-1.5 rounded-lg border border-[#1A4A55]">
          <Activity className="w-3.5 h-3.5 text-[#CB4B16]" />
          <span className="text-[#93A1A1] font-mono">BPM:</span>
          <input
            type="text"
            value={song.bpm}
            onChange={(e) => onUpdateSong({ bpm: e.target.value })}
            placeholder="120"
            className="w-full bg-transparent text-[#CB4B16] font-mono focus:outline-none text-center"
          />
        </div>

        {/* Format Badge */}
        <div className="flex items-center gap-1 bg-[#002B36] px-2.5 py-1.5 rounded-lg border border-[#1A4A55] font-mono text-[11px] text-[#93A1A1]">
          <FileCode className="w-3 h-3 text-[#2AA198]" />
          <span>{song.format === 'CHORD_PRO' ? 'ChordPro' : '2-Line Tabs'}</span>
        </div>
      </div>

      {/* Main Split Pane Workspace */}
      <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
        {/* Left Pane: Editor */}
        <div className="flex-1 flex flex-col border-b md:border-b-0 md:border-r border-[#1A4A55] bg-[#002B36] h-1/2 md:h-full">
          {/* Quick Section Snippets Toolbar */}
          <div className="px-3 py-1.5 bg-[#073642]/60 border-b border-[#1A4A55]/60 flex items-center gap-1.5 overflow-x-auto text-xs">
            <span className="text-[10px] font-mono text-[#93A1A1] mr-1 uppercase">Insert:</span>
            {['[Intro]', '[Verse 1]', '[Chorus]', '[Bridge]', '[Solo]', '[Outro]', '[Tab]'].map((tag) => (
              <button
                key={tag}
                type="button"
                onClick={() => insertTextAtCursor(tag)}
                className="px-2 py-0.5 rounded bg-[#002B36] border border-[#1A4A55] text-[#FDF6E3] hover:border-[#8B5CF6] hover:text-[#8B5CF6] font-mono text-[11px] transition-colors cursor-pointer"
              >
                {tag}
              </button>
            ))}
            <button
              type="button"
              onClick={() => insertTextAtCursor('[G]')}
              className="px-2 py-0.5 rounded bg-[#002B36] border border-[#1A4A55] text-[#B58900] hover:border-[#B58900] font-mono text-[11px] transition-colors cursor-pointer ml-auto flex items-center gap-1"
            >
              <Plus className="w-3 h-3" />
              <span>[Chord]</span>
            </button>
          </div>

          {/* Text Editor Area */}
          <div className="flex-1 relative flex overflow-hidden">
            <textarea
              ref={textareaRef}
              value={song.rawContent}
              onChange={(e) => onUpdateSong({ rawContent: e.target.value })}
              placeholder="Enter ChordPro lyrics with [G] chords or two-line chords over lyrics..."
              spellCheck={false}
              className="w-full h-full p-4 bg-[#002B36] text-[#FDF6E3] font-mono text-sm leading-relaxed focus:outline-none resize-none selection:bg-[#2AA198]/30 selection:text-[#FDF6E3] overflow-y-auto"
            />
          </div>

          {/* Left Footer Info */}
          <div className="h-7 px-3 bg-[#073642] border-t border-[#1A4A55] flex items-center justify-between text-[11px] font-mono text-[#93A1A1]">
            <span>Lines: {song.rawContent.split('\n').length} | Characters: {song.rawContent.length}</span>
            <span className="text-[#2AA198]">GTAR Native Parser Active</span>
          </div>
        </div>

        {/* Right Pane: Real-time Formatted Preview */}
        <div className="flex-1 flex flex-col bg-[#002B36] h-1/2 md:h-full overflow-hidden">
          <div className="px-4 py-2 bg-[#073642]/60 border-b border-[#1A4A55]/60 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono font-semibold text-[#B58900] uppercase tracking-wider">
                Stage Preview
              </span>
              {transposeOffset !== 0 && (
                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[#B58900]/15 text-[#B58900] border border-[#B58900]/30">
                  Transposed ({transposeOffset > 0 ? `+${transposeOffset}` : transposeOffset})
                </span>
              )}
            </div>
            <span className="text-[10px] font-mono text-[#93A1A1]">
              Live 1:1 Rendering
            </span>
          </div>

          {/* Formatted Song Rendering */}
          <div className="flex-1 p-6 overflow-y-auto bg-[#002B36] select-text">
            {/* Song Preview Header */}
            <div className="mb-6 pb-4 border-b border-[#1A4A55]/50">
              <h1 className="text-2xl font-bold text-[#FDF6E3] tracking-tight mb-1">
                {song.title || 'Untitled Song'}
              </h1>
              <div className="flex flex-wrap items-center gap-3 text-xs text-[#93A1A1] font-mono">
                {song.artist && <span className="text-[#2AA198] font-semibold">{song.artist}</span>}
                {song.key && (
                  <span className="px-2 py-0.5 rounded bg-[#073642] border border-[#1A4A55] text-[#B58900] font-bold">
                    Key: {song.key}
                  </span>
                )}
                {song.capo && (
                  <span className="px-2 py-0.5 rounded bg-[#073642] border border-[#1A4A55] text-[#EEE8D5]">
                    Capo: {song.capo}
                  </span>
                )}
                {song.bpm && (
                  <span className="px-2 py-0.5 rounded bg-[#073642] border border-[#1A4A55] text-[#CB4B16]">
                    {song.bpm} BPM
                  </span>
                )}
              </div>
            </div>

            {/* High-Contrast Structured Rendering with Spaced Chord Pills & Badges */}
            <SongLineRenderer lines={parsedSong.lines} fontSizePx={16} />
          </div>

          {/* Right Footer */}
          <div className="h-7 px-4 bg-[#073642] border-t border-[#1A4A55] flex items-center justify-between text-[11px] font-mono text-[#93A1A1]">
            <span>Preview Mode: High Contrast Solarized</span>
            <span className="text-[#859900]">Stage Ready</span>
          </div>
        </div>
      </div>
    </div>
  )
}
