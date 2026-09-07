package com.joel.gta.data.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.server.WebSocketServer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

enum class BandSyncRole {
    OFF,
    HOST,
    CLIENT
}

data class DiscoveredHost(
    val name: String,
    val hostAddress: String,
    val port: Int
)

data class BandSyncState(
    val role: BandSyncRole = BandSyncRole.OFF,
    val hostPort: Int = 8765,
    val hostIp: String? = null,
    val connectedClientsCount: Int = 0,
    val connectedClientNames: List<String> = emptyList(),
    val isConnectedToHost: Boolean = false,
    val currentHostName: String? = null,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val statusMessage: String = "Band Sync inactive"
) {
    val isHost: Boolean get() = role == BandSyncRole.HOST
    val isClient: Boolean get() = role == BandSyncRole.CLIENT
}

class BandSyncManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncState = MutableStateFlow(BandSyncState())
    val syncState: StateFlow<BandSyncState> = _syncState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SyncMessage> = _incomingMessages.asSharedFlow()

    // Host WebSocket Server references
    private var wsServer: GtarWsServer? = null
    private var udpBroadcastJob: Job? = null

    // Client WebSocket references
    private var wsClient: GtarWsClient? = null
    private var udpListenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val DEFAULT_PORT = 8765
    private val UDP_BEACON_PREFIX = "GTAR_LEADER:"

    private inner class GtarWsServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {
        private val clientMap = mutableMapOf<WebSocket, String>()

        override fun onStart() {
            _syncState.value = _syncState.value.copy(
                statusMessage = "Host active on WebSocket port $port. Ready for band members.",
                hostIp = getLocalIpAddress()
            )
        }

        override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
            if (conn != null) {
                clientMap[conn] = "Band Member"
                updateHostClients()
            }
        }

        override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
            if (conn != null) {
                clientMap.remove(conn)
                updateHostClients()
            }
        }

        override fun onMessage(conn: WebSocket?, message: String?) {
            if (message != null && conn != null) {
                val parsed = SyncMessage.deserialize(message)
                if (parsed is SyncMessage.ClientJoin) {
                    clientMap[conn] = parsed.clientName
                    updateHostClients()
                } else if (parsed != null) {
                    _incomingMessages.tryEmit(parsed)
                }
            }
        }

        override fun onError(conn: WebSocket?, ex: Exception?) {
            // Log or ignore non-fatal socket issues
        }

        private fun updateHostClients() {
            val names = clientMap.values.toList()
            _syncState.value = _syncState.value.copy(
                connectedClientsCount = names.size,
                connectedClientNames = names
            )
        }
    }

    private inner class GtarWsClient(serverUri: URI) : WebSocketClient(serverUri) {
        override fun onOpen(handshakedata: ServerHandshake?) {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val joinMsg = SyncMessage.serialize(SyncMessage.ClientJoin(deviceName))
            send(joinMsg)

            _syncState.value = _syncState.value.copy(
                isConnectedToHost = true,
                currentHostName = uri.host,
                statusMessage = "Connected to Band Leader at ${uri.host}!"
            )
        }

        override fun onMessage(message: String?) {
            if (message != null) {
                val parsed = SyncMessage.deserialize(message)
                if (parsed != null) {
                    _incomingMessages.tryEmit(parsed)
                }
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            _syncState.value = _syncState.value.copy(
                isConnectedToHost = false,
                statusMessage = "Disconnected from Band Leader."
            )
        }

        override fun onError(ex: Exception?) {
            _syncState.value = _syncState.value.copy(
                isConnectedToHost = false,
                statusMessage = "Connection error: ${ex?.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    /**
     * Starts Host Mode (Band Leader):
     * - Binds WebSocket server on port 8765
     * - Broadcasts periodic UDP beacon to 255.255.255.255:8765
     */
    fun startHost(port: Int = DEFAULT_PORT) {
        stopAll()
        val localIp = getLocalIpAddress()
        _syncState.value = _syncState.value.copy(
            role = BandSyncRole.HOST,
            hostPort = port,
            hostIp = localIp,
            statusMessage = "Starting Host WebSocket on port $port..."
        )

        scope.launch {
            try {
                val server = GtarWsServer(port)
                server.isReuseAddr = true
                server.start()
                wsServer = server

                // Start periodic UDP broadcast beacon
                startUdpBeacon(port)
            } catch (e: Exception) {
                _syncState.value = _syncState.value.copy(
                    statusMessage = "Host start failed: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun startUdpBeacon(port: Int) {
        udpBroadcastJob?.cancel()
        udpBroadcastJob = scope.launch(Dispatchers.IO) {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val beaconMessage = "$UDP_BEACON_PREFIX$deviceName:$port"
            val beaconBytes = beaconMessage.toByteArray(Charsets.UTF_8)

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(beaconBytes, beaconBytes.size, broadcastAddress, port)

                while (isActive) {
                    try {
                        socket.send(packet)
                    } catch (_: Exception) {
                    }
                    delay(1500)
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
            }
        }
    }

    /**
     * Broadcasts a SyncMessage to all connected band members via WebSocket.
     */
    fun broadcast(message: SyncMessage) {
        if (_syncState.value.role != BandSyncRole.HOST) return
        val serialized = SyncMessage.serialize(message)
        wsServer?.broadcast(serialized)
    }

    /**
     * Starts Client Mode (Band Member):
     * - Listens on UDP port 8765 for beacon announcements
     * - Displays active Leaders dynamically in Discovered Leaders list
     */
    fun startClient() {
        stopAll()
        _syncState.value = _syncState.value.copy(
            role = BandSyncRole.CLIENT,
            isConnectedToHost = false,
            discoveredHosts = emptyList(),
            statusMessage = "Searching for Band Leader via UDP..."
        )
        startUdpListener()
    }

    private fun startUdpListener(port: Int = DEFAULT_PORT) {
        udpListenJob?.cancel()

        // Acquire MulticastLock to ensure Wi-Fi radio receives UDP broadcasts
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("gtar_udp_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}

        udpListenJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        val senderIp = packet.address.hostAddress ?: continue

                        if (text.startsWith(UDP_BEACON_PREFIX)) {
                            val payload = text.removePrefix(UDP_BEACON_PREFIX)
                            val parts = payload.split(":")
                            val leaderName = if (parts.isNotEmpty()) parts[0] else "Stage Leader"
                            val leaderPort = if (parts.size > 1) parts[1].toIntOrNull() ?: port else port

                            val currentList = _syncState.value.discoveredHosts.toMutableList()
                            if (currentList.none { it.hostAddress == senderIp && it.port == leaderPort }) {
                                currentList.add(DiscoveredHost(leaderName, senderIp, leaderPort))
                                _syncState.value = _syncState.value.copy(
                                    discoveredHosts = currentList,
                                    statusMessage = "Found Leader: $leaderName ($senderIp)"
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    _syncState.value = _syncState.value.copy(
                        statusMessage = "UDP listener error: ${e.localizedMessage}"
                    )
                }
            } finally {
                socket?.close()
                releaseMulticastLock()
            }
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
            multicastLock = null
        } catch (_: Exception) {}
    }

    /**
     * Connects to a Stage Leader via WebSocket: ws://<hostIp>:<port>
     */
    fun connectToHost(hostIp: String, port: Int = DEFAULT_PORT) {
        stopClientConnection()
        _syncState.value = _syncState.value.copy(
            role = BandSyncRole.CLIENT,
            statusMessage = "Connecting to ws://$hostIp:$port..."
        )

        scope.launch {
            try {
                val uri = URI("ws://$hostIp:$port")
                val client = GtarWsClient(uri)
                wsClient = client
                client.connect()
            } catch (e: Exception) {
                _syncState.value = _syncState.value.copy(
                    isConnectedToHost = false,
                    statusMessage = "Failed to connect: ${e.localizedMessage}"
                )
            }
        }
    }

    /**
     * Stops all active server, client, UDP broadcasts, and listeners.
     */
    fun stopAll() {
        stopHostServer()
        stopClientConnection()
        udpListenJob?.cancel()
        udpListenJob = null
        releaseMulticastLock()
        _syncState.value = BandSyncState(role = BandSyncRole.OFF, statusMessage = "Band Sync inactive")
    }

    private fun stopHostServer() {
        udpBroadcastJob?.cancel()
        udpBroadcastJob = null
        try {
            wsServer?.stop()
        } catch (_: Exception) {}
        wsServer = null
    }

    private fun stopClientConnection() {
        try {
            wsClient?.close()
        } catch (_: Exception) {}
        wsClient = null
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
