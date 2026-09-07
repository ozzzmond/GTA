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
} from 'lucide-react'
import { bandSync, type BandSyncState } from '../utils/bandSync'
import { metronome, type MetronomeState } from '../utils/metronome'

interface BandSyncModalProps {
  isOpen: boolean
  onClose: () => void
  initialTab?: 'metronome' | 'tuner' | 'sync'
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
}) => {
  const [activeTab, setActiveTab] = useState<'metronome' | 'tuner' | 'sync'>(initialTab)
  const [syncState, setSyncState] = useState<BandSyncState>(() => bandSync.getState())
  const [metroState, setMetroState] = useState<MetronomeState>(() => metronome.getState())
  const [activeTuningString, setActiveTuningString] = useState<string | null>(null)
  const [audioCtx, setAudioCtx] = useState<AudioContext | null>(null)
  const [activeOsc, setActiveOsc] = useState<OscillatorNode | null>(null)
  const tapTimesRef = useRef<number[]>([])

  useEffect(() => {
    setActiveTab(initialTab)
  }, [initialTab, isOpen])

  // Sync state subscription
  useEffect(() => {
    const unsub = bandSync.subscribe((st) => setSyncState(st))
    return unsub
  }, [])

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
            <div className="space-y-5">
              {/* Role Selector Pills */}
              <div className="grid grid-cols-3 gap-2">
                <button
                  type="button"
                  onClick={() => bandSync.setRole('OFF')}
                  className={`py-2 rounded-xl text-xs font-bold border transition-all cursor-pointer ${
                    syncState.role === 'OFF'
                      ? 'bg-[#002B36] border-[#93A1A1] text-[#FDF6E3]'
                      : 'bg-[#073642] border-[#1A4A55] text-[#93A1A1] hover:text-[#FDF6E3]'
                  }`}
                >
                  Off
                </button>

                <button
                  type="button"
                  onClick={() => bandSync.setRole('HOST')}
                  className={`py-2 rounded-xl text-xs font-bold border transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                    syncState.role === 'HOST'
                      ? 'bg-[#B58900] border-[#B58900] text-[#002B36] font-extrabold shadow-md'
                      : 'bg-[#073642] border-[#1A4A55] text-[#93A1A1] hover:text-[#B58900]'
                  }`}
                >
                  <Radio className="w-3.5 h-3.5" />
                  <span>Band Leader</span>
                </button>

                <button
                  type="button"
                  onClick={() => bandSync.setRole('CLIENT')}
                  className={`py-2 rounded-xl text-xs font-bold border transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                    syncState.role === 'CLIENT'
                      ? 'bg-[#2AA198] border-[#2AA198] text-[#002B36] font-extrabold shadow-md'
                      : 'bg-[#073642] border-[#1A4A55] text-[#93A1A1] hover:text-[#2AA198]'
                  }`}
                >
                  <Users className="w-3.5 h-3.5" />
                  <span>Band Member</span>
                </button>
              </div>

              {/* State Details */}
              {syncState.role === 'OFF' && (
                <div className="p-5 rounded-2xl bg-[#002B36] border border-[#1A4A55] text-center space-y-3">
                  <div className="w-12 h-12 rounded-full bg-[#073642] border border-[#1A4A55] flex items-center justify-center text-[#B58900] mx-auto">
                    <Wifi className="w-6 h-6" />
                  </div>
                  <h3 className="text-sm font-bold text-[#FDF6E3]">
                    Play Together (Multi-Device Stage Sync)
                  </h3>
                  <p className="text-xs text-[#93A1A1] leading-relaxed">
                    Zero-latency song and scroll syncing for your whole band across browsers,
                    tablets, laptops, and stage teleprompter screens without internet.
                  </p>
                  <div className="grid grid-cols-2 gap-3 pt-2">
                    <button
                      type="button"
                      onClick={() => bandSync.setRole('HOST')}
                      className="py-2.5 rounded-xl bg-[#B58900] text-[#002B36] font-bold text-xs hover:bg-[#D4A017] transition-colors cursor-pointer"
                    >
                      Host as Leader
                    </button>
                    <button
                      type="button"
                      onClick={() => bandSync.setRole('CLIENT')}
                      className="py-2.5 rounded-xl bg-[#002B36] border border-[#2AA198] text-[#2AA198] font-bold text-xs hover:bg-[#2AA198]/15 transition-colors cursor-pointer"
                    >
                      Join as Member
                    </button>
                  </div>
                </div>
              )}

              {syncState.role === 'HOST' && (
                <div className="p-5 rounded-2xl bg-[#002B36] border border-[#B58900] space-y-4">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-[#10B981] animate-pulse" />
                    <span className="text-xs font-black uppercase text-[#10B981] tracking-wide">
                      BAND LEADER MODE ACTIVE
                    </span>
                  </div>

                  <div className="text-xs text-[#FDF6E3] font-semibold">
                    Broadcasting on Stage Channel • Connected Members:{' '}
                    <span className="text-[#B58900] font-bold">{syncState.connectedPeers}</span>
                  </div>

                  <p className="text-xs text-[#93A1A1] leading-relaxed">
                    Whenever you select a song, change key, or scroll your Stage Viewer, all
                    connected band members will synchronously follow your screen with zero latency.
                  </p>

                  <button
                    type="button"
                    onClick={() => bandSync.setRole('OFF')}
                    className="w-full py-2.5 rounded-xl bg-[#DC6E67] text-white font-bold text-xs hover:bg-[#E53935] transition-colors cursor-pointer"
                  >
                    Stop Band Host
                  </button>
                </div>
              )}

              {syncState.role === 'CLIENT' && (
                <div className="p-5 rounded-2xl bg-[#002B36] border border-[#2AA198] space-y-4">
                  <div className="flex items-center gap-2">
                    <span className={`w-2.5 h-2.5 rounded-full ${syncState.wsConnected ? 'bg-[#22C55E]' : 'bg-[#EAB308]'} animate-pulse`} />
                    <span className={`text-xs font-black uppercase ${syncState.wsConnected ? 'text-[#22C55E]' : 'text-[#EAB308]'} tracking-wide`}>
                      {syncState.wsConnected ? 'CONNECTED TO STAGE LEADER' : 'CONNECT TO STAGE LEADER'}
                    </span>
                  </div>

                  <p className="text-xs text-[#93A1A1] leading-relaxed">
                    Connect directly to the Android Stage Leader tablet or phone over local Wi-Fi / Hotspot on port 8765.
                  </p>

                  <div className="space-y-2">
                    <label className="text-[11px] font-bold text-[#93A1A1] uppercase tracking-wider block">
                      Leader Device IP Address
                    </label>
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        value={syncState.wsLeaderIp || ''}
                        onChange={(e) => {
                          const newIp = e.target.value
                          bandSync.connectWebSocket(newIp)
                        }}
                        placeholder="e.g. 192.168.43.1"
                        className="flex-1 px-3 py-2 rounded-xl bg-[#073642] border border-[#1A4A55] text-xs font-mono text-[#FDF6E3] focus:border-[#2AA198] outline-none"
                      />
                      <button
                        type="button"
                        onClick={() => {
                          if (syncState.wsLeaderIp) {
                            bandSync.connectWebSocket(syncState.wsLeaderIp)
                          }
                        }}
                        className="px-3 py-2 rounded-xl bg-[#2AA198] text-[#002B36] font-bold text-xs hover:bg-[#20827a] transition-colors cursor-pointer"
                      >
                        {syncState.wsConnected ? 'Reconnect' : 'Connect'}
                      </button>
                    </div>
                    {syncState.wsStatus && (
                      <div className="text-[11px] font-mono text-[#2AA198] pt-1">
                        {syncState.wsStatus}
                      </div>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      bandSync.disconnectWebSocket()
                      bandSync.setRole('OFF')
                    }}
                    className="w-full py-2.5 rounded-xl bg-[#073642] border border-[#1A4A55] text-[#93A1A1] hover:text-[#DC6E67] font-bold text-xs transition-colors cursor-pointer"
                  >
                    Disconnect
                  </button>
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
