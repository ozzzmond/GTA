/**
 * GTAR Band Sync Real-time Synchronization Engine (1:1 with Android BandSyncManager)
 * Enables real-time stage sync (active song, transpose key, scroll position, and autoscroll)
 * across multiple browser windows, tablets, laptops, and stage teleprompter screens.
 */

export type BandSyncRole = 'OFF' | 'HOST' | 'CLIENT'

export interface BandSyncMessage {
  type: 'SONG_SYNC' | 'SCROLL_SYNC' | 'AUTOSCROLL_SYNC' | 'SETLIST_SYNC' | 'HEARTBEAT'
  senderId: string
  role: BandSyncRole
  payload: any
  timestamp: number
}

export type ConnectionStatus = 'Disconnected' | 'Connecting...' | 'Connected to Leader (synced)'

export interface BandSyncState {
  role: BandSyncRole
  connectedPeers: number
  isLive: boolean
  lastMessage?: string
  wsConnected: boolean
  wsConnecting: boolean
  wsLeaderIp: string
  customHostIp: string
  wsStatus: ConnectionStatus
  roomId: string
  leaderEndpoint: string
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
  private wsPort: string = '8765'
  private wsConnected: boolean = false
  private wsConnecting: boolean = false
  private wsReconnectTimer: number | null = null
  private shouldReconnectWs: boolean = false

  private customHostIp: string = ''

  constructor() {
    this.initChannel()
    if (typeof window !== 'undefined') {
      this.wsLeaderIp = localStorage.getItem('gtar_band_sync_leader_ip') || ''
      this.customHostIp = localStorage.getItem('gtar_band_sync_custom_host_ip') || ''
    }
  }

  public setCustomHostIp(ip: string) {
    this.customHostIp = ip.trim()
    if (typeof window !== 'undefined') {
      localStorage.setItem('gtar_band_sync_custom_host_ip', this.customHostIp)
    }
    this.notify()
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

    let status: ConnectionStatus = 'Disconnected'
    if (this.wsConnected) {
      status = 'Connected to Leader (synced)'
    } else if (this.wsConnecting || this.shouldReconnectWs) {
      status = 'Connecting...'
    } else {
      status = 'Disconnected'
    }

    const host =
      this.customHostIp ||
      (typeof window !== 'undefined' && window.location.hostname
        ? window.location.hostname
        : '127.0.0.1')
    const leaderEndpoint =
      host === 'localhost' || host === '127.0.0.1'
        ? 'ws://127.0.0.1:8765'
        : `ws://${host}:8765`

    return {
      role: this.role,
      connectedPeers: this.role === 'HOST' ? activeCount : this.wsConnected ? 1 : 0,
      isLive: this.role !== 'OFF',
      wsConnected: this.wsConnected,
      wsConnecting: this.wsConnecting,
      wsLeaderIp: this.wsLeaderIp,
      customHostIp: this.customHostIp,
      wsStatus: status,
      roomId: 'gtar_stage_band_sync',
      leaderEndpoint,
    }
  }

  public connectWebSocket(leaderIp: string) {
    let clean = leaderIp.trim()
    if (!clean) return
    clean = clean.replace(/^(ws:\/\/|wss:\/\/|http:\/\/|https:\/\/)/i, '')
    let ipOnly = clean
    let port = '8765'
    if (clean.includes(':')) {
      const parts = clean.split(':')
      ipOnly = parts[0]
      port = parts[1] || '8765'
    }
    this.wsLeaderIp = ipOnly
    this.wsPort = port
    if (typeof window !== 'undefined') {
      localStorage.setItem('gtar_band_sync_leader_ip', ipOnly)
    }

    this.shouldReconnectWs = true
    this.wsConnecting = true
    this.wsConnected = false
    this.setRole('CLIENT')
    this.initWebSocketConnection()
  }

  public disconnectWebSocket() {
    this.shouldReconnectWs = false
    this.wsConnecting = false
    this.wsConnected = false
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

    const wsUrl = `ws://${this.wsLeaderIp}:${this.wsPort || '8765'}`
    this.wsConnecting = true
    this.notify()

    try {
      this.ws = new WebSocket(wsUrl)

      this.ws.onopen = () => {
        this.wsConnected = true
        this.wsConnecting = false
        if (typeof window !== 'undefined') {
          localStorage.setItem('gtar_band_sync_leader_ip', this.wsLeaderIp)
        }
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
        if (this.shouldReconnectWs) {
          this.wsConnecting = true
          this.scheduleWsReconnect()
        } else {
          this.wsConnecting = false
        }
        this.notify()
      }

      this.ws.onerror = () => {
        this.wsConnected = false
        this.notify()
      }
    } catch (err) {
      this.wsConnected = false
      if (this.shouldReconnectWs) {
        this.wsConnecting = true
        this.scheduleWsReconnect()
      } else {
        this.wsConnecting = false
      }
      this.notify()
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
      const qType = (data.queueType || (data.setlistIndex !== undefined ? 'SETLIST' : 'LIBRARY')).toUpperCase()
      const qIndex = data.queueIndex ?? data.setlistIndex ?? data.songIndex ?? 0
      const transpose = data.transpose ?? data.transposeOffset ?? data.offset ?? 0
      const scrollProgress = data.scrollProgress ?? data.scroll ?? 0
      const msg: BandSyncMessage = {
        type: 'SONG_SYNC',
        senderId: 'android_leader',
        role: 'HOST',
        payload: {
          title: data.title || '',
          artist: data.artist || '',
          queueType: qType,
          queueIndex: qIndex,
          songIndex: qIndex,
          songTitle: data.title || '',
          transposeOffset: transpose,
          transpose: transpose,
          scrollProgress: scrollProgress,
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
    } else if (data.type === 'SETLIST_SYNC') {
      const msg: BandSyncMessage = {
        type: 'SETLIST_SYNC',
        senderId: 'android_leader',
        role: 'HOST',
        payload: {
          setlistName: data.setlistName || 'Band Setlist',
          songs: Array.isArray(data.songs) ? data.songs : [],
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
  public broadcastSong(
    songIndex: number,
    songTitle: string,
    transposeOffset: number,
    extra?: {
      artist?: string
      queueType?: 'SETLIST' | 'LIBRARY'
      scrollProgress?: number
      rawContent?: string
      key?: string
      capo?: string
    }
  ) {
    if (this.role === 'HOST') {
      const qType = extra?.queueType || 'LIBRARY'
      const payload = {
        type: 'SONG_CHANGE',
        title: songTitle,
        artist: extra?.artist || '',
        queueType: qType,
        queueIndex: songIndex,
        songIndex,
        songTitle,
        transpose: transposeOffset,
        transposeOffset,
        scrollProgress: extra?.scrollProgress || 0,
        rawContent: extra?.rawContent || '',
        key: extra?.key || '',
        capo: extra?.capo || '',
      }
      this.broadcastMessage('SONG_SYNC', payload)
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        try {
          this.ws.send(
            JSON.stringify({
              type: 'SONG_CHANGE',
              title: songTitle,
              artist: extra?.artist || '',
              queueType: qType,
              queueIndex: songIndex,
              transpose: transposeOffset,
              scrollProgress: extra?.scrollProgress || 0,
              content: extra?.rawContent || '',
              key: extra?.key || '',
              capo: extra?.capo || '',
            })
          )
        } catch {
          // ignore
        }
      }
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

  public broadcastSetlist(setlistName: string, songs: any[]) {
    if (this.role === 'HOST') {
      this.broadcastMessage('SETLIST_SYNC', { setlistName, songs })
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
