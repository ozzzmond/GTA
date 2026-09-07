/**
 * Precision Metronome & Visual Beat Flasher Engine for GTAR Stage
 * Uses Web Audio API for tight timing and provides beat pulse callbacks
 */

export interface MetronomeState {
  isRunning: boolean
  isMuted: boolean
  bpm: number
  beatsPerBar: number
  currentBeat: number // 1-indexed (1, 2, 3, 4)
}

type BeatCallback = (beat: number, isAccented: boolean) => void

class MetronomeEngine {
  private audioCtx: AudioContext | null = null
  private timerId: number | null = null
  private listeners: Set<(state: MetronomeState) => void> = new Set()
  private beatListeners: Set<BeatCallback> = new Set()

  private state: MetronomeState = {
    isRunning: false,
    isMuted: false,
    bpm: 120,
    beatsPerBar: 4,
    currentBeat: 1,
  }

  constructor() {
    // Lazy AudioContext initialization
  }

  private initAudio() {
    if (!this.audioCtx) {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
      if (AudioContextClass) {
        this.audioCtx = new AudioContextClass()
      }
    }
    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume()
    }
  }

  private emitState() {
    const copy = { ...this.state }
    this.listeners.forEach((l) => l(copy))
  }

  public subscribe(listener: (state: MetronomeState) => void): () => void {
    this.listeners.add(listener)
    listener({ ...this.state })
    return () => this.listeners.delete(listener)
  }

  public onBeat(cb: BeatCallback): () => void {
    this.beatListeners.add(cb)
    return () => this.beatListeners.delete(cb)
  }

  public getState(): MetronomeState {
    return { ...this.state }
  }

  public setBpm(newBpm: number) {
    const clamped = Math.max(30, Math.min(300, newBpm))
    this.state.bpm = clamped
    this.emitState()

    // If already running, restart interval with new BPM
    if (this.state.isRunning) {
      this.stop()
      this.start()
    }
  }

  public setBeatsPerBar(beats: number) {
    this.state.beatsPerBar = Math.max(1, Math.min(16, beats))
    if (this.state.currentBeat > this.state.beatsPerBar) {
      this.state.currentBeat = 1
    }
    this.emitState()
  }

  public toggleMute() {
    this.state.isMuted = !this.state.isMuted
    this.emitState()
  }

  private playClick(isAccented: boolean) {
    if (this.state.isMuted || !this.audioCtx) return

    try {
      const osc = this.audioCtx.createOscillator()
      const gain = this.audioCtx.createGain()

      // High pitch for beat 1, lower for other beats
      osc.frequency.setValueAtTime(isAccented ? 1200 : 800, this.audioCtx.currentTime)
      osc.type = 'sine'

      gain.gain.setValueAtTime(0.3, this.audioCtx.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.001, this.audioCtx.currentTime + 0.04)

      osc.connect(gain)
      gain.connect(this.audioCtx.destination)

      osc.start()
      osc.stop(this.audioCtx.currentTime + 0.05)
    } catch {
      // Audio playback failsafe
    }
  }

  private tick = () => {
    const isAccented = this.state.currentBeat === 1
    this.playClick(isAccented)

    this.beatListeners.forEach((cb) => cb(this.state.currentBeat, isAccented))
    this.emitState()

    // Increment beat
    this.state.currentBeat =
      this.state.currentBeat >= this.state.beatsPerBar ? 1 : this.state.currentBeat + 1
  }

  public start() {
    if (this.state.isRunning) return
    this.initAudio()

    this.state.isRunning = true
    this.state.currentBeat = 1
    this.emitState()

    // Play first beat immediately
    this.tick()

    const intervalMs = (60 / this.state.bpm) * 1000
    this.timerId = window.setInterval(this.tick, intervalMs)
  }

  public stop() {
    if (this.timerId !== null) {
      clearInterval(this.timerId)
      this.timerId = null
    }
    this.state.isRunning = false
    this.state.currentBeat = 1
    this.emitState()
  }

  public toggle() {
    if (this.state.isRunning) {
      this.stop()
    } else {
      this.start()
    }
  }
}

export const metronome = new MetronomeEngine()
