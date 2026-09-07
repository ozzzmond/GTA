/**
 * GTAR Band Sync Real-time Synchronization Engine (1:1 with Android BandSyncManager)
 * Enables real-time stage sync (active song, transpose key, scroll position, and autoscroll)
 * across multiple browser windows, tablets, laptops, and stage teleprompter screens.
 */

export type BandSyncRole = 'OFF' | 'HOST' | 'CLIENT'

export interface BandSyncMessage {
  type: 'SONG_SYNC' | 'SCROLL_SYNC' | 'AUTOSCROLL_SYNC' | 'HEARTBEAT'
  senderId: string
  role: BandSyncRole
  payload: any
  timestamp: number
}

export interface BandSyncState {
  role: BandSyncRole
  connectedPeers: number
  isLive: boolean
  lastMessage?: string
  wsConnected?: boolean
  wsLeaderIp?: string
  wsStatus?: string
}

type SyncListener = (state: BandSyncState) => void
type MessageHandler = (msg: BandSyncMessage) => void

class BandSyncEngine {
  private channel: BroadcastChannel | null = null
  private role: BandSyncRole = 'OFF'
  private peerId: string = Math.random().toString(36).substring(2, 9)
  private listeners: Set<SyncListener> = new Set()
  private messageHandlers: Set<MessageHandler> = new Set()
  private knownPeers: Map<string, number> = new Map()
  private heartbeatInterval: number | null = null
  private pruneInterval: number | null = null

  // Live WebSocket Connection to Android Stage Leader (port 8765)
  private ws: WebSocket | null = null
  private wsLeaderIp: string = ''
  private wsConnected: boolean = false
  private wsReconnectTimer: number | null = null
  private shouldReconnectWs: boolean = false

  constructor() {
    this.initChannel()
    if (typeof window !== 'undefined') {
      this.wsLeaderIp = localStorage.getItem('gtar_band_sync_leader_ip') || ''
    }
  }

  private initChannel() {
    if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
      try {
        this.channel = new BroadcastChannel('gtar_stage_band_sync')
        this.channel.onmessage = (event) => {
          this.handleIncoming(event.data)
        }
      } catch (err) {
        console.warn('BroadcastChannel not supported, falling back to storage sync', err)
      }
    }

    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (e) => {
        if (e.key === 'gtar_storage_sync_event' && e.newValue) {
          try {
            const parsed = JSON.parse(e.newValue)
            this.handleIncoming(parsed)
          } catch {
            // ignore
          }
        }
      })
    }
  }

  public getRole(): BandSyncRole {
    return this.role
  }

  public getState(): BandSyncState {
    const now = Date.now()
    let activeCount = 0
    this.knownPeers.forEach((lastSeen, id) => {
      if (id !== this.peerId && now - lastSeen < 4500) {
        activeCount++
      }
    })

    return {
      role: this.role,
      connectedPeers: activeCount,
      isLive: this.role !== 'OFF',
      wsConnected: this.wsConnected,
      wsLeaderIp: this.wsLeaderIp,
      wsStatus: this.wsConnected
        ? `Connected to Android Leader (ws://${this.wsLeaderIp}:8765)`
        : this.shouldReconnectWs
        ? `Connecting to ws://${this.wsLeaderIp}:8765...`
        : 'WebSocket disconnected',
    }
  }

  public connectWebSocket(leaderIp: string) {
    const cleanIp = leaderIp.trim()
    if (!cleanIp) return
    this.wsLeaderIp = cleanIp
    if (typeof window !== 'undefined') {
      localStorage.setItem('gtar_band_sync_leader_ip', cleanIp)
    }

    this.shouldReconnectWs = true
    this.setRole('CLIENT')
    this.initWebSocketConnection()
  }

  public disconnectWebSocket() {
    this.shouldReconnectWs = false
    if (this.wsReconnectTimer) {
      clearTimeout(this.wsReconnectTimer)
      this.wsReconnectTimer = null
    }
    if (this.ws) {
      try {
        this.ws.close()
      } catch {}
      this.ws = null
    }
    this.wsConnected = false
    this.notify()
  }

  private initWebSocketConnection() {
    if (!this.shouldReconnectWs || !this.wsLeaderIp) return

    if (this.ws) {
      try {
        this.ws.close()
      } catch {}
      this.ws = null
    }

    const wsUrl = `ws://${this.wsLeaderIp}:8765`
    try {
      this.ws = new WebSocket(wsUrl)

      this.ws.onopen = () => {
        this.wsConnected = true
        // Send join identifier
        const joinMsg = JSON.stringify({ type: 'JOIN', name: 'Web Member' })
        this.ws?.send(joinMsg)
        this.notify()
      }

      this.ws.onmessage = (event) => {
        try {
          const raw = typeof event.data === 'string' ? event.data : ''
          if (!raw) return
          const data = JSON.parse(raw)
          this.handleWebSocketMessage(data)
        } catch (e) {
          console.warn('Failed to parse WebSocket message:', e)
        }
      }

      this.ws.onclose = () => {
        this.wsConnected = false
        this.notify()
        if (this.shouldReconnectWs) {
          this.scheduleWsReconnect()
        }
      }

      this.ws.onerror = () => {
        this.wsConnected = false
        this.notify()
      }
    } catch (err) {
      this.wsConnected = false
      this.notify()
      if (this.shouldReconnectWs) {
        this.scheduleWsReconnect()
      }
    }
  }

  private scheduleWsReconnect() {
    if (this.wsReconnectTimer) {
      clearTimeout(this.wsReconnectTimer)
    }
    this.wsReconnectTimer = window.setTimeout(() => {
      if (this.shouldReconnectWs) {
        this.initWebSocketConnection()
      }
    }, 2500)
  }

  private handleWebSocketMessage(data: any) {
    if (!data || typeof data !== 'object') return

    // Convert Android SyncMessage JSON to web BandSyncMessage
    if (data.type === 'SONG' || data.type === 'SONG_CHANGE') {
      const msg: BandSyncMessage = {
        type: 'SONG_SYNC',
        senderId: 'android_leader',
        role: 'HOST',
        payload: {
          songIndex: data.setlistIndex ?? 0,
          songTitle: data.title || '',
          transposeOffset: data.transposeOffset || 0,
          rawContent: data.content || data.rawContent || '',
          key: data.key || '',
          capo: data.capo || '',
        },
        timestamp: Date.now(),
      }
      this.dispatchSyncMessage(msg)
    } else if (data.type === 'SCROLL') {
      const msg: BandSyncMessage = {
        type: 'SCROLL_SYNC',
        senderId: 'android_leader',
        role: 'HOST',
        payload: {
          scrollFraction: data.scroll || 0,
          scrollTop: 0,
        },
        timestamp: Date.now(),
      }
      this.dispatchSyncMessage(msg)
    } else if (data.type === 'TRANSPOSE') {
      const msg: BandSyncMessage = {
        type: 'SONG_SYNC',
        senderId: 'android_leader',
        role: 'HOST',
        payload: {
          transposeOffset: data.transposeOffset ?? data.offset ?? 0,
        },
        timestamp: Date.now(),
      }
      this.dispatchSyncMessage(msg)
    }
  }

  private dispatchSyncMessage(msg: BandSyncMessage) {
    this.messageHandlers.forEach((handler) => {
      try {
        handler(msg)
      } catch (err) {
        console.error('BandSync handler error:', err)
      }
    })
  }

  public subscribe(listener: SyncListener): () => void {
    this.listeners.add(listener)
    listener(this.getState())
    return () => this.listeners.delete(listener)
  }

  public onMessage(handler: MessageHandler): () => void {
    this.messageHandlers.add(handler)
    return () => this.messageHandlers.delete(handler)
  }

  private notify() {
    const st = this.getState()
    this.listeners.forEach((l) => l(st))
  }

  private startHeartbeat() {
    this.stopHeartbeat()
    this.broadcastMessage('HEARTBEAT', { role: this.role })
    this.heartbeatInterval = window.setInterval(() => {
      this.broadcastMessage('HEARTBEAT', { role: this.role })
    }, 1800)

    this.pruneInterval = window.setInterval(() => {
      this.notify()
    }, 2000)
  }

  private stopHeartbeat() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval)
      this.heartbeatInterval = null
    }
    if (this.pruneInterval) {
      clearInterval(this.pruneInterval)
      this.pruneInterval = null
    }
  }

  public setRole(newRole: BandSyncRole) {
    this.role = newRole
    if (newRole !== 'OFF') {
      this.startHeartbeat()
    } else {
      this.stopHeartbeat()
      this.disconnectWebSocket()
      this.broadcastMessage('HEARTBEAT', { role: 'OFF', disconnecting: true })
    }
    this.notify()
  }

  public broadcastMessage(type: BandSyncMessage['type'], payload: any) {
    if (this.role === 'OFF') return

    const msg: BandSyncMessage = {
      type,
      senderId: this.peerId,
      role: this.role,
      payload,
      timestamp: Date.now(),
    }

    try {
      this.channel?.postMessage(msg)
    } catch {
      // ignore
    }

    try {
      localStorage.setItem('gtar_storage_sync_event', JSON.stringify(msg))
    } catch {
      // ignore
    }
  }

  // Specialized broadcast actions (used by Band Leader)
  public broadcastSong(songIndex: number, songTitle: string, transposeOffset: number) {
    if (this.role === 'HOST') {
      this.broadcastMessage('SONG_SYNC', { songIndex, songTitle, transposeOffset })
    }
  }

  public broadcastScroll(scrollFraction: number, scrollTop: number) {
    if (this.role === 'HOST') {
      this.broadcastMessage('SCROLL_SYNC', { scrollFraction, scrollTop })
    }
  }

  public broadcastAutoScroll(isAutoScrolling: boolean, scrollSpeed: number) {
    if (this.role === 'HOST') {
      this.broadcastMessage('AUTOSCROLL_SYNC', { isAutoScrolling, scrollSpeed })
    }
  }

  private handleIncoming(data: any) {
    if (!data || typeof data !== 'object' || !data.senderId) return
    if (data.senderId === this.peerId) return // Ignore self-messages

    const msg = data as BandSyncMessage
    this.knownPeers.set(msg.senderId, Date.now())

    if (msg.type === 'HEARTBEAT' && msg.payload?.disconnecting) {
      this.knownPeers.delete(msg.senderId)
    }

    this.notify()

    // Pass to listeners
    this.messageHandlers.forEach((handler) => {
      try {
        handler(msg)
      } catch (err) {
        console.error('BandSync handler error:', err)
      }
    })
  }
}

export const bandSync = new BandSyncEngine()
