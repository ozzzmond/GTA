package com.joel.gta.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
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

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncState = MutableStateFlow(BandSyncState())
    val syncState: StateFlow<BandSyncState> = _syncState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SyncMessage> = _incomingMessages.asSharedFlow()

    // Host Server references
    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private val activeClients = CopyOnWriteArrayList<ClientConnection>()

    // Client Socket references
    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val SERVICE_TYPE = "_gtasync._tcp."
    private val DEFAULT_PORT = 8765

    private data class ClientConnection(
        val socket: Socket,
        val writer: PrintWriter,
        var name: String = "Band Member"
    )

    /**
     * Starts Host Mode (Band Leader).
     * Binds ServerSocket and advertises via NSD.
     */
    fun startHost(port: Int = DEFAULT_PORT) {
        stopAll()
        _syncState.value = _syncState.value.copy(
            role = BandSyncRole.HOST,
            hostPort = port,
            statusMessage = "Starting Host on port $port..."
        )

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                registerNsdService(port)

                _syncState.value = _syncState.value.copy(
                    statusMessage = "Host active on port $port. Ready for band members.",
                    hostIp = getLocalIpAddress()
                )

                while (isActive && serverSocket?.isClosed == false) {
                    val socket = serverSocket?.accept() ?: break
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val conn = ClientConnection(socket, writer)
                    activeClients.add(conn)
                    updateHostClientCount()

                    // Handle messages from this client
                    launch {
                        handleClientMessages(conn)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    _syncState.value = _syncState.value.copy(
                        statusMessage = "Host error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private suspend fun handleClientMessages(conn: ClientConnection) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(conn.socket.getInputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { msgStr ->
                    val parsed = SyncMessage.deserialize(msgStr)
                    if (parsed is SyncMessage.ClientJoin) {
                        conn.name = parsed.clientName
                        updateHostClientCount()
                    } else if (parsed != null) {
                        _incomingMessages.emit(parsed)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            activeClients.remove(conn)
            runCatching { conn.socket.close() }
            updateHostClientCount()
        }
    }

    private fun updateHostClientCount() {
        _syncState.value = _syncState.value.copy(
            connectedClientsCount = activeClients.size,
            connectedClientNames = activeClients.map { it.name }
        )
    }

    /**
     * Broadcasts a SyncMessage to all connected band members.
     */
    fun broadcast(message: SyncMessage) {
        if (_syncState.value.role != BandSyncRole.HOST) return
        val serialized = SyncMessage.serialize(message)
        scope.launch {
            for (client in activeClients) {
                try {
                    client.writer.println(serialized)
                } catch (_: Exception) {
                    activeClients.remove(client)
                }
            }
        }
    }

    /**
     * Starts Client Mode (Band Member).
     * Discovers Host via NSD or connects directly if target specified.
     */
    fun startClient() {
        stopAll()
        _syncState.value = _syncState.value.copy(
            role = BandSyncRole.CLIENT,
            isConnectedToHost = false,
            statusMessage = "Searching for Band Leader..."
        )
        startNsdDiscovery()
    }

    /**
     * Connects directly to a Host by IP and port (e.g. Wi-Fi Hotspot or manual input).
     */
    fun connectToHost(hostIp: String, port: Int = DEFAULT_PORT) {
        stopClientConnection()
        _syncState.value = _syncState.value.copy(
            role = BandSyncRole.CLIENT,
            statusMessage = "Connecting to $hostIp:$port..."
        )

        scope.launch {
            try {
                val socket = Socket(hostIp, port)
                clientSocket = socket
                clientWriter = PrintWriter(socket.getOutputStream(), true)

                // Send Join notification
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                clientWriter?.println(SyncMessage.serialize(SyncMessage.ClientJoin(deviceName)))

                _syncState.value = _syncState.value.copy(
                    isConnectedToHost = true,
                    currentHostName = hostIp,
                    statusMessage = "Connected to Band Leader at $hostIp!"
                )

                // Read incoming broadcast messages from host
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String? = null
                while (isActive && reader.readLine().also { line = it } != null) {
                    line?.let { msgStr ->
                        val msg = SyncMessage.deserialize(msgStr)
                        if (msg != null) {
                            _incomingMessages.emit(msg)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    _syncState.value = _syncState.value.copy(
                        isConnectedToHost = false,
                        statusMessage = "Connection failed: ${e.localizedMessage}"
                    )
                }
            } finally {
                stopClientConnection()
            }
        }
    }

    /**
     * Stops all active server/client connections and NSD listeners.
     */
    fun stopAll() {
        stopHostServer()
        stopClientConnection()
        stopNsdDiscovery()
        _syncState.value = BandSyncState(role = BandSyncRole.OFF, statusMessage = "Band Sync inactive")
    }

    private fun stopHostServer() {
        unregisterNsdService()
        runCatching { serverSocket?.close() }
        serverSocket = null
        for (c in activeClients) {
            runCatching { c.socket.close() }
        }
        activeClients.clear()
    }

    private fun stopClientConnection() {
        runCatching { clientSocket?.close() }
        clientSocket = null
        clientWriter = null
    }

    // --- NSD Registration & Discovery ---

    private fun registerNsdService(port: Int) {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "GTA-Leader-${Build.MODEL}"
                serviceType = SERVICE_TYPE
                setPort(port)
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) {
                    _syncState.value = _syncState.value.copy(
                        statusMessage = "Host registered as ${info?.serviceName}"
                    )
                }

                override fun onRegistrationFailed(info: NsdServiceInfo?, errCode: Int) {
                    _syncState.value = _syncState.value.copy(
                        statusMessage = "Host NSD registration failed (code $errCode)"
                    )
                }

                override fun onServiceUnregistered(info: NsdServiceInfo?) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo?, errCode: Int) {}
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (_: Exception) {}
    }

    private fun unregisterNsdService() {
        registrationListener?.let {
            runCatching { nsdManager?.unregisterService(it) }
            registrationListener = null
        }
    }

    private fun startNsdDiscovery() {
        try {
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("gtasync")) {
                        nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val host = serviceInfo.host?.hostAddress ?: return
                                val port = serviceInfo.port
                                val name = serviceInfo.serviceName

                                val currentList = _syncState.value.discoveredHosts.toMutableList()
                                if (currentList.none { it.hostAddress == host && it.port == port }) {
                                    currentList.add(DiscoveredHost(name, host, port))
                                    _syncState.value = _syncState.value.copy(discoveredHosts = currentList)
                                }
                            }
                        })
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    val currentList = _syncState.value.discoveredHosts.filterNot { it.name == service.serviceName }
                    _syncState.value = _syncState.value.copy(discoveredHosts = currentList)
                }

                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }

            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (_: Exception) {}
    }

    private fun stopNsdDiscovery() {
        discoveryListener?.let {
            runCatching { nsdManager?.stopServiceDiscovery(it) }
            discoveryListener = null
        }
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
