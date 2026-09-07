import React, { useState, useRef, useMemo } from 'react'

import {
  X,
  Download,
  Upload,
  Copy,
  Check,
  FileJson,
  Music,
  ListMusic,
  AlertCircle
} from 'lucide-react'
import type { ActiveSongState, SongEntity, GtarSetlist, GtarBackup } from '../types/gtar'

interface JsonBridgeModalProps {
  isOpen: boolean
  initialTab?: 'export' | 'import'
  onClose: () => void
  song: ActiveSongState
  onImportSong: (imported: Partial<ActiveSongState>) => void
}

export const JsonBridgeModal: React.FC<JsonBridgeModalProps> = ({
  isOpen,
  initialTab = 'export',
  onClose,
  song,
  onImportSong,
}) => {
  const [tab, setTab] = useState<'export' | 'import'>(initialTab)
  const [exportFormat, setExportFormat] = useState<'song' | 'setlist'>('song')
  const [setlistName, setSetlistName] = useState('My Setlist')
  const [copied, setCopied] = useState(false)

  // Import states
  const [importJsonText, setImportJsonText] = useState('')
  const [importError, setImportError] = useState<string | null>(null)
  const [detectedSongs, setDetectedSongs] = useState<Array<{ title: string; artist?: string; key?: string; content: string }>>([])
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [exportTimestamp] = useState(() => Date.now())

  // Generate Song Room Entity JSON payload & Setlist JSON payload in useMemo
  const { songRoomPayload, setlistPayload } = useMemo(() => {
    const isoString = new Date(exportTimestamp).toISOString()


    const songEntity: SongEntity = {
      id: song.id || 0,
      title: song.title || 'Untitled Song',
      artist: song.artist || null,
      key: song.key || null,
      capo: song.capo || null,
      rawContent: song.rawContent,
      format: song.format,
      isFavorite: false,
      transposeOffset: song.transposeOffset || 0,
      tags: '',
      isDeleted: false,
      createdAt: exportTimestamp,
      lastOpenedAt: exportTimestamp,
    }


    const gtarSetlist: GtarSetlist = {
      version: 1,
      type: 'GTAR_SETLIST',
      name: setlistName.trim() || 'GTAR Setlist',
      createdAt: isoString,
      songs: [
        {
          title: song.title || 'Untitled Song',
          artist: song.artist || '',
          key: song.key || '',
          chordsContent: song.rawContent,
          order: 1,
        },
      ],
    }

    return { songRoomPayload: songEntity, setlistPayload: gtarSetlist }
  }, [song, setlistName, exportTimestamp])


  if (!isOpen) return null


  const exportPayloadString =
    exportFormat === 'song'
      ? JSON.stringify(songRoomPayload, null, 2)
      : JSON.stringify(setlistPayload, null, 2)

  const handleCopy = () => {
    navigator.clipboard.writeText(exportPayloadString).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  const handleDownload = () => {
    const filename =
      exportFormat === 'song'
        ? `${(song.title || 'song').toLowerCase().replace(/[^a-z0-9]/gi, '_')}.song.json`
        : `${(setlistName || 'setlist').toLowerCase().replace(/[^a-z0-9]/gi, '_')}.setlist.json`

    const blob = new Blob([exportPayloadString], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const parseJsonData = (text: string) => {
    setImportError(null)
    setDetectedSongs([])

    const trimmed = text.trim()
    if (!trimmed) {
      setImportError('Please enter or paste JSON content.')
      return
    }

    try {
      const data = JSON.parse(trimmed)

      // Case 1: GTAR Setlist JSON format (type: "GTAR_SETLIST")
      if (data.type === 'GTAR_SETLIST' && Array.isArray(data.songs)) {
        const list = (data as GtarSetlist).songs.map((s) => ({
          title: s.title,
          artist: s.artist,
          key: s.key,
          content: s.chordsContent,
        }))
        if (list.length === 0) {
          setImportError('Setlist contains no songs.')
        } else {
          setDetectedSongs(list)
        }
        return
      }

      // Case 2: GTAR Backup JSON format (metadata.appName === "GTAR")
      if (data.metadata?.appName === 'GTAR' && Array.isArray(data.songs)) {
        const list = (data as GtarBackup).songs.map((s) => ({
          title: s.title,
          artist: s.artist || undefined,
          key: s.key || undefined,
          content: s.rawContent,
        }))
        if (list.length === 0) {
          setImportError('Backup contains no active songs.')
        } else {
          setDetectedSongs(list)
        }
        return
      }

      // Case 3: Single Song Room Entity (has rawContent or chordsContent)
      if (data.title && (data.rawContent || data.chordsContent)) {
        setDetectedSongs([
          {
            title: data.title,
            artist: data.artist || undefined,
            key: data.key || undefined,
            content: data.rawContent || data.chordsContent,
          },
        ])
        return
      }

      // Case 4: Generic Array of songs
      if (Array.isArray(data) && data.length > 0 && (data[0].rawContent || data[0].chordsContent || data[0].title)) {
        setDetectedSongs(
          data.map((item) => ({
            title: item.title || 'Untitled',
            artist: item.artist,
            key: item.key,
            content: item.rawContent || item.chordsContent || '',
          }))
        )
        return
      }

      setImportError('Unrecognized GTAR JSON format. Expected GTAR_SETLIST, Backup JSON, or SongEntity.')
    } catch (err: unknown) {
      setImportError(`JSON syntax error: ${err instanceof Error ? err.message : 'Invalid JSON'}`)
    }
  }

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const reader = new FileReader()
    reader.onload = (evt) => {
      const content = evt.target?.result as string
      setImportJsonText(content)
      parseJsonData(content)
    }
    reader.readAsText(file)
  }

  const handleSelectSongToLoad = (item: { title: string; artist?: string; key?: string; content: string }) => {
    onImportSong({
      title: item.title,
      artist: item.artist || '',
      key: item.key || '',
      rawContent: item.content,
      transposeOffset: 0,
    })
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 select-none">
      <div className="w-full max-w-2xl rounded-2xl border border-[#1A4A55] bg-[#073642] shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Modal Header */}
        <div className="px-6 py-4 border-b border-[#1A4A55] flex items-center justify-between bg-[#002B36]">
          <div className="flex items-center gap-2">
            <FileJson className="w-5 h-5 text-[#2AA198]" />
            <h2 className="text-base font-bold text-[#FDF6E3]">
              GTAR JSON Bridge (v1.0.39+ Schema)
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-[#93A1A1] hover:text-[#FDF6E3] hover:bg-[#073642] cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-[#1A4A55] bg-[#002B36]/60 px-6">
          <button
            type="button"
            onClick={() => setTab('export')}
            className={`py-3 px-4 text-xs font-bold border-b-2 flex items-center gap-2 cursor-pointer transition-colors ${
              tab === 'export'
                ? 'border-[#B58900] text-[#B58900]'
                : 'border-transparent text-[#93A1A1] hover:text-[#FDF6E3]'
            }`}
          >
            <Download className="w-4 h-4" />
            <span>Export to Android</span>
          </button>
          <button
            type="button"
            onClick={() => setTab('import')}
            className={`py-3 px-4 text-xs font-bold border-b-2 flex items-center gap-2 cursor-pointer transition-colors ${
              tab === 'import'
                ? 'border-[#2AA198] text-[#2AA198]'
                : 'border-transparent text-[#93A1A1] hover:text-[#FDF6E3]'
            }`}
          >
            <Upload className="w-4 h-4" />
            <span>Import from Android / JSON</span>
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 overflow-y-auto flex-1 text-xs">
          {tab === 'export' ? (
            <div className="space-y-4">
              {/* Format selection */}
              <div>
                <label className="block text-[#93A1A1] font-mono text-[11px] mb-2 uppercase">
                  Select Android Export Schema:
                </label>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => setExportFormat('song')}
                    className={`p-3 rounded-xl border text-left flex items-start gap-2.5 cursor-pointer transition-all ${
                      exportFormat === 'song'
                        ? 'border-[#2AA198] bg-[#002B36] text-[#FDF6E3] ring-1 ring-[#2AA198]'
                        : 'border-[#1A4A55] bg-[#002B36]/50 text-[#93A1A1] hover:border-[#1A4A55]'
                    }`}
                  >
                    <Music className="w-4 h-4 text-[#2AA198] shrink-0 mt-0.5" />
                    <div>
                      <div className="font-bold text-xs text-[#FDF6E3]">Room SongEntity (.json)</div>
                      <div className="text-[11px] text-[#93A1A1] mt-0.5">
                        Native GTAR single-song room model
                      </div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setExportFormat('setlist')}
                    className={`p-3 rounded-xl border text-left flex items-start gap-2.5 cursor-pointer transition-all ${
                      exportFormat === 'setlist'
                        ? 'border-[#B58900] bg-[#002B36] text-[#FDF6E3] ring-1 ring-[#B58900]'
                        : 'border-[#1A4A55] bg-[#002B36]/50 text-[#93A1A1] hover:border-[#1A4A55]'
                    }`}
                  >
                    <ListMusic className="w-4 h-4 text-[#B58900] shrink-0 mt-0.5" />
                    <div>
                      <div className="font-bold text-xs text-[#FDF6E3]">GTAR Setlist (.json)</div>
                      <div className="text-[11px] text-[#93A1A1] mt-0.5">
                        GTAR_SETLIST v1 share payload
                      </div>
                    </div>
                  </button>
                </div>
              </div>

              {exportFormat === 'setlist' && (
                <div>
                  <label className="block text-[#93A1A1] font-mono text-[11px] mb-1 uppercase">
                    Setlist Name:
                  </label>
                  <input
                    type="text"
                    value={setlistName}
                    onChange={(e) => setSetlistName(e.target.value)}
                    placeholder="Enter setlist name"
                    className="w-full px-3 py-2 rounded-lg bg-[#002B36] border border-[#1A4A55] text-[#FDF6E3] focus:outline-none focus:border-[#B58900]"
                  />
                </div>
              )}

              {/* JSON Preview Box */}
              <div>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-[#93A1A1] font-mono text-[11px] uppercase">
                    Generated JSON Payload:
                  </span>
                  <span className="text-[#93A1A1] font-mono text-[10px]">
                    {exportPayloadString.length} bytes
                  </span>
                </div>
                <textarea
                  readOnly
                  value={exportPayloadString}
                  rows={8}
                  className="w-full p-3 rounded-xl bg-[#002B36] border border-[#1A4A55] text-[#2AA198] font-mono text-xs focus:outline-none resize-none selection:bg-[#2AA198]/20"
                />
              </div>

              {/* Export Action Buttons */}
              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={handleCopy}
                  className="px-4 py-2.5 rounded-xl border border-[#1A4A55] bg-[#002B36] text-[#EEE8D5] hover:border-[#2AA198] hover:text-[#2AA198] font-semibold flex items-center gap-2 transition-colors cursor-pointer"
                >
                  {copied ? <Check className="w-4 h-4 text-[#859900]" /> : <Copy className="w-4 h-4" />}
                  <span>{copied ? 'Copied to Clipboard' : 'Copy JSON'}</span>
                </button>
                <button
                  type="button"
                  onClick={handleDownload}
                  className="px-5 py-2.5 rounded-xl bg-[#B58900] hover:bg-[#B58900]/90 text-[#002B36] font-bold flex items-center gap-2 transition-colors cursor-pointer shadow-lg shadow-[#B58900]/20"
                >
                  <Download className="w-4 h-4" />
                  <span>Download .json File</span>
                </button>
              </div>
            </div>
          ) : (
            /* Import Tab */
            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-[#93A1A1] font-mono text-[11px] uppercase">
                    Paste JSON or Upload File:
                  </label>
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="text-xs text-[#2AA198] hover:underline flex items-center gap-1 cursor-pointer font-medium"
                  >
                    <Upload className="w-3.5 h-3.5" />
                    <span>Upload .json File</span>
                  </button>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".json"
                    onChange={handleFileUpload}
                    className="hidden"
                  />
                </div>
                <textarea
                  value={importJsonText}
                  onChange={(e) => {
                    setImportJsonText(e.target.value)
                    parseJsonData(e.target.value)
                  }}
                  placeholder="Paste GTAR Setlist JSON, SongEntity JSON, or Backup JSON here..."
                  rows={6}
                  className="w-full p-3 rounded-xl bg-[#002B36] border border-[#1A4A55] text-[#FDF6E3] font-mono text-xs focus:outline-none focus:border-[#2AA198] resize-none"
                />
              </div>

              {importError && (
                <div className="p-3 rounded-xl bg-[#DC6E67]/10 border border-[#DC6E67]/40 text-[#DC6E67] flex items-center gap-2 font-mono">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{importError}</span>
                </div>
              )}

              {detectedSongs.length > 0 && (
                <div>
                  <div className="text-[#93A1A1] font-mono text-[11px] uppercase mb-2">
                    Detected Songs ({detectedSongs.length}):
                  </div>
                  <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
                    {detectedSongs.map((s, idx) => (
                      <div
                        key={idx}
                        className="p-3 rounded-xl bg-[#002B36] border border-[#1A4A55] flex items-center justify-between hover:border-[#2AA198] transition-colors"
                      >
                        <div>
                          <div className="font-bold text-[#FDF6E3]">{s.title}</div>
                          <div className="text-[11px] text-[#93A1A1] mt-0.5 flex items-center gap-2">
                            {s.artist && <span>{s.artist}</span>}
                            {s.key && <span className="text-[#B58900] font-mono">Key: {s.key}</span>}
                          </div>
                        </div>
                        <button
                          type="button"
                          onClick={() => handleSelectSongToLoad(s)}
                          className="px-3 py-1.5 rounded-lg bg-[#2AA198] text-[#002B36] font-bold text-xs hover:bg-[#35B8AD] transition-colors cursor-pointer"
                        >
                          Load into Editor
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
