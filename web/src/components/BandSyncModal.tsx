import React, { useState, useEffect, useRef } from 'react'
import {
  X,
  Radio,
  Users,
  Gauge,
  Sliders,
  Volume2,
  Play,
  Pause,
  Plus,
  Minus,
  Wifi,
  Copy,
  Check,
  Loader2,
  Share2,
  Edit3,
} from 'lucide-react'
import { bandSync, type BandSyncState } from '../utils/bandSync'
import { metronome, type MetronomeState } from '../utils/metronome'

interface BandSyncModalProps {
  isOpen: boolean
  onClose: () => void
  initialTab?: 'metronome' | 'tuner' | 'sync'
  onPushSetlist?: () => { success: boolean; message: string }
  activeSetlistName?: string
  activeSetlistSongCount?: number
}

const GUITAR_STRINGS = [
  { name: 'E2 (6th)', freq: 82.41 },
  { name: 'A2 (5th)', freq: 110.0 },
  { name: 'D3 (4th)', freq: 146.83 },
  { name: 'G3 (3rd)', freq: 196.0 },
  { name: 'B3 (2nd)', freq: 246.94 },
  { name: 'E4 (1st)', freq: 329.63 },
]

export const BandSyncModal: React.FC<BandSyncModalProps> = ({
  isOpen,
  onClose,
  initialTab = 'sync',
  onPushSetlist,
  activeSetlistName,
  activeSetlistSongCount = 0,
}) => {
  const [activeTab, setActiveTab] = useState<'metronome' | 'tuner' | 'sync'>(initialTab)
  const [syncState, setSyncState] = useState<BandSyncState>(() => bandSync.getState())
  const [metroState, setMetroState] = useState<MetronomeState>(() => metronome.getState())
  const [activeTuningString, setActiveTuningString] = useState<string | null>(null)
  const [audioCtx, setAudioCtx] = useState<AudioContext | null>(null)
  const [activeOsc, setActiveOsc] = useState<OscillatorNode | null>(null)
  const tapTimesRef = useRef<number[]>([])

  // Band Sync local UI state
  const [syncSubTab, setSyncSubTab] = useState<'leader' | 'member'>(() =>
    syncState.role === 'CLIENT' ? 'member' : 'leader'
  )
  const [inputIp, setInputIp] = useState<string>(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('gtar_band_sync_leader_ip') || ''
    }
    return ''
  })
  const [customHostIpInput, setCustomHostIpInput] = useState<string>(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('gtar_band_sync_custom_host_ip') || ''
    }
    return ''
  })
  const [isEditingHostIp, setIsEditingHostIp] = useState(false)
  const [hasCopiedLeaderInfo, setHasCopiedLeaderInfo] = useState(false)
  const [pushStatusMessage, setPushStatusMessage] = useState<string | null>(null)

  useEffect(() => {
    setActiveTab(initialTab)
  }, [initialTab, isOpen])

  // Sync state subscription
  useEffect(() => {
    const unsub = bandSync.subscribe((st) => setSyncState(st))
    return unsub
  }, [])

  // Keep subtab and input in sync with external role changes
  useEffect(() => {
    if (syncState.role === 'CLIENT') {
      setSyncSubTab('member')
    } else if (syncState.role === 'HOST') {
      setSyncSubTab('leader')
    }
    if (syncState.wsLeaderIp && !inputIp) {
      setInputIp(syncState.wsLeaderIp)
    }
  }, [syncState.role, syncState.wsLeaderIp])

  const handleCopyLeaderInfo = () => {
    const text = `GTAR Band Sync Info:\nWebSocket Endpoint: ${syncState.leaderEndpoint}\nRoom ID: ${syncState.roomId}\nPort: 8765`
    if (navigator.clipboard) {
      navigator.clipboard
        .writeText(text)
        .then(() => {
          setHasCopiedLeaderInfo(true)
          setTimeout(() => setHasCopiedLeaderInfo(false), 2000)
        })
        .catch(() => {})
    }
  }

  // Metronome subscription
  useEffect(() => {
    const unsub = metronome.subscribe((st) => setMetroState(st))
    return unsub
  }, [])

  // Stop tuning oscillator when modal closes
  useEffect(() => {
    return () => {
      if (activeOsc) {
        try {
          activeOsc.stop()
          activeOsc.disconnect()
        } catch {}
      }
    }
  }, [activeOsc])

  if (!isOpen) return null

  // Tap tempo handler
  const handleTapTempo = () => {
    const now = performance.now()
    const recent = [...tapTimesRef.current, now].filter((t) => now - t < 3000)
    tapTimesRef.current = recent
    if (recent.length >= 2) {
      const intervals = []
      for (let i = 1; i < recent.length; i++) {
        intervals.push(recent[i] - recent[i - 1])
      }
      const avgMs = intervals.reduce((a, b) => a + b, 0) / intervals.length
      const calculatedBpm = Math.round(60000 / avgMs)
      if (calculatedBpm >= 30 && calculatedBpm <= 300) {
        metronome.setBpm(calculatedBpm)
      }
    }
  }

  // Play reference guitar tuning tone
  const playTuningTone = (stringName: string, freq: number) => {
    if (activeTuningString === stringName) {
      if (activeOsc) {
        try {
          activeOsc.stop()
          activeOsc.disconnect()
        } catch {}
      }
      setActiveOsc(null)
      setActiveTuningString(null)
      return
    }

    if (activeOsc) {
      try {
        activeOsc.stop()
        activeOsc.disconnect()
      } catch {}
    }

    const ctx = audioCtx || new (window.AudioContext || (window as any).webkitAudioContext)()
    if (!audioCtx) setAudioCtx(ctx)

    if (ctx.state === 'suspended') {
      ctx.resume()
    }

    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'triangle'
    osc.frequency.setValueAtTime(freq, ctx.currentTime)

    gain.gain.setValueAtTime(0.3, ctx.currentTime)
    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start()
    setActiveOsc(osc)
    setActiveTuningString(stringName)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in">
      <div className="w-full max-w-lg rounded-2xl bg-[#073642] border border-[#1A4A55] shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b border-[#1A4A55] flex items-center justify-between bg-[#002B36]/50">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#2AA198]/20 border border-[#2AA198]/30 flex items-center justify-center text-[#2AA198]">
              <Radio className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#FDF6E3]">STAGE TOOLS & SYNC</h2>
              <p className="text-xs text-[#93A1A1]">Metronome, Guitar Tuner & Band Sync</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-[#93A1A1] hover:text-[#FDF6E3] hover:bg-[#002B36] transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* 3-Tab Segmented Switcher */}
        <div className="p-3 border-b border-[#1A4A55] bg-[#002B36]/30">
          <div className="grid grid-cols-3 gap-1 bg-[#002B36] p-1 rounded-xl border border-[#1A4A55]">
            <button
              type="button"
              onClick={() => setActiveTab('metronome')}
              className={`py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                activeTab === 'metronome'
                  ? 'bg-[#2AA198] text-[#002B36] shadow-sm'
                  : 'text-[#93A1A1] hover:text-[#FDF6E3]'
              }`}
            >
              <Gauge className="w-3.5 h-3.5" />
              <span>Metronome</span>
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('tuner')}
              className={`py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                activeTab === 'tuner'
                  ? 'bg-[#2AA198] text-[#002B36] shadow-sm'
                  : 'text-[#93A1A1] hover:text-[#FDF6E3]'
              }`}
            >
              <Sliders className="w-3.5 h-3.5" />
              <span>Guitar Tuner</span>
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('sync')}
              className={`py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                activeTab === 'sync'
                  ? 'bg-[#B58900] text-[#002B36] shadow-sm'
                  : 'text-[#93A1A1] hover:text-[#FDF6E3]'
              }`}
            >
              <Wifi className="w-3.5 h-3.5" />
              <span>Band Sync</span>
            </button>
          </div>
        </div>

        {/* Tab Content Panel */}
        <div className="p-6 overflow-y-auto flex-1">
          {/* 1. METRONOME PANEL */}
          {activeTab === 'metronome' && (
            <div className="space-y-6 text-center">
              {/* Large BPM Display */}
              <div className="py-2">
                <div className="text-6xl font-black font-mono text-[#FDF6E3] tracking-tighter">
                  {metroState.bpm}
                </div>
                <div className="text-xs font-bold uppercase tracking-widest text-[#2AA198] mt-1">
                  BPM (Beats Per Minute)
                </div>
              </div>

              {/* Slider & Steppers */}
              <div className="space-y-3">
                <input
                  type="range"
                  min="30"
                  max="280"
                  value={metroState.bpm}
                  onChange={(e) => metronome.setBpm(parseInt(e.target.value, 10))}
                  className="w-full accent-[#2AA198] cursor-pointer"
                />

                <div className="flex items-center justify-center gap-2">
                  <button
                    type="button"
                    onClick={() => metronome.setBpm(metroState.bpm - 5)}
                    className="px-2.5 py-1 rounded-lg bg-[#002B36] border border-[#1A4A55] text-xs font-bold text-[#EEE8D5] hover:text-[#2AA198] cursor-pointer"
                  >
                    -5
                  </button>
                  <button
                    type="button"
                    onClick={() => metronome.setBpm(metroState.bpm - 1)}
                    className="p-1.5 rounded-lg bg-[#002B36] border border-[#1A4A55] text-[#EEE8D5] hover:text-[#2AA198] cursor-pointer"
                  >
                    <Minus className="w-4 h-4" />
                  </button>

                  <button
                    type="button"
                    onClick={handleTapTempo}
                    className="px-5 py-2 rounded-xl bg-[#002B36] border border-[#2AA198] text-xs font-extrabold text-[#2AA198] hover:bg-[#2AA198]/15 cursor-pointer uppercase tracking-wider active:scale-95 transition-transform"
                  >
                    Tap Tempo
                  </button>

                  <button
                    type="button"
                    onClick={() => metronome.setBpm(metroState.bpm + 1)}
                    className="p-1.5 rounded-lg bg-[#002B36] border border-[#1A4A55] text-[#EEE8D5] hover:text-[#2AA198] cursor-pointer"
                  >
                    <Plus className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => metronome.setBpm(metroState.bpm + 5)}
                    className="px-2.5 py-1 rounded-lg bg-[#002B36] border border-[#1A4A55] text-xs font-bold text-[#EEE8D5] hover:text-[#2AA198] cursor-pointer"
                  >
                    +5
                  </button>
                </div>
              </div>

              {/* Play / Stop Button */}
              <button
                type="button"
                onClick={() => metronome.toggle()}
                className={`w-full py-3.5 rounded-xl font-black text-sm flex items-center justify-center gap-2 cursor-pointer transition-all shadow-lg ${
                  metroState.isRunning
                    ? 'bg-[#DC6E67] text-white hover:bg-[#E53935]'
                    : 'bg-[#2AA198] text-[#002B36] hover:bg-[#35B8AD]'
                }`}
              >
                {metroState.isRunning ? <Pause className="w-5 h-5" /> : <Play className="w-5 h-5" />}
                <span>{metroState.isRunning ? 'STOP METRONOME' : 'START METRONOME'}</span>
              </button>
            </div>
          )}

          {/* 2. GUITAR TUNER PANEL */}
          {activeTab === 'tuner' && (
            <div className="space-y-4">
              <p className="text-xs text-[#93A1A1] text-center">
                Click any string to play standard guitar reference frequency:
              </p>

              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
                {GUITAR_STRINGS.map((str) => {
                  const isPlaying = activeTuningString === str.name
                  return (
                    <button
                      key={str.name}
                      type="button"
                      onClick={() => playTuningTone(str.name, str.freq)}
                      className={`p-3.5 rounded-xl border text-center transition-all cursor-pointer flex flex-col items-center gap-1 ${
                        isPlaying
                          ? 'bg-[#2AA198]/20 border-[#2AA198] text-[#2AA198] shadow-md'
                          : 'bg-[#002B36] border-[#1A4A55] text-[#EEE8D5] hover:border-[#2AA198]'
                      }`}
                    >
                      <Volume2
                        className={`w-4 h-4 ${isPlaying ? 'text-[#2AA198] animate-pulse' : 'text-[#93A1A1]'}`}
                      />
                      <span className="text-xs font-extrabold">{str.name}</span>
                      <span className="text-[10px] font-mono text-[#93A1A1]">
                        {str.freq.toFixed(1)} Hz
                      </span>
                    </button>
                  )
                })}
              </div>

              {activeTuningString && (
                <div className="p-3 rounded-xl bg-[#2AA198]/10 border border-[#2AA198]/30 text-center text-xs text-[#2AA198] font-bold">
                  Now playing reference tone for {activeTuningString}
                </div>
              )}
            </div>
          )}

          {/* 3. BAND SYNC PANEL */}
          {activeTab === 'sync' && (
            <div className="space-y-4">
              {/* Role / Sub-Tab Switcher: Band Leader vs Band Member vs Off */}
              <div className="grid grid-cols-3 gap-1.5 p-1 bg-[#002B36] rounded-xl border border-[#1A4A55]">
                <button
                  type="button"
                  onClick={() => {
                    setSyncSubTab('leader')
                    bandSync.setRole('HOST')
                  }}
                  className={`py-2 rounded-lg text-xs font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                    syncSubTab === 'leader'
                      ? 'bg-[#B58900] text-[#002B36] font-extrabold shadow-sm'
                      : 'text-[#93A1A1] hover:text-[#B58900]'
                  }`}
                >
                  <Radio className="w-3.5 h-3.5" />
                  <span>Band Leader</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setSyncSubTab('member')
                    if (syncState.role !== 'CLIENT') {
                      bandSync.setRole('CLIENT')
                    }
                  }}
                  className={`py-2 rounded-lg text-xs font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                    syncSubTab === 'member'
                      ? 'bg-[#2AA198] text-[#002B36] font-extrabold shadow-sm'
                      : 'text-[#93A1A1] hover:text-[#2AA198]'
                  }`}
                >
                  <Users className="w-3.5 h-3.5" />
                  <span>Band Member</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    bandSync.disconnectWebSocket()
                    bandSync.setRole('OFF')
                  }}
                  className={`py-2 rounded-lg text-xs font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                    syncState.role === 'OFF'
                      ? 'bg-[#073642] text-[#FDF6E3] font-extrabold border border-[#93A1A1]/40'
                      : 'text-[#93A1A1] hover:text-[#DC6E67]'
                  }`}
                >
                  <span>Sync Off</span>
                </button>
              </div>

              {/* A. BAND LEADER TAB */}
              {syncSubTab === 'leader' && (
                <div className="p-4 sm:p-5 rounded-2xl bg-[#002B36] border border-[#B58900] space-y-4 shadow-lg animate-fade-in">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span
                        className={`w-2.5 h-2.5 rounded-full ${
                          syncState.role === 'HOST' ? 'bg-[#10B981] animate-pulse' : 'bg-[#93A1A1]'
                        }`}
                      />
                      <span
                        className={`text-xs font-black uppercase tracking-wide ${
                          syncState.role === 'HOST' ? 'text-[#10B981]' : 'text-[#93A1A1]'
                        }`}
                      >
                        {syncState.role === 'HOST'
                          ? 'BAND LEADER ACTIVE (BROADCASTING)'
                          : 'BAND LEADER (STANDBY)'}
                      </span>
                    </div>

                    {syncState.role === 'HOST' && (
                      <span className="px-2 py-0.5 rounded-md bg-[#10B981]/20 border border-[#10B981]/40 text-[#10B981] font-mono text-[10px] font-bold">
                        LIVE
                      </span>
                    )}
                  </div>

                  {/* Connection Details Card */}
                  <div className="p-3.5 rounded-xl bg-[#073642] border border-[#1A4A55] space-y-3">
                    <div>
                      <div className="flex items-center justify-between text-[10px] font-bold uppercase tracking-wider text-[#93A1A1]">
                        <span>Active WebSocket Endpoint</span>
                        <button
                          type="button"
                          onClick={() => setIsEditingHostIp(!isEditingHostIp)}
                          className="text-[#2AA198] hover:underline flex items-center gap-1 cursor-pointer font-sans"
                        >
                          <Edit3 className="w-3 h-3" />
                          <span>{isEditingHostIp ? 'Done' : 'Set LAN IP'}</span>
                        </button>
                      </div>
                      <div className="mt-1">
                        {isEditingHostIp ? (
                          <div className="flex items-center gap-2">
                            <input
                              type="text"
                              value={customHostIpInput}
                              onChange={(e) => setCustomHostIpInput(e.target.value)}
                              placeholder="e.g. 192.168.1.10"
                              className="flex-1 px-3 py-1.5 rounded-lg bg-[#002B36] border border-[#2AA198] text-xs font-mono text-[#FDF6E3] focus:outline-none"
                            />
                            <button
                              type="button"
                              onClick={() => {
                                bandSync.setCustomHostIp(customHostIpInput)
                                setIsEditingHostIp(false)
                              }}
                              className="px-3 py-1.5 rounded-lg bg-[#2AA198] text-[#002B36] font-bold text-xs cursor-pointer"
                            >
                              Save
                            </button>
                          </div>
                        ) : (
                          <code className="block text-xs font-mono font-bold text-[#FDF6E3] bg-[#002B36] px-3 py-2 rounded-lg border border-[#1A4A55] truncate select-all">
                            {syncState.leaderEndpoint}
                          </code>
                        )}
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <div className="p-2.5 rounded-lg bg-[#002B36] border border-[#1A4A55]">
                        <div className="text-[9px] font-bold uppercase tracking-wider text-[#93A1A1]">
                          Stage Room ID
                        </div>
                        <div className="text-xs font-mono font-bold text-[#2AA198] mt-0.5 truncate">
                          {syncState.roomId}
                        </div>
                      </div>

                      <div className="p-2.5 rounded-lg bg-[#002B36] border border-[#1A4A55]">
                        <div className="text-[9px] font-bold uppercase tracking-wider text-[#93A1A1]">
                          Connected Clients
                        </div>
                        <div className="text-xs font-mono font-bold text-[#B58900] mt-0.5 flex items-center gap-1.5">
                          <Users className="w-3.5 h-3.5 text-[#B58900]" />
                          <span>
                            {syncState.connectedPeers} peer
                            {syncState.connectedPeers !== 1 ? 's' : ''}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* 1-Click Copy Connection Link / Room Info Button */}
                    <button
                      type="button"
                      onClick={handleCopyLeaderInfo}
                      className="w-full py-2.5 px-3 rounded-xl bg-[#002B36] border border-[#2AA198] hover:bg-[#2AA198]/15 text-[#2AA198] font-bold text-xs flex items-center justify-center gap-2 transition-all cursor-pointer shadow-sm active:scale-98"
                    >
                      {hasCopiedLeaderInfo ? (
                        <>
                          <Check className="w-4 h-4 text-[#10B981]" />
                          <span className="text-[#10B981]">Connection Info Copied!</span>
                        </>
                      ) : (
                        <>
                          <Copy className="w-4 h-4" />
                          <span>Copy Connection Link / Room Info</span>
                        </>
                      )}
                    </button>
                  </div>

                  {/* Push Setlist to Members (Band Leader Control) */}
                  <div className="p-3.5 rounded-xl bg-[#073642] border border-[#B58900]/40 space-y-2.5">
                    <div className="flex items-center justify-between">
                      <div className="text-xs font-bold text-[#FDF6E3] flex items-center gap-1.5">
                        <Share2 className="w-4 h-4 text-[#B58900]" />
                        <span>Direct Setlist Sharing</span>
                      </div>
                      <span className="text-[10px] font-mono text-[#B58900]">
                        {activeSetlistName ? `"${activeSetlistName}" (${activeSetlistSongCount} songs)` : 'No active setlist'}
                      </span>
                    </div>
                    <p className="text-[11px] text-[#93A1A1] leading-relaxed">
                      Push the active setlist and full song data directly to all connected band members with smart merge.
                    </p>
                    <button
                      type="button"
                      onClick={() => {
                        if (onPushSetlist) {
                          const res = onPushSetlist()
                          setPushStatusMessage(res.message)
                          setTimeout(() => setPushStatusMessage(null), 4000)
                        }
                      }}
                      className="w-full py-2.5 rounded-xl bg-[#B58900] hover:bg-[#D4A017] text-[#002B36] font-black text-xs transition-colors cursor-pointer shadow-md flex items-center justify-center gap-2"
                    >
                      <Share2 className="w-4 h-4" />
                      <span>Push Setlist to Members</span>
                    </button>
                    {pushStatusMessage && (
                      <div className="p-2 rounded-lg bg-[#002B36] border border-[#B58900] text-center text-xs font-bold text-[#B58900] animate-fade-in">
                        {pushStatusMessage}
                      </div>
                    )}
                  </div>

                  {/* Stage Broadcast Explanation */}
                  <div className="text-[11px] text-[#93A1A1] leading-relaxed space-y-1">
                    <p className="font-semibold text-[#EEE8D5]">
                      • Real-Time Stage Peer Status:{' '}
                      <span className="text-[#10B981]">
                        {syncState.role === 'HOST'
                          ? `Broadcasting to ${syncState.connectedPeers} stage peer(s)`
                          : 'Host standby'}
                      </span>
                    </p>
                    <p>
                      Band members on phones, tablets, or laptops will immediately mirror your active
                      song selection, transposed key, and autoscrolling.
                    </p>
                  </div>

                  {/* Host Toggle */}
                  {syncState.role !== 'HOST' ? (
                    <button
                      type="button"
                      onClick={() => bandSync.setRole('HOST')}
                      className="w-full py-2.5 rounded-xl bg-[#B58900] text-[#002B36] font-extrabold text-xs hover:bg-[#D4A017] transition-colors cursor-pointer shadow-md"
                    >
                      Start Broadcasting as Band Leader
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => bandSync.setRole('OFF')}
                      className="w-full py-2.5 rounded-xl bg-[#DC6E67] text-white font-bold text-xs hover:bg-[#E53935] transition-colors cursor-pointer"
                    >
                      Stop Band Host
                    </button>
                  )}
                </div>
              )}

              {/* B. BAND MEMBER TAB */}
              {syncSubTab === 'member' && (
                <div className="p-4 sm:p-5 rounded-2xl bg-[#002B36] border border-[#2AA198] space-y-4 shadow-lg animate-fade-in">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span
                        className={`w-2.5 h-2.5 rounded-full ${
                          syncState.wsStatus === 'Connected to Leader (synced)'
                            ? 'bg-[#10B981] animate-pulse'
                            : syncState.wsStatus === 'Connecting...'
                            ? 'bg-[#EAB308] animate-ping'
                            : 'bg-[#93A1A1]'
                        }`}
                      />
                      <span className="text-xs font-black uppercase text-[#FDF6E3] tracking-wide">
                        BAND MEMBER SYNC
                      </span>
                    </div>

                    {/* Explicit Connection States: Disconnected | Connecting... | Connected to Leader (synced) */}
                    <div
                      className={`px-2.5 py-1 rounded-lg text-xs font-mono font-bold flex items-center gap-1.5 ${
                        syncState.wsStatus === 'Connected to Leader (synced)'
                          ? 'bg-[#10B981]/20 border border-[#10B981]/40 text-[#10B981]'
                          : syncState.wsStatus === 'Connecting...'
                          ? 'bg-[#EAB308]/20 border border-[#EAB308]/40 text-[#EAB308]'
                          : 'bg-[#073642] border border-[#1A4A55] text-[#93A1A1]'
                      }`}
                    >
                      {syncState.wsStatus === 'Connecting...' && (
                        <Loader2 className="w-3 h-3 animate-spin text-[#EAB308]" />
                      )}
                      <span>{syncState.wsStatus}</span>
                    </div>
                  </div>

                  <p className="text-xs text-[#93A1A1] leading-relaxed">
                    Connect directly to the Android Stage Leader or web host over local Wi-Fi or hotspot.
                  </p>

                  {/* Leader IP / Address input */}
                  <div className="space-y-2">
                    <label className="text-[11px] font-bold text-[#93A1A1] uppercase tracking-wider block">
                      Leader IP / Address
                    </label>
                    <div className="flex items-center gap-2">
                      <div className="relative flex-1">
                        <input
                          type="text"
                          value={inputIp}
                          onChange={(e) => setInputIp(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' && inputIp.trim()) {
                              bandSync.connectWebSocket(inputIp.trim())
                            }
                          }}
                          placeholder="e.g. 192.168.1.50"
                          className="w-full px-3 py-2.5 pr-14 rounded-xl bg-[#073642] border border-[#1A4A55] text-xs font-mono text-[#FDF6E3] focus:border-[#2AA198] outline-none"
                        />
                        <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-mono text-[#93A1A1] pointer-events-none select-none">
                          :8765
                        </span>
                      </div>
                      <button
                        type="button"
                        disabled={!inputIp.trim()}
                        onClick={() => {
                          if (inputIp.trim()) {
                            bandSync.connectWebSocket(inputIp.trim())
                          }
                        }}
                        className="px-4 py-2.5 rounded-xl bg-[#2AA198] text-[#002B36] font-extrabold text-xs hover:bg-[#35B8AD] disabled:opacity-50 transition-colors cursor-pointer shadow-md"
                      >
                        {syncState.wsStatus === 'Connecting...'
                          ? 'Connecting...'
                          : syncState.wsStatus === 'Connected to Leader (synced)'
                          ? 'Reconnect'
                          : 'Connect'}
                      </button>
                    </div>
                    <p className="text-[10px] text-[#93A1A1] font-mono">
                      Default port is <span className="text-[#2AA198]">:8765</span>. Last successful address is automatically saved in local storage.
                    </p>
                  </div>

                  {/* Disconnect button if active */}
                  {(syncState.wsStatus !== 'Disconnected' || syncState.role === 'CLIENT') && (
                    <button
                      type="button"
                      onClick={() => {
                        bandSync.disconnectWebSocket()
                        bandSync.setRole('OFF')
                      }}
                      className="w-full py-2.5 rounded-xl bg-[#073642] border border-[#1A4A55] text-[#93A1A1] hover:text-[#DC6E67] hover:border-[#DC6E67]/50 font-bold text-xs transition-colors cursor-pointer"
                    >
                      Disconnect
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-3.5 border-t border-[#1A4A55] bg-[#002B36]/50 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2 rounded-xl bg-[#002B36] border border-[#1A4A55] text-xs font-bold text-[#93A1A1] hover:text-[#FDF6E3] transition-colors cursor-pointer"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  )
}
