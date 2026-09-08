/**
 * Stage Cast & Secondary Screen Projection Synchronization Engine
 * Mirrors stage state (song, key, transpose, scroll position, fonts, theme)
 * to secondary displays, Chromecasts, TVs, or external teleprompter popouts.
 */

import type { ActiveSongState } from '../types/gtar'

export interface StageCastState {
  song: ActiveSongState
  effectiveKey: string
  transposeOffset: number
  fontSizePx: number
  fontStyle: 'mono' | 'sans' | 'serif'
  isTwoColumn: boolean
  themeMode?: string
  customThemeColors?: {
    bgHex: string
    textHex: string
    chordHex: string
    sectionHex: string
  }
}

export type StageCastMessage =
  | { type: 'STATE_UPDATE'; payload: StageCastState }
  | { type: 'SCROLL_UPDATE'; payload: { scrollTop: number; scrollFraction: number } }
  | { type: 'REQUEST_STATE' }

const CHANNEL_NAME = 'gtar_stage_cast'
const STORAGE_KEY = 'gtar_stage_cast_state'

class StageCastEngine {
  private channel: BroadcastChannel | null = null
  private popupWindow: Window | null = null
  private lastState: StageCastState | null = null

  constructor() {
    if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
      try {
        this.channel = new BroadcastChannel(CHANNEL_NAME)
      } catch (err) {
        console.warn('BroadcastChannel not supported in this environment:', err)
      }
    }
  }

  public setPopupWindow(win: Window | null) {
    this.popupWindow = win
  }

  public getCachedState(): StageCastState | null {
    if (this.lastState) return this.lastState
    if (typeof window === 'undefined') return null
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        return JSON.parse(raw) as StageCastState
      }
    } catch {}
    return null
  }

  public broadcastState(state: StageCastState) {
    this.lastState = state
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    } catch {}

    const msg: StageCastMessage = { type: 'STATE_UPDATE', payload: state }

    if (this.channel) {
      try {
        this.channel.postMessage(msg)
      } catch (err) {
        console.error('Failed to post to stageCast channel:', err)
      }
    }

    if (this.popupWindow && !this.popupWindow.closed) {
      try {
        this.popupWindow.postMessage({ source: 'GTAR_CAST', message: msg }, '*')
      } catch {}
    }
  }

  public broadcastScroll(scrollTop: number, scrollFraction: number) {
    const msg: StageCastMessage = {
      type: 'SCROLL_UPDATE',
      payload: { scrollTop, scrollFraction },
    }

    if (this.channel) {
      try {
        this.channel.postMessage(msg)
      } catch {}
    }

    if (this.popupWindow && !this.popupWindow.closed) {
      try {
        this.popupWindow.postMessage({ source: 'GTAR_CAST', message: msg }, '*')
      } catch {}
    }
  }

  public requestState() {
    const msg: StageCastMessage = { type: 'REQUEST_STATE' }
    if (this.channel) {
      try {
        this.channel.postMessage(msg)
      } catch {}
    }
  }

  public openPresentationWindow(): Window | null {
    if (typeof window === 'undefined') return null

    const targetUrl = `${window.location.origin}/stage/present?view=present`
    const windowFeatures =
      'width=1280,height=720,menubar=no,toolbar=no,location=no,status=no,resizable=yes,scrollbars=no'

    // Try browser Presentation API if supported
    if ('PresentationRequest' in window) {
      try {
        const pr = new (window as any).PresentationRequest([targetUrl])
        pr.start()
          .then((conn: any) => {
            console.log('Browser presentation started on external display', conn)
          })
          .catch(() => {
            // User cancelled or presentation failed -> fallback to popup window
            const win = window.open(targetUrl, 'gtar_stage_teleprompter', windowFeatures)
            this.setPopupWindow(win)
          })
        return null
      } catch {
        // Fallback to popup
      }
    }

    const win = window.open(targetUrl, 'gtar_stage_teleprompter', windowFeatures)
    if (win) {
      win.focus()
      this.setPopupWindow(win)
    }
    return win
  }

  public subscribe(
    onState: (state: StageCastState) => void,
    onScroll: (scrollTop: number, scrollFraction: number) => void,
    onRequestState?: () => void
  ): () => void {
    const handleMessage = (msg: StageCastMessage) => {
      if (!msg || typeof msg !== 'object') return
      if (msg.type === 'STATE_UPDATE' && msg.payload) {
        onState(msg.payload)
      } else if (msg.type === 'SCROLL_UPDATE' && msg.payload) {
        onScroll(msg.payload.scrollTop, msg.payload.scrollFraction)
      } else if (msg.type === 'REQUEST_STATE' && onRequestState) {
        onRequestState()
      }
    }

    const channelListener = (e: MessageEvent) => {
      handleMessage(e.data)
    }

    const windowListener = (e: MessageEvent) => {
      if (e.data && e.data.source === 'GTAR_CAST' && e.data.message) {
        handleMessage(e.data.message)
      }
    }

    if (this.channel) {
      this.channel.addEventListener('message', channelListener)
    }
    window.addEventListener('message', windowListener)

    return () => {
      if (this.channel) {
        this.channel.removeEventListener('message', channelListener)
      }
      window.removeEventListener('message', windowListener)
    }
  }
}

export const stageCast = new StageCastEngine()
