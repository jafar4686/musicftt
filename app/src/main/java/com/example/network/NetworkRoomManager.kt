package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.model.RoomMember
import com.example.model.RoomPhase
import com.example.model.RoomRole
import com.example.model.RoomSyncCommand
import com.example.model.SyncRoomState
import com.example.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class NetworkRoomManager(
    private val context: Context,
    private val onGuestSyncCommand: (RoomSyncCommand) -> Unit = {},
    private val onGuestRoomStarted: () -> Unit = {},
    private val onRoomEnded: (String) -> Unit = {},
    private val onFindLocalFileByHash: (fileHash: String, fileSize: Long, title: String?) -> String? = { _, _, _ -> null }
) {

    companion object {
        private const val TAG = "NetworkRoomManager"
        val RoomProtocol: com.example.network.RoomProtocol = com.example.network.RoomProtocol
    }

    private data class ClientSession(
        val member: RoomMember,
        val socket: Socket,
        val writer: BufferedWriter
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localMemberId = "dev_${Build.MODEL.hashCode()}_${Random.nextInt(100, 999)}"
    private val syncCacheDir = File(appContext.filesDir, "room_sync_cache").apply { mkdirs() }

    private val _roomState = MutableStateFlow(SyncRoomState())
    val roomState: StateFlow<SyncRoomState> = _roomState.asStateFlow()

    private var controlServerSocket: ServerSocket? = null
    private var controlServerPort: Int = RoomProtocol.DEFAULT_CONTROL_PORT
    private var httpServerSocket: ServerSocket? = null
    private var httpServerPort: Int = 0
    private var udpResponderSocket: DatagramSocket? = null

    private var hostAcceptJob: Job? = null
    private var httpAcceptJob: Job? = null
    private var udpResponderJob: Job? = null
    private var hostBeaconJob: Job? = null
    private var hostPingJob: Job? = null
    private var prepareTrackJob: Job? = null

    private val transferSeq = AtomicLong(0L)
    private val hostClients = ConcurrentHashMap<String, ClientSession>()
    private val servedPayloadsByHash = ConcurrentHashMap<String, TrackAudioPayload>()
    private val clientReadySignals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    @Volatile private var activeTransferId: String? = null
    private var lastSyncedPlayCommand: RoomSyncCommand? = null
    private var lastPreparedTrack: Track? = null
    private var lastPreparedPayload: TrackAudioPayload? = null

    private var guestSocket: Socket? = null
    private var guestWriter: BufferedWriter? = null
    private var guestJoinJob: Job? = null
    private var guestDownloadJob: Job? = null
    private val syncedFilePaths = ConcurrentHashMap<String, String>()

    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        refreshNetworkStatus()
    }

    fun refreshNetworkStatus() {
        val localIps = getLocalIpv4Candidates()
        val hasLocalIp = localIps.isNotEmpty()
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = try {
            cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        } catch (_: Exception) {
            null
        }
        val isWifiOrEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val label = when {
            isWifiOrEthernet -> "Wi-Fi Connected"
            hasLocalIp -> "Hotspot / Local LAN"
            else -> "No Wi-Fi / Hotspot"
        }

        _roomState.update {
            it.copy(
                isNetworkReady = hasLocalIp || isWifiOrEthernet,
                hostIpAddress = localIps.firstOrNull(),
                networkLabel = label
            )
        }
    }

    private fun getLocalDeviceDisplayName(): String {
        val model = Build.MODEL.orEmpty().trim()
        val mfg = Build.MANUFACTURER.orEmpty().trim()
        return when {
            model.isBlank() -> "Android Device"
            mfg.isNotBlank() && !model.startsWith(mfg, ignoreCase = true) -> "$mfg $model"
            else -> model
        }
    }

    private fun acquireMulticastLockSafely() {
        try {
            if (multicastLock == null) {
                val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wm?.createMulticastLock("ASDMusicRoomLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MulticastLock optional skip: ${e.message}")
        }
    }

    private fun releaseMulticastLockSafely() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }

    fun createRoom(): String? {
        refreshNetworkStatus()
        closeInternalSockets(resetState = false)

        val code = RoomProtocol.generateFiveDigitCode()
        val primaryIp = getLocalIpv4Candidates().firstOrNull()
        val hostMember = RoomMember(
            id = localMemberId,
            name = "${getLocalDeviceDisplayName()} (Host)",
            isHost = true,
            address = primaryIp
        )

        _roomState.update {
            it.copy(
                role = RoomRole.HOST,
                phase = RoomPhase.LOBBY,
                roomCode = code,
                members = listOf(hostMember),
                hostIpAddress = primaryIp,
                isConnecting = false,
                isDiscovering = false,
                isTransferringTrack = false,
                transferProgress = 0f,
                transferTrackTitle = null,
                statusMessage = "Starting room $code on network...",
                errorMessage = null,
                currentSyncedTrackTitle = null
            )
        }

        val serversStarted = startHostServers(code, hostMember)
        if (!serversStarted) {
            closeInternalSockets(resetState = true)
            _roomState.update {
                it.copy(errorMessage = "Could not open network room server. Check Wi-Fi or Hotspot connection.")
            }
            return null
        }

        _roomState.update {
            it.copy(
                hostIpAddress = primaryIp ?: getLocalIpv4Candidates().firstOrNull(),
                statusMessage = "Room $code ready on network! Friends on the same Wi-Fi/Hotspot can enter $code and tap Join."
            )
        }
        return code
    }

    private fun startHostServers(roomCode: String, hostMember: RoomMember): Boolean {
        return try {
            acquireMulticastLockSafely()

            // 1. Open TCP Control Server (prefer deterministic port 45892 so subnet probe finds it even if UDP is blocked)
            val tcpServer = try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(RoomProtocol.DEFAULT_CONTROL_PORT))
                }
            } catch (_: Exception) {
                ServerSocket(0)
            }
            controlServerSocket = tcpServer
            controlServerPort = tcpServer.localPort

            // 2. Open HTTP Audio Streaming Server (ephemeral port)
            val httpServer = ServerSocket(0)
            httpServerSocket = httpServer
            httpServerPort = httpServer.localPort

            startControlAcceptLoop(tcpServer, roomCode, hostMember)
            startHttpServerLoop(httpServer)
            startUdpResponderAndBeacon(roomCode, controlServerPort)
            registerNsdService(roomCode, controlServerPort)
            startHostPingLoop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start host servers: ${e.message}")
            false
        }
    }

    private fun startControlAcceptLoop(
        server: ServerSocket,
        roomCode: String,
        hostMember: RoomMember
    ) {
        hostAcceptJob = scope.launch {
            while (isActive && _roomState.value.role == RoomRole.HOST) {
                val clientSocket = try {
                    server.accept()
                } catch (_: Exception) {
                    null
                } ?: break

                launch {
                    handleIncomingGuestConnection(clientSocket, roomCode, hostMember)
                }
            }
        }
    }

    private suspend fun handleIncomingGuestConnection(
        socket: Socket,
        expectedCode: String,
        hostMember: RoomMember
    ) {
        var assignedClientId: String? = null
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8), 65536)
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), 65536)

            val handshakeLine = withTimeoutOrNull(6_000L) {
                reader.readLine()
            }
            if (handshakeLine.isNullOrBlank()) {
                socket.close()
                return
            }

            val json = JSONObject(handshakeLine)
            if (json.optString("type") != "JOIN") {
                sendLineBlocking(writer, RoomProtocol.encodeReject("Invalid join request"))
                socket.close()
                return
            }

            val enteredCode = json.optString("code").trim()
            if (enteredCode != expectedCode) {
                sendLineBlocking(writer, RoomProtocol.encodeReject("Wrong 5-digit room code"))
                socket.close()
                return
            }

            val guestName = json.optString("name").ifBlank { "Joined Device" }
            val guestId = json.optString("id").ifBlank { "guest_${System.currentTimeMillis()}" }
            val guestIp = socket.inetAddress?.hostAddress

            val newMember = RoomMember(
                id = guestId,
                name = guestName,
                isHost = false,
                address = guestIp
            )
            assignedClientId = guestId

            val session = ClientSession(newMember, socket, writer)
            hostClients[guestId] = session

            val updatedMembers = buildCurrentHostMemberList(hostMember)
            _roomState.update {
                it.copy(
                    members = updatedMembers,
                    statusMessage = "$guestName joined Room $expectedCode (${updatedMembers.size} in room)"
                )
            }

            val currentPhase = _roomState.value.phase
            sendLineBlocking(writer, RoomProtocol.encodeWelcome(expectedCode, currentPhase, updatedMembers))
            broadcastLineToGuests(RoomProtocol.encodeMembers(updatedMembers), excludeId = guestId)

            // If room is already PLAYING, transfer & sync current song to the late joiner
            if (currentPhase == RoomPhase.PLAYING) {
                val track = lastPreparedTrack
                val cmd = lastSyncedPlayCommand
                if (track != null && cmd != null) {
                    scope.launch {
                        syncLateJoinerTrack(session, track, lastPreparedPayload, cmd)
                    }
                }
            }

            while (scope.isActive && _roomState.value.role == RoomRole.HOST) {
                val line = reader.readLine() ?: break
                handleHostReceivedFromGuest(guestId, line)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Guest TCP session ended: ${e.message}")
        } finally {
            if (assignedClientId != null) {
                hostClients.remove(assignedClientId)
                clientReadySignals.remove(assignedClientId)?.complete(false)
                if (_roomState.value.role == RoomRole.HOST) {
                    val remaining = buildCurrentHostMemberList(hostMember)
                    _roomState.update {
                        it.copy(
                            members = remaining,
                            statusMessage = "${remaining.size} in Room"
                        )
                    }
                    broadcastLineToGuests(RoomProtocol.encodeMembers(remaining))
                }
            }
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun handleHostReceivedFromGuest(guestId: String, line: String) {
        try {
            val json = JSONObject(line)
            val txId = json.optString("transferId")
            when (json.optString("type")) {
                "TRACK_READY" -> {
                    if (txId == activeTransferId) {
                        clientReadySignals[guestId]?.complete(true)
                    } else {
                        clientReadySignals["$txId:$guestId"]?.complete(true)
                    }
                }
                "TRACK_FAILED" -> {
                    if (txId == activeTransferId) {
                        clientReadySignals[guestId]?.complete(false)
                    } else {
                        clientReadySignals["$txId:$guestId"]?.complete(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Host parse error: ${e.message}")
        }
    }

    private suspend fun syncLateJoinerTrack(
        client: ClientSession,
        track: Track,
        payload: TrackAudioPayload?,
        cmd: RoomSyncCommand
    ) {
        val lateTxId = "late_${System.currentTimeMillis()}"
        val key = "$lateTxId:${client.member.id}"
        val signal = CompletableDeferred<Boolean>()
        clientReadySignals[key] = signal

        try {
            val downloadUrl = if (payload != null && httpServerPort > 0) {
                servedPayloadsByHash[payload.fileHash] = payload
                buildHttpAudioUrl(client.socket.localAddress, payload.fileHash)
            } else null

            sendLineBlocking(
                client.writer,
                RoomProtocol.encodePrepareTrack(
                    transferId = lateTxId,
                    track = track,
                    fileHash = payload?.fileHash,
                    fileSize = payload?.fileSize ?: 0L,
                    fileExt = payload?.fileExt,
                    hasBinary = payload != null,
                    audioDownloadUrl = downloadUrl
                )
            )
            withTimeoutOrNull(30_000L) { signal.await() }
            sendLineBlocking(
                client.writer,
                RoomProtocol.encodeSyncPlay(
                    track = track,
                    positionMs = cmd.positionMs,
                    speed = cmd.speed,
                    isPlaying = cmd.isPlaying,
                    syncDelayMs = 120L,
                    fileHash = payload?.fileHash,
                    fileSize = payload?.fileSize ?: 0L,
                    fileExt = payload?.fileExt
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Late joiner sync error: ${e.message}")
        } finally {
            clientReadySignals.remove(key)
        }
    }

    private fun startHttpServerLoop(httpServer: ServerSocket) {
        httpAcceptJob = scope.launch {
            while (isActive && _roomState.value.role == RoomRole.HOST) {
                val reqSocket = try {
                    httpServer.accept()
                } catch (_: Exception) {
                    null
                } ?: break

                launch {
                    handleHttpAudioRequest(reqSocket)
                }
            }
        }
    }

    private fun handleHttpAudioRequest(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            // Consume headers
            while (true) {
                val h = reader.readLine() ?: break
                if (h.isBlank()) break
            }

            val parts = requestLine.split(" ")
            val path = parts.getOrNull(1) ?: ""
            val out = BufferedOutputStream(socket.outputStream, 32768)

            if (!path.startsWith("/audio/")) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                return
            }

            val requestedHash = path.removePrefix("/audio/").substringBefore("?").substringBefore(".")
            val payload = servedPayloadsByHash[requestedHash] ?: lastPreparedPayload

            if (payload == null) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                return
            }

            val headers = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: ${payload.mimeType}\r\n")
                append("Content-Length: ${payload.fileSize}\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(headers.toByteArray(Charsets.US_ASCII))

            val stream = payload.openStream()
            if (stream != null) {
                stream.use { input ->
                    val buf = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                    }
                }
            }
            out.flush()
        } catch (e: Exception) {
            Log.d(TAG, "HTTP audio stream closed: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun startUdpResponderAndBeacon(roomCode: String, tcpPort: Int) {
        udpResponderJob = scope.launch {
            try {
                val udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(RoomProtocol.DEFAULT_UDP_PORT))
                }
                udpResponderSocket = udpSocket
                val buf = ByteArray(1024)
                val expectedProbe = "DISCOVER_ASD_ROOM:$roomCode"

                while (isActive && _roomState.value.role == RoomRole.HOST) {
                    val packet = DatagramPacket(buf, buf.size)
                    udpSocket.receive(packet)
                    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (msg == expectedProbe || msg == "DISCOVER_ASD_ROOM:*") {
                        val replyBytes = "ASD_ROOM_HERE:$roomCode:$tcpPort".toByteArray(Charsets.UTF_8)
                        val replyPacket = DatagramPacket(replyBytes, replyBytes.size, packet.address, packet.port)
                        udpSocket.send(replyPacket)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "UDP responder stopped: ${e.message}")
            }
        }

        hostBeaconJob = scope.launch {
            val beaconSocket = try {
                DatagramSocket().apply { broadcast = true }
            } catch (_: Exception) {
                null
            } ?: return@launch

            try {
                val beaconBytes = "ASD_ROOM_BEACON:$roomCode:$tcpPort".toByteArray(Charsets.UTF_8)
                while (isActive && _roomState.value.role == RoomRole.HOST) {
                    getBroadcastTargets().forEach { addr ->
                        try {
                            val pkt = DatagramPacket(beaconBytes, beaconBytes.size, addr, RoomProtocol.DEFAULT_UDP_PORT)
                            beaconSocket.send(pkt)
                        } catch (_: Exception) {}
                    }
                    delay(1500L)
                }
            } finally {
                try {
                    beaconSocket.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun startHostPingLoop() {
        hostPingJob = scope.launch {
            while (isActive && _roomState.value.role == RoomRole.HOST) {
                delay(5000L)
                if (hostClients.isNotEmpty()) {
                    broadcastLineToGuests(RoomProtocol.encodePing())
                }
            }
        }
    }

    private fun registerNsdService(roomCode: String, tcpPort: Int) {
        try {
            val mgr = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "ASDMusic_$roomCode"
                serviceType = RoomProtocol.NSD_SERVICE_TYPE
                port = tcpPort
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.d(TAG, "NSD registered: ${info.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            nsdRegistrationListener = listener
            mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD register skipped: ${e.message}")
        }
    }

    private fun buildCurrentHostMemberList(hostMember: RoomMember): List<RoomMember> {
        return listOf(hostMember) + hostClients.values.map { it.member }
    }

    private fun sendLineBlocking(writer: BufferedWriter, line: String) {
        synchronized(writer) {
            writer.write(line)
            writer.newLine()
            writer.flush()
        }
    }

    private fun broadcastLineToGuests(line: String, excludeId: String? = null) {
        scope.launch {
            hostClients.forEach { (id, client) ->
                if (id != excludeId) {
                    try {
                        sendLineBlocking(client.writer, line)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing to ${client.member.name}: ${e.message}")
                    }
                }
            }
        }
    }

    fun startRoom() {
        val state = _roomState.value
        if (state.role != RoomRole.HOST) return
        _roomState.update {
            it.copy(
                phase = RoomPhase.PLAYING,
                statusMessage = "Room started! Pick a song to play on all devices simultaneously.",
                errorMessage = null
            )
        }
        broadcastLineToGuests(RoomProtocol.encodeStartRoom())
    }

    fun prepareAndPlayTrack(
        track: Track,
        positionMs: Long = 0L,
        speed: Float = 1.0f,
        onReadyToPlayLocal: (syncDelayMs: Long) -> Unit
    ) {
        val seq = transferSeq.incrementAndGet()
        prepareTrackJob?.cancel()

        val state = _roomState.value
        if (state.role != RoomRole.HOST || state.phase != RoomPhase.PLAYING) {
            onReadyToPlayLocal(0L)
            return
        }

        val snapshotClients = hostClients.values.toList()
        if (snapshotClients.isEmpty()) {
            lastPreparedTrack = track
            lastPreparedPayload = null
            lastSyncedPlayCommand = RoomSyncCommand(
                action = "SYNC_PLAY",
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = track.durationSeconds,
                sourceUrl = track.sourceUrl,
                positionMs = positionMs,
                speed = speed,
                isPlaying = true
            )
            scope.launch(Dispatchers.Main) {
                onReadyToPlayLocal(0L)
            }
            return
        }

        prepareTrackJob = scope.launch {
            val payload = RoomProtocol.resolveTrackAudioPayload(appContext, track)
            if (!isActive || transferSeq.get() != seq) return@launch

            lastPreparedTrack = track
            lastPreparedPayload = payload
            if (payload != null) {
                servedPayloadsByHash[payload.fileHash] = payload
            }

            val txId = "tx_${seq}_${System.currentTimeMillis()}"
            activeTransferId = txId
            clientReadySignals.clear()

            _roomState.update {
                it.copy(
                    isTransferringTrack = true,
                    transferProgress = 0.2f,
                    transferTrackTitle = track.title,
                    statusMessage = "Syncing '${track.title}' to ${snapshotClients.size} device(s)..."
                )
            }

            val fallbackHostIp = getLocalIpv4Candidates().firstOrNull()
            snapshotClients.forEach { client ->
                val cid = client.member.id
                clientReadySignals[cid] = CompletableDeferred()
                val downloadUrl = if (payload != null && httpServerPort > 0) {
                    buildHttpAudioUrl(client.socket.localAddress, payload.fileHash, fallbackHostIp)
                } else null

                val prepMsg = RoomProtocol.encodePrepareTrack(
                    transferId = txId,
                    track = track,
                    fileHash = payload?.fileHash,
                    fileSize = payload?.fileSize ?: 0L,
                    fileExt = payload?.fileExt,
                    hasBinary = payload != null,
                    audioDownloadUrl = downloadUrl
                )
                try {
                    sendLineBlocking(client.writer, prepMsg)
                } catch (_: Exception) {
                    clientReadySignals[cid]?.complete(false)
                }
            }

            // Wait for every connected device to finish downloading/verifying the audio file over LAN HTTP
            snapshotClients.forEachIndexed { idx, client ->
                if (!isActive || transferSeq.get() != seq) return@launch
                withTimeoutOrNull(35_000L) {
                    clientReadySignals[client.member.id]?.await()
                }
                val frac = ((idx + 1).toFloat() / snapshotClients.size.toFloat()).coerceIn(0.2f, 0.98f)
                _roomState.update { it.copy(transferProgress = frac) }
            }

            if (!isActive || transferSeq.get() != seq) return@launch

            val syncDelay = 180L
            lastSyncedPlayCommand = RoomSyncCommand(
                action = "SYNC_PLAY",
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = track.durationSeconds,
                sourceUrl = track.sourceUrl,
                positionMs = positionMs,
                speed = speed,
                isPlaying = true,
                syncDelayMs = syncDelay,
                fileHash = payload?.fileHash,
                fileSize = payload?.fileSize ?: 0L,
                fileExt = payload?.fileExt
            )

            val syncPlayMsg = RoomProtocol.encodeSyncPlay(
                track = track,
                positionMs = positionMs,
                speed = speed,
                isPlaying = true,
                syncDelayMs = syncDelay,
                fileHash = payload?.fileHash,
                fileSize = payload?.fileSize ?: 0L,
                fileExt = payload?.fileExt
            )

            snapshotClients.forEach { client ->
                try {
                    sendLineBlocking(client.writer, syncPlayMsg)
                } catch (_: Exception) {}
            }

            _roomState.update {
                it.copy(
                    isTransferringTrack = false,
                    transferProgress = 1f,
                    transferTrackTitle = null,
                    currentSyncedTrackTitle = track.title,
                    statusMessage = "Playing '${track.title}' on ${snapshotClients.size + 1} devices"
                )
            }

            withContext(Dispatchers.Main) {
                if (transferSeq.get() == seq) {
                    onReadyToPlayLocal(syncDelay)
                }
            }
        }
    }

    fun broadcastSyncPlay(
        track: Track,
        positionMs: Long = 0L,
        speed: Float = 1.0f,
        isPlaying: Boolean = true,
        syncDelayMs: Long = 180L
    ) {
        val state = _roomState.value
        if (state.role != RoomRole.HOST || state.phase != RoomPhase.PLAYING) return
        val cachedPayload = if (lastPreparedTrack?.id == track.id) lastPreparedPayload else null
        lastSyncedPlayCommand = RoomSyncCommand(
            action = "SYNC_PLAY",
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSeconds = track.durationSeconds,
            sourceUrl = track.sourceUrl,
            positionMs = positionMs,
            speed = speed,
            isPlaying = isPlaying,
            syncDelayMs = syncDelayMs,
            fileHash = cachedPayload?.fileHash,
            fileSize = cachedPayload?.fileSize ?: 0L,
            fileExt = cachedPayload?.fileExt
        )
        _roomState.update {
            it.copy(
                currentSyncedTrackTitle = track.title,
                statusMessage = "Playing '${track.title}' on ${it.members.size} devices"
            )
        }
        broadcastLineToGuests(
            RoomProtocol.encodeSyncPlay(
                track = track,
                positionMs = positionMs,
                speed = speed,
                isPlaying = isPlaying,
                syncDelayMs = syncDelayMs,
                fileHash = cachedPayload?.fileHash,
                fileSize = cachedPayload?.fileSize ?: 0L,
                fileExt = cachedPayload?.fileExt
            )
        )
    }

    fun broadcastSyncPause(positionMs: Long) {
        val state = _roomState.value
        if (state.role != RoomRole.HOST || state.phase != RoomPhase.PLAYING) return
        lastSyncedPlayCommand = lastSyncedPlayCommand?.copy(positionMs = positionMs, isPlaying = false)
        broadcastLineToGuests(RoomProtocol.encodeSyncPause(positionMs))
    }

    fun broadcastSyncSeek(positionMs: Long, isPlaying: Boolean) {
        val state = _roomState.value
        if (state.role != RoomRole.HOST || state.phase != RoomPhase.PLAYING) return
        lastSyncedPlayCommand = lastSyncedPlayCommand?.copy(positionMs = positionMs, isPlaying = isPlaying)
        broadcastLineToGuests(RoomProtocol.encodeSyncSeek(positionMs, isPlaying))
    }

    fun joinRoom(rawCode: String, manualHostIp: String? = null) {
        val cleanCode = rawCode.trim()
        val cleanHostIp = manualHostIp?.trim()?.ifBlank { null }
        refreshNetworkStatus()

        if (!RoomProtocol.isValidRoomCode(cleanCode)) {
            _roomState.update { it.copy(errorMessage = "Enter a valid 5-digit room code.") }
            return
        }

        closeInternalSockets(resetState = false)
        acquireMulticastLockSafely()

        _roomState.update {
            it.copy(
                role = RoomRole.GUEST,
                phase = RoomPhase.IDLE,
                roomCode = cleanCode,
                members = emptyList(),
                isConnecting = true,
                isDiscovering = true,
                isTransferringTrack = false,
                transferProgress = 0f,
                transferTrackTitle = null,
                statusMessage = if (cleanHostIp != null) {
                    "Connecting to Room $cleanCode at $cleanHostIp..."
                } else {
                    "Searching Wi-Fi / Hotspot for Room $cleanCode..."
                },
                errorMessage = null,
                currentSyncedTrackTitle = null
            )
        }

        guestJoinJob = scope.launch {
            val connectedFlag = AtomicBoolean(false)

            // Direct IP fast-path if user supplied optional Host IP
            if (cleanHostIp != null) {
                if (tryConnectToHost(cleanHostIp, RoomProtocol.DEFAULT_CONTROL_PORT, cleanCode, connectedFlag)) {
                    releaseMulticastLockSafely()
                    return@launch
                }
            }

            val attemptedEndpoints = ConcurrentHashMap.newKeySet<String>()

            // Run NSD, UDP Broadcast/Beacon listener, and fast /24 subnet TCP scan concurrently
            coroutineScope {
                val nsdJob = launch {
                    startNsdDiscoveryForRoom(cleanCode) { hostIp, port ->
                        val ep = "$hostIp:$port"
                        if (!connectedFlag.get() && attemptedEndpoints.add(ep)) {
                            scope.launch {
                                tryConnectToHost(hostIp, port, cleanCode, connectedFlag)
                            }
                        }
                    }
                }

                val udpJob = launch {
                    runUdpDiscoveryForRoom(cleanCode, connectedFlag) { hostIp, port ->
                        val ep = "$hostIp:$port"
                        if (!connectedFlag.get() && attemptedEndpoints.add(ep)) {
                            scope.launch {
                                tryConnectToHost(hostIp, port, cleanCode, connectedFlag)
                            }
                        }
                    }
                }

                val subnetJob = launch {
                    delay(350L) // Give UDP & NSD a brief head start
                    if (!connectedFlag.get() && isActive && _roomState.value.role == RoomRole.GUEST) {
                        scanLocalSubnetsForRoom(cleanCode, connectedFlag, attemptedEndpoints)
                    }
                }

                // Wait until connected or timeout (14s)
                val deadline = System.currentTimeMillis() + 14_000L
                while (isActive &&
                    _roomState.value.role == RoomRole.GUEST &&
                    !connectedFlag.get() &&
                    System.currentTimeMillis() < deadline
                ) {
                    delay(150L)
                }

                nsdJob.cancel()
                udpJob.cancel()
                subnetJob.cancel()
            }

            stopNsdDiscoverySafely()
            releaseMulticastLockSafely()

            if (!connectedFlag.get() && _roomState.value.role == RoomRole.GUEST) {
                _roomState.update {
                    it.copy(
                        role = RoomRole.NONE,
                        phase = RoomPhase.IDLE,
                        isConnecting = false,
                        isDiscovering = false,
                        statusMessage = null,
                        errorMessage = "Could not find Room $cleanCode on this network. Make sure both phones are on the same Wi-Fi or Hotspot."
                    )
                }
            }
        }
    }

    private suspend fun runUdpDiscoveryForRoom(
        roomCode: String,
        connectedFlag: AtomicBoolean,
        onFound: (hostIp: String, port: Int) -> Unit
    ) {
        var udpSocket: DatagramSocket? = null
        try {
            udpSocket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 500
                bind(InetSocketAddress(0))
            }
            val probeBytes = "DISCOVER_ASD_ROOM:$roomCode".toByteArray(Charsets.UTF_8)
            val targets = getBroadcastTargets()

            // Also listen for unsolicited Host beacons on a separate bound socket if available
            val beaconListenJob = scope.launch {
                var bSock: DatagramSocket? = null
                try {
                    bSock = DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        soTimeout = 600
                        bind(InetSocketAddress(RoomProtocol.DEFAULT_UDP_PORT))
                    }
                    val buf = ByteArray(1024)
                    while (isActive && !connectedFlag.get() && _roomState.value.role == RoomRole.GUEST) {
                        try {
                            val pkt = DatagramPacket(buf, buf.size)
                            bSock.receive(pkt)
                            val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8).trim()
                            parseDiscoveryReply(text, roomCode, pkt.address?.hostAddress)?.let { (ip, port) ->
                                onFound(ip, port)
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        bSock?.close()
                    } catch (_: Exception) {}
                }
            }

            val recvBuf = ByteArray(1024)
            while (!connectedFlag.get() && _roomState.value.role == RoomRole.GUEST) {
                targets.forEach { target ->
                    try {
                        val pkt = DatagramPacket(probeBytes, probeBytes.size, target, RoomProtocol.DEFAULT_UDP_PORT)
                        udpSocket.send(pkt)
                    } catch (_: Exception) {}
                }
                val startRecv = System.currentTimeMillis()
                while (!connectedFlag.get() && System.currentTimeMillis() - startRecv < 550L) {
                    try {
                        val pkt = DatagramPacket(recvBuf, recvBuf.size)
                        udpSocket.receive(pkt)
                        val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8).trim()
                        parseDiscoveryReply(text, roomCode, pkt.address?.hostAddress)?.let { (ip, port) ->
                            onFound(ip, port)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            beaconListenJob.cancel()
        } catch (e: Exception) {
            Log.d(TAG, "UDP discovery ended: ${e.message}")
        } finally {
            try {
                udpSocket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun parseDiscoveryReply(message: String, targetCode: String, senderIp: String?): Pair<String, Int>? {
        if (senderIp.isNullOrBlank()) return null
        val parts = message.split(":")
        if (parts.size == 3 &&
            (parts[0] == "ASD_ROOM_HERE" || parts[0] == "ASD_ROOM_BEACON") &&
            parts[1] == targetCode
        ) {
            val port = parts[2].toIntOrNull() ?: return null
            return senderIp to port
        }
        return null
    }

    private suspend fun scanLocalSubnetsForRoom(
        roomCode: String,
        connectedFlag: AtomicBoolean,
        attemptedEndpoints: MutableSet<String>
    ) {
        val localIps = getLocalIpv4Candidates()
        val candidateHosts = linkedSetOf<String>()

        // Always prioritize common Android/iOS Hotspot gateway IPs first
        candidateHosts.add("192.168.43.1")
        candidateHosts.add("192.168.49.1")
        candidateHosts.add("192.168.137.1")
        candidateHosts.add("172.20.10.1")

        localIps.forEach { ip ->
            val octets = ip.split(".")
            if (octets.size == 4) {
                val prefix = "${octets[0]}.${octets[1]}.${octets[2]}"
                candidateHosts.add("$prefix.1")
                for (last in 2..254) {
                    val host = "$prefix.$last"
                    if (host != ip) {
                        candidateHosts.add(host)
                    }
                }
            }
        }

        val semaphore = Semaphore(36)
        coroutineScope {
            candidateHosts.map { ip ->
                async(Dispatchers.IO) {
                    if (connectedFlag.get() || !isActive || _roomState.value.role != RoomRole.GUEST) return@async
                    val ep = "$ip:${RoomProtocol.DEFAULT_CONTROL_PORT}"
                    if (!attemptedEndpoints.add(ep)) return@async
                    semaphore.withPermit {
                        if (connectedFlag.get() || !isActive || _roomState.value.role != RoomRole.GUEST) return@withPermit
                        tryConnectToHost(ip, RoomProtocol.DEFAULT_CONTROL_PORT, roomCode, connectedFlag, connectTimeoutMs = 450)
                    }
                }
            }.awaitAll()
        }
    }

    private fun startNsdDiscoveryForRoom(
        roomCode: String,
        onResolved: (hostIp: String, port: Int) -> Unit
    ) {
        try {
            val mgr = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr
            val expectedName = "ASDMusic_$roomCode"
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceName.startsWith(expectedName)) {
                        try {
                            @Suppress("DEPRECATION")
                            mgr.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    @Suppress("DEPRECATION")
                                    val hostIp = info.host?.hostAddress
                                    if (!hostIp.isNullOrBlank() && info.port > 0) {
                                        onResolved(hostIp, info.port)
                                    }
                                }
                            })
                        } catch (_: Exception) {}
                    }
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            nsdDiscoveryListener = listener
            mgr.discoverServices(RoomProtocol.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD discover skipped: ${e.message}")
        }
    }

    private fun stopNsdRegistrationSafely() {
        nsdRegistrationListener?.let { l ->
            try {
                nsdManager?.unregisterService(l)
            } catch (_: Exception) {}
        }
        nsdRegistrationListener = null
    }

    private fun stopNsdDiscoverySafely() {
        nsdDiscoveryListener?.let { l ->
            try {
                nsdManager?.stopServiceDiscovery(l)
            } catch (_: Exception) {}
        }
        nsdDiscoveryListener = null
    }

    private suspend fun tryConnectToHost(
        hostIp: String,
        port: Int,
        roomCode: String,
        connectedFlag: AtomicBoolean,
        connectTimeoutMs: Int = 1500
    ): Boolean {
        if (connectedFlag.get()) return false
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(hostIp, port), connectTimeoutMs)
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 4000

            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), 65536)
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8), 65536)

            sendLineBlocking(writer, RoomProtocol.encodeJoin(roomCode, getLocalDeviceDisplayName(), localMemberId))
            val responseLine = reader.readLine()
            if (responseLine.isNullOrBlank()) {
                socket.close()
                return false
            }

            val respJson = JSONObject(responseLine)
            if (respJson.optString("type") == "WELCOME" && connectedFlag.compareAndSet(false, true)) {
                socket.soTimeout = 0 // Block cleanly during active room session
                guestSocket = socket
                guestWriter = writer

                val phaseName = respJson.optString("phase", RoomPhase.LOBBY.name)
                val parsedPhase = RoomPhase.entries.firstOrNull { it.name == phaseName } ?: RoomPhase.LOBBY
                val parsedMembers = RoomProtocol.parseMembersArray(respJson.optJSONArray("members"))

                _roomState.update {
                    it.copy(
                        role = RoomRole.GUEST,
                        phase = parsedPhase,
                        roomCode = roomCode,
                        members = parsedMembers,
                        hostIpAddress = hostIp,
                        isConnecting = false,
                        isDiscovering = false,
                        statusMessage = if (parsedPhase == RoomPhase.PLAYING) {
                            "Connected to live Room $roomCode!"
                        } else {
                            "Joined Room $roomCode! Waiting for Host to tap Start Room..."
                        },
                        errorMessage = null
                    )
                }

                if (parsedPhase == RoomPhase.PLAYING) {
                    onGuestRoomStarted()
                }

                listenToHostMessages(socket, reader, writer)
                true
            } else {
                socket.close()
                false
            }
        } catch (_: Exception) {
            try {
                socket?.close()
            } catch (_: Exception) {}
            false
        }
    }

    private fun listenToHostMessages(
        socket: Socket,
        reader: BufferedReader,
        writer: BufferedWriter
    ) {
        scope.launch {
            try {
                while (isActive && _roomState.value.role == RoomRole.GUEST) {
                    val line = reader.readLine() ?: break
                    val json = JSONObject(line)
                    when (json.optString("type")) {
                        "PING" -> {
                            // Keepalive heartbeat
                        }
                        "MEMBERS" -> {
                            val updatedMembers = RoomProtocol.parseMembersArray(json.optJSONArray("members"))
                            _roomState.update {
                                it.copy(
                                    members = updatedMembers,
                                    statusMessage = "${updatedMembers.size} people in Room ${it.roomCode}"
                                )
                            }
                        }
                        "START_ROOM" -> {
                            _roomState.update {
                                it.copy(
                                    phase = RoomPhase.PLAYING,
                                    statusMessage = "Room started! Waiting for Host to pick a song..."
                                )
                            }
                            onGuestRoomStarted()
                        }
                        "PREPARE_TRACK" -> {
                            handleGuestPrepareTrack(json, writer)
                        }
                        "SYNC_PLAY", "SYNC_PAUSE", "SYNC_SEEK" -> {
                            val cmd = RoomProtocol.parseSyncCommand(json)
                            if (cmd != null) {
                                val resolvedCmd = if (cmd.action == "SYNC_PLAY") {
                                    val cachedLocal = cmd.fileHash?.let { syncedFilePaths[it] }
                                        ?: if (!cmd.fileHash.isNullOrBlank() && !cmd.fileExt.isNullOrBlank()) {
                                            val candidate = File(syncCacheDir, "${cmd.fileHash}.${cmd.fileExt}")
                                            if (candidate.exists() && candidate.length() > 512L) candidate.absolutePath else null
                                        } else null
                                        ?: cmd.sourceUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                                            ?.let { syncedFilePaths["url_${it.hashCode()}"] }

                                    _roomState.update {
                                        it.copy(
                                            phase = RoomPhase.PLAYING,
                                            isTransferringTrack = false,
                                            transferTrackTitle = null,
                                            currentSyncedTrackTitle = cmd.title,
                                            statusMessage = "Playing together: ${cmd.title ?: "Song"}"
                                        )
                                    }
                                    cmd.copy(localSyncedFilePath = cachedLocal)
                                } else {
                                    cmd
                                }
                                onGuestSyncCommand(resolvedCmd)
                            }
                        }
                        "ROOM_CLOSED" -> {
                            val reason = json.optString("reason", "Host ended the Room")
                            closeInternalSockets(resetState = true)
                            onRoomEnded(reason)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Host connection ended: ${e.message}")
            } finally {
                if (_roomState.value.role == RoomRole.GUEST && guestSocket == socket) {
                    closeInternalSockets(resetState = true)
                    onRoomEnded("Disconnected from Network Room")
                }
            }
        }
    }

    private fun handleGuestPrepareTrack(json: JSONObject, writer: BufferedWriter) {
        guestDownloadJob?.cancel()
        val transferId = json.optString("transferId")
        val title = json.optString("title", "Shared Song")
        val fileHash = json.optString("fileHash")
        val fileSize = json.optLong("fileSize", 0L)
        val fileExt = json.optString("fileExt", "mp3").ifBlank { "mp3" }
        val hasBinary = json.optBoolean("hasBinary", false)
        val audioDownloadUrl = json.optString("audioDownloadUrl")
        val sourceUrl = json.optString("sourceUrl")

        if (hasBinary && fileHash.isNotBlank() && fileSize > 0L) {
            val cachedFile = File(syncCacheDir, "$fileHash.$fileExt")
            val existingCachedPath = syncedFilePaths[fileHash]
                ?: if (cachedFile.exists() && cachedFile.length() == fileSize) cachedFile.absolutePath else null
                ?: onFindLocalFileByHash(fileHash, fileSize, title)

            if (existingCachedPath != null) {
                syncedFilePaths[fileHash] = existingCachedPath
                sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
            } else if (audioDownloadUrl.startsWith("http://") || audioDownloadUrl.startsWith("https://")) {
                _roomState.update {
                    it.copy(
                        isTransferringTrack = true,
                        transferProgress = 0.05f,
                        transferTrackTitle = title,
                        statusMessage = "Downloading '$title' over network..."
                    )
                }
                guestDownloadJob = scope.launch {
                    val savedPath = downloadUrlToCacheWithProgress(
                        url = audioDownloadUrl,
                        targetFile = cachedFile,
                        expectedSize = fileSize,
                        trackTitle = title
                    )
                    if (savedPath != null) {
                        syncedFilePaths[fileHash] = savedPath
                        _roomState.update {
                            it.copy(
                                isTransferringTrack = false,
                                transferProgress = 1f,
                                statusMessage = "'$title' synced! Starting playback..."
                            )
                        }
                        sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
                    } else {
                        _roomState.update { it.copy(isTransferringTrack = false) }
                        sendGuestLineAsync(writer, RoomProtocol.encodeTrackFailed(transferId, "HTTP transfer failed"))
                    }
                }
            } else {
                sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
            }
        } else if (sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")) {
            val urlKey = "url_${sourceUrl.hashCode()}"
            val cachedUrlFile = File(syncCacheDir, "$urlKey.$fileExt")
            if (cachedUrlFile.exists() && cachedUrlFile.length() > 1024L) {
                syncedFilePaths[urlKey] = cachedUrlFile.absolutePath
                sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
            } else {
                _roomState.update {
                    it.copy(
                        isTransferringTrack = true,
                        transferProgress = 0.15f,
                        transferTrackTitle = title,
                        statusMessage = "Downloading '$title'..."
                    )
                }
                guestDownloadJob = scope.launch {
                    val savedPath = downloadUrlToCacheWithProgress(
                        url = sourceUrl,
                        targetFile = cachedUrlFile,
                        expectedSize = 0L,
                        trackTitle = title
                    )
                    if (savedPath != null) {
                        syncedFilePaths[urlKey] = savedPath
                    }
                    _roomState.update { it.copy(isTransferringTrack = false) }
                    sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
                }
            }
        } else {
            sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
        }
    }

    private fun downloadUrlToCacheWithProgress(
        url: String,
        targetFile: File,
        expectedSize: Long,
        trackTitle: String
    ): String? {
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
        if (partFile.exists()) partFile.delete()

        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 25000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }

            val contentLen = conn.contentLengthLong.takeIf { it > 0L } ?: expectedSize
            var totalRead = 0L
            var lastEmit = 0L

            BufferedInputStream(conn.inputStream, 32768).use { input ->
                BufferedOutputStream(FileOutputStream(partFile), 32768).use { output ->
                    val buf = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        totalRead += read
                        val now = System.currentTimeMillis()
                        if (contentLen > 0L && (now - lastEmit >= 100L || totalRead >= contentLen)) {
                            lastEmit = now
                            val pct = (totalRead.toFloat() / contentLen.toFloat()).coerceIn(0f, 0.99f)
                            _roomState.update {
                                it.copy(
                                    isTransferringTrack = true,
                                    transferProgress = pct,
                                    transferTrackTitle = trackTitle,
                                    statusMessage = "Receiving '$trackTitle' (${(pct * 100).toInt()}%)..."
                                )
                            }
                        }
                    }
                }
            }
            conn.disconnect()

            if (partFile.exists() && partFile.length() > 512L) {
                if (targetFile.exists()) targetFile.delete()
                if (partFile.renameTo(targetFile)) targetFile.absolutePath else partFile.absolutePath
            } else {
                partFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP download failed: ${e.message}")
            try {
                if (partFile.exists()) partFile.delete()
            } catch (_: Exception) {}
            null
        }
    }

    private fun sendGuestLineAsync(writer: BufferedWriter, line: String) {
        scope.launch {
            try {
                sendLineBlocking(writer, line)
            } catch (e: Exception) {
                Log.w(TAG, "Guest send error: ${e.message}")
            }
        }
    }

    private fun getLocalIpv4Candidates(): List<String> {
        val preferred = mutableListOf<String>()
        val secondary = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                val isLanInterface = name.startsWith("wlan") ||
                    name.startsWith("ap") ||
                    name.startsWith("swlan") ||
                    name.startsWith("eth") ||
                    name.startsWith("rndis")
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (isLanInterface) preferred.add(hostAddr) else secondary.add(hostAddr)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getLocalIpv4Candidates error: ${e.message}")
        }
        return (preferred + secondary).distinct()
    }

    private fun buildHttpAudioUrl(
        localAddr: InetAddress?,
        fileHash: String,
        fallbackIpv4: String? = getLocalIpv4Candidates().firstOrNull()
    ): String? {
        val rawHost = when (localAddr) {
            is Inet4Address -> localAddr.hostAddress
            else -> fallbackIpv4 ?: localAddr?.hostAddress?.substringBefore("%")
        }?.trim()
        if (rawHost.isNullOrBlank()) return null
        val formattedHost = if (rawHost.contains(":")) "[$rawHost]" else rawHost
        return "http://$formattedHost:$httpServerPort/audio/$fileHash"
    }

    private fun getBroadcastTargets(): List<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        try {
            targets.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {}

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                iface.interfaceAddresses?.forEach { ifAddr ->
                    val bcast = ifAddr.broadcast
                    if (bcast is Inet4Address) {
                        targets.add(bcast)
                    }
                }
            }
        } catch (_: Exception) {}

        listOf("192.168.43.1", "192.168.49.1", "192.168.137.1", "172.20.10.1").forEach { gw ->
            try {
                targets.add(InetAddress.getByName(gw))
            } catch (_: Exception) {}
        }
        return targets.toList()
    }

    fun leaveOrCloseRoom() {
        val currentRole = _roomState.value.role
        if (currentRole == RoomRole.HOST) {
            val closeMsg = RoomProtocol.encodeRoomClosed("Host closed the Room")
            hostClients.values.forEach { client ->
                try {
                    sendLineBlocking(client.writer, closeMsg)
                } catch (_: Exception) {}
            }
        }
        closeInternalSockets(resetState = true)
    }

    fun clearFeedback() {
        _roomState.update { it.copy(errorMessage = null) }
    }

    private fun closeInternalSockets(resetState: Boolean) {
        stopNsdRegistrationSafely()
        stopNsdDiscoverySafely()
        releaseMulticastLockSafely()

        prepareTrackJob?.cancel()
        prepareTrackJob = null
        guestDownloadJob?.cancel()
        guestDownloadJob = null
        activeTransferId = null
        clientReadySignals.clear()

        hostAcceptJob?.cancel()
        hostAcceptJob = null
        httpAcceptJob?.cancel()
        httpAcceptJob = null
        udpResponderJob?.cancel()
        udpResponderJob = null
        hostBeaconJob?.cancel()
        hostBeaconJob = null
        hostPingJob?.cancel()
        hostPingJob = null
        guestJoinJob?.cancel()
        guestJoinJob = null

        hostClients.values.forEach { client ->
            try {
                client.socket.close()
            } catch (_: Exception) {}
        }
        hostClients.clear()

        try {
            controlServerSocket?.close()
        } catch (_: Exception) {}
        controlServerSocket = null

        try {
            httpServerSocket?.close()
        } catch (_: Exception) {}
        httpServerSocket = null
        httpServerPort = 0

        try {
            udpResponderSocket?.close()
        } catch (_: Exception) {}
        udpResponderSocket = null

        try {
            guestSocket?.close()
        } catch (_: Exception) {}
        guestSocket = null
        guestWriter = null

        lastSyncedPlayCommand = null
        lastPreparedTrack = null
        lastPreparedPayload = null

        if (resetState) {
            _roomState.update {
                it.copy(
                    role = RoomRole.NONE,
                    phase = RoomPhase.IDLE,
                    roomCode = "",
                    members = emptyList(),
                    isConnecting = false,
                    isDiscovering = false,
                    isTransferringTrack = false,
                    transferProgress = 0f,
                    transferTrackTitle = null,
                    statusMessage = null,
                    errorMessage = null,
                    currentSyncedTrackTitle = null
                )
            }
        }
    }

    fun release() {
        leaveOrCloseRoom()
        scope.cancel()
    }
}
