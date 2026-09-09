/**
 * Stage Cast & Secondary Screen Projection Synchronization Engine
 * Mirrors stage state (song, key, transpose, scroll position, fonts, theme)
 * to secondary displays, Chromecasts, TVs, or external teleprompter popouts.
 */

import type { ActiveSongState } from '../types/gtar'
import { appLogger } from './logger'

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
  private presentationConnection: any | null = null
  private lastState: StageCastState | null = null
  private sessionListeners: Set<(isActive: boolean) => void> = new Set()
  constructor() {
    if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
      try {
        this.channel = new BroadcastChannel(CHANNEL_NAME)
      } catch (err) {
        console.warn('BroadcastChannel not supported in this environment:', err)
      }
    }
  }

  public isPresentationActive(): boolean {
    const isConnActive =
      this.presentationConnection != null &&
      (this.presentationConnection.state === 'connected' ||
        this.presentationConnection.state === 'connecting')

    const isPopupActive = this.popupWindow != null && !this.popupWindow.closed
    return Boolean(isConnActive || isPopupActive)
  }

  public subscribeSessionState(listener: (isActive: boolean) => void): () => void {
    this.sessionListeners.add(listener)
    listener(this.isPresentationActive())
    return () => {
      this.sessionListeners.delete(listener)
    }
  }

  private notifySessionChange() {
    const active = this.isPresentationActive()
    for (const listener of this.sessionListeners) {
      try {
        listener(active)
      } catch {}
    }
  }

  public setPopupWindow(win: Window | null) {
    this.popupWindow = win
    this.notifySessionChange()
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

    if (this.presentationConnection && this.presentationConnection.state === 'connected') {
      try {
        this.presentationConnection.send(JSON.stringify({ source: 'GTAR_CAST', message: msg }))
      } catch (err) {
        appLogger.warn('StageCast', `Failed to send state over PresentationConnection: ${err}`)
      }
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

    if (this.presentationConnection && this.presentationConnection.state === 'connected') {
      try {
        this.presentationConnection.send(JSON.stringify({ source: 'GTAR_CAST', message: msg }))
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

  public stopPresentation() {
    let hadSession = false

    if (this.presentationConnection) {
      hadSession = true
      const connId = this.presentationConnection.id || 'active'
      appLogger.info('StageCast', `Explicit session cleanup: Terminating active PresentationConnection (ID: ${connId})...`)
      try {
        if (typeof this.presentationConnection.terminate === 'function') {
          this.presentationConnection.terminate()
        } else if (typeof this.presentationConnection.close === 'function') {
          this.presentationConnection.close()
        }
      } catch (err) {
        appLogger.warn('StageCast', `Error terminating PresentationConnection: ${err}`)
      }
      this.presentationConnection = null
    }

    if (this.popupWindow && !this.popupWindow.closed) {
      hadSession = true
      appLogger.info('StageCast', 'Explicit session cleanup: Closing active external presentation pop-up window...')
      try {
        this.popupWindow.close()
      } catch (err) {
        appLogger.warn('StageCast', `Error closing pop-up window: ${err}`)
      }
      this.popupWindow = null
    }

    if (hadSession) {
      appLogger.info('StageCast', 'Stage Cast presentation session has been successfully stopped and disconnected.')
    }

    this.notifySessionChange()
  }

  public async openPresentationWindow(): Promise<Window | null> {
    if (typeof window === 'undefined') {
      appLogger.warn('StageCast', 'Cannot open presentation window: window is undefined (SSR environment)')
      return null
    }

    // 1. Clean up any existing active session before requesting a new one
    if (this.isPresentationActive()) {
      appLogger.info('StageCast', 'Active presentation session detected. Cleaning up and terminating previous session before starting new one...')
      this.stopPresentation()
    }

    // Always use same-origin URL for PresentationRequest (Chrome rejects cross-origin LAN IPs)
    const targetUrl = `${window.location.origin}/stage/present?view=present`
    const windowFeatures =
      'width=1280,height=720,menubar=no,toolbar=no,location=no,status=no,resizable=yes,scrollbars=no'

    appLogger.info('StageCast', `Initiating Stage Cast presentation request. Target URL: ${targetUrl}`)

    // 2. Try browser Presentation API if supported (Chromecast, Smart TV, Wireless Displays)
    if ('PresentationRequest' in window) {
      try {
        appLogger.info('StageCast', 'Browser Presentation API detected. Requesting presentation display...')
        const pr = new (window as any).PresentationRequest([targetUrl])
        pr.start()
          .then((conn: any) => {
            this.presentationConnection = conn
            appLogger.info(
              'StageCast',
              `PresentationConnection established on external display. Connection ID: ${conn?.id || 'active'}, State: ${conn?.state}`
            )
            this.notifySessionChange()

            // Wire connection lifecycle listeners
            conn.onconnect = () => {
              appLogger.info('StageCast', `PresentationConnection connected (ID: ${conn?.id})`)
              this.notifySessionChange()
            }
            conn.onclose = () => {
              appLogger.info('StageCast', `PresentationConnection closed (ID: ${conn?.id})`)
              this.presentationConnection = null
              this.notifySessionChange()
            }
            conn.onterminate = () => {
              appLogger.info('StageCast', `PresentationConnection terminated (ID: ${conn?.id})`)
              this.presentationConnection = null
              this.notifySessionChange()
            }
            conn.onmessage = (event: MessageEvent) => {
              try {
                const data = JSON.parse(event.data)
                if (data?.type === 'REQUEST_STATE' && this.lastState) {
                  this.broadcastState(this.lastState)
                }
              } catch {}
            }
          })
          .catch((err: any) => {
            if (err?.name === 'AbortError' || err?.name === 'NotAllowedError') {
              appLogger.info('StageCast', 'Presentation request cancelled by user (picker closed).')
              return
            }
            appLogger.warn(
              'StageCast',
              `Presentation API request failed (${err?.message || 'unknown'}). Triggering fallback pop-up window...`
            )
            this.openPopupWindow(targetUrl, windowFeatures)
          })
        return null
      } catch (presErr) {
        appLogger.warn(
          'StageCast',
          `PresentationRequest threw immediate exception (${presErr}), triggering fallback pop-up window.`
        )
        return this.openPopupWindow(targetUrl, windowFeatures)
      }
    }

    // 3. Fallback to standard window.open pop-up
    return this.openPopupWindow(targetUrl, windowFeatures)
  }

  private openPopupWindow(targetUrl: string, windowFeatures: string): Window | null {
    try {
      appLogger.info('StageCast', `Opening fallback presentation pop-up window: ${targetUrl}`)
      const win = window.open(targetUrl, 'gtar_stage_teleprompter', windowFeatures)
      if (win) {
        win.focus()
        this.setPopupWindow(win)
        this.monitorWindowLifecycle(win)
        this.notifySessionChange()
        appLogger.info('StageCast', 'External stage teleprompter pop-up window opened and focused successfully.')
        return win
      } else {
        appLogger.warn('StageCast', 'window.open returned null: The browser popup blocker may be blocking the external stage window.')
        this.notifySessionChange()
        return null
      }
    } catch (openErr) {
      appLogger.error('StageCast', 'Unexpected error opening presentation popup window', openErr as Error)
      this.notifySessionChange()
      return null
    }
  }

  private monitorWindowLifecycle(win: Window) {
    try {
      const checkTimer = setInterval(() => {
        try {
          if (win.closed) {
            clearInterval(checkTimer)
            this.setPopupWindow(null)
            appLogger.info('StageCast', 'External presentation pop-up window closed by user.')
            this.notifySessionChange()
          }
        } catch {
          clearInterval(checkTimer)
        }
      }, 1500)
    } catch {}
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
