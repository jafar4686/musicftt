package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.BluetoothRoomState
import com.example.model.RoomMember
import com.example.model.RoomPhase
import com.example.model.RoomRole
import com.example.model.RoomSyncCommand
import com.example.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class BluetoothRoomManager(
    private val context: Context,
    private val onGuestSyncCommand: (RoomSyncCommand) -> Unit = {},
    private val onGuestRoomStarted: () -> Unit = {},
    private val onRoomEnded: (String) -> Unit = {},
    private val onFindLocalFileByHash: (fileHash: String, fileSize: Long, title: String?) -> String? = { _, _, _ -> null }
) {

    companion object {
        private const val TAG = "BluetoothRoomManager"
        const val ROOM_SERVICE_NAME = "ASDMusicRoom"
        val ROOM_UUID: UUID = UUID.fromString("8a9d5c10-4b22-4f88-9c31-6e7b31a5d480")
        val RoomProtocol: com.example.bluetooth.RoomProtocol = com.example.bluetooth.RoomProtocol

        fun generateFiveDigitCode(): String {
            return Random.nextInt(10_000, 100_000).toString()
        }

        fun isValidRoomCode(code: String): Boolean {
            return code.length == 5 && code.all { it.isDigit() }
        }

        fun requiredBluetoothPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        fun hasBluetoothPermissions(context: Context): Boolean {
            return requiredBluetoothPermissions().all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private data class ClientSession(
        val member: RoomMember,
        val socket: BluetoothSocket,
        val writer: BufferedWriter
    )

    private data class IncomingFileState(
        val transferId: String,
        val fileHash: String,
        val fileExt: String,
        val expectedSize: Long,
        val title: String,
        val partFile: File,
        val finalFile: File,
        val output: BufferedOutputStream,
        var bytesWritten: Long = 0L
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localMemberId = "dev_${Build.MODEL.hashCode()}_${Random.nextInt(100, 999)}"
    private val syncCacheDir = File(appContext.filesDir, "room_sync_cache").apply { mkdirs() }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private val _roomState = MutableStateFlow(
        BluetoothRoomState(
            isBluetoothSupported = bluetoothAdapter != null,
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
            hasBluetoothPermission = hasBluetoothPermissions(appContext)
        )
    )
    val roomState: StateFlow<BluetoothRoomState> = _roomState.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var hostAcceptJob: Job? = null
    private var prepareTrackJob: Job? = null
    private val transferSeq = AtomicLong(0L)
    private val hostClients = ConcurrentHashMap<String, ClientSession>()

    @Volatile private var activeTransferId: String? = null
    private val clientPrepSignals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>() // true = READY, false = NEED_FILE
    private val clientReadyAfterTransferSignals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private var lastSyncedPlayCommand: RoomSyncCommand? = null
    private var lastPreparedTrack: Track? = null
    private var lastPreparedPayload: TrackAudioPayload? = null

    private var guestSocket: BluetoothSocket? = null
    private var guestWriter: BufferedWriter? = null
    private var guestJoinJob: Job? = null
    private var discoveryReceiver: BroadcastReceiver? = null
    private val syncedFilePaths = ConcurrentHashMap<String, String>()
    private var incomingFile: IncomingFileState? = null

    fun refreshBluetoothStatus() {
        val supported = bluetoothAdapter != null
        val enabled = try {
            bluetoothAdapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
        val hasPerm = hasBluetoothPermissions(appContext)
        _roomState.update {
            it.copy(
                isBluetoothSupported = supported,
                isBluetoothEnabled = enabled,
                hasBluetoothPermission = hasPerm
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocalDeviceDisplayName(): String {
        return try {
            val btName = if (hasBluetoothPermissions(appContext)) bluetoothAdapter?.name else null
            btName?.takeIf { it.isNotBlank() } ?: Build.MODEL.ifBlank { "Android Device" }
        } catch (_: SecurityException) {
            Build.MODEL.ifBlank { "Android Device" }
        }
    }

    @SuppressLint("MissingPermission")
    fun createRoom(): String? {
        refreshBluetoothStatus()
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _roomState.update { it.copy(errorMessage = "Bluetooth hardware is not available on this device.") }
            return null
        }
        if (!hasBluetoothPermissions(appContext)) {
            _roomState.update { it.copy(errorMessage = "Grant Bluetooth permissions first to create a Room.") }
            return null
        }
        if (!adapter.isEnabled) {
            _roomState.update { it.copy(errorMessage = "Please turn on Bluetooth first.") }
            return null
        }

        closeInternalSockets(resetState = false)

        val code = generateFiveDigitCode()
        val hostMember = RoomMember(
            id = localMemberId,
            name = "${getLocalDeviceDisplayName()} (Host)",
            isHost = true
        )

        _roomState.update {
            it.copy(
                role = RoomRole.HOST,
                phase = RoomPhase.LOBBY,
                roomCode = code,
                members = listOf(hostMember),
                isConnecting = false,
                isDiscovering = false,
                isTransferringTrack = false,
                transferProgress = 0f,
                transferTrackTitle = null,
                statusMessage = "Room $code created. Waiting for devices to join...",
                errorMessage = null,
                currentSyncedTrackTitle = null
            )
        }

        startHostRfcommServer(adapter, code, hostMember)
        return code
    }

    @SuppressLint("MissingPermission")
    private fun startHostRfcommServer(
        adapter: BluetoothAdapter,
        roomCode: String,
        hostMember: RoomMember
    ) {
        hostAcceptJob = scope.launch {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(ROOM_SERVICE_NAME, ROOM_UUID)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Bluetooth server socket: ${e.message}")
                _roomState.update {
                    it.copy(errorMessage = "Could not start Bluetooth Room server: ${e.message}")
                }
                return@launch
            }

            while (isActive && _roomState.value.role == RoomRole.HOST) {
                val clientSocket = try {
                    serverSocket?.accept()
                } catch (_: Exception) {
                    null
                } ?: break

                launch {
                    handleIncomingGuestConnection(clientSocket, roomCode, hostMember)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleIncomingGuestConnection(
        socket: BluetoothSocket,
        expectedCode: String,
        hostMember: RoomMember
    ) {
        var assignedClientId: String? = null
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8), 65536)
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), 65536)

            val handshakeLine = withTimeoutOrNull(8_000L) {
                reader.readLine()
            }

            if (handshakeLine.isNullOrBlank()) {
                socket.close()
                return
            }

            val json = JSONObject(handshakeLine)
            if (json.optString("type") != "JOIN") {
                sendLineBlocking(writer, RoomProtocol.encodeReject("Unexpected handshake"))
                socket.close()
                return
            }

            val enteredCode = json.optString("code").trim()
            if (enteredCode != expectedCode) {
                sendLineBlocking(writer, RoomProtocol.encodeReject("Wrong 5-digit room code"))
                socket.close()
                return
            }

            val fallbackDeviceName = try {
                socket.remoteDevice?.name ?: "Guest Device"
            } catch (_: SecurityException) {
                "Guest Device"
            }
            val guestName = json.optString("name").ifBlank { fallbackDeviceName }
            val guestId = json.optString("id").ifBlank { "guest_${System.currentTimeMillis()}" }
            val guestAddress = try {
                socket.remoteDevice?.address
            } catch (_: SecurityException) {
                null
            }

            val newMember = RoomMember(
                id = guestId,
                name = guestName,
                isHost = false,
                address = guestAddress
            )
            assignedClientId = guestId

            val session = ClientSession(newMember, socket, writer)
            hostClients[guestId] = session

            val updatedMembers = buildCurrentHostMemberList(hostMember)
            _roomState.update {
                it.copy(
                    members = updatedMembers,
                    statusMessage = "$guestName joined the Room (${updatedMembers.size} in room)"
                )
            }

            val currentPhase = _roomState.value.phase
            sendLineBlocking(writer, RoomProtocol.encodeWelcome(expectedCode, currentPhase, updatedMembers))

            // Broadcast updated member list to every other connected guest
            broadcastLineToGuests(RoomProtocol.encodeMembers(updatedMembers), excludeId = guestId)

            // If room is already PLAYING and has a current track, sync that real audio file to the late joiner
            if (currentPhase == RoomPhase.PLAYING) {
                val track = lastPreparedTrack
                val cmd = lastSyncedPlayCommand
                if (track != null && cmd != null) {
                    scope.launch {
                        syncLateJoinerTrack(session, track, lastPreparedPayload, cmd)
                    }
                }
            }

            // Keep reading responses/status from this guest
            while (scope.isActive && _roomState.value.role == RoomRole.HOST) {
                val line = reader.readLine() ?: break
                handleHostReceivedFromGuest(guestId, line)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Guest connection closed: ${e.message}")
        } finally {
            if (assignedClientId != null) {
                hostClients.remove(assignedClientId)
                clientPrepSignals.remove(assignedClientId)?.complete(true)
                clientReadyAfterTransferSignals.remove(assignedClientId)?.complete(true)
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
                        clientPrepSignals[guestId]?.complete(true)
                        clientReadyAfterTransferSignals[guestId]?.complete(true)
                    } else {
                        clientReadyAfterTransferSignals["$txId:$guestId"]?.complete(true)
                    }
                }
                "NEED_FILE" -> {
                    if (txId == activeTransferId) {
                        clientPrepSignals[guestId]?.complete(false)
                    } else {
                        clientPrepSignals["$txId:$guestId"]?.complete(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Host message parse error: ${e.message}")
        }
    }

    private suspend fun syncLateJoinerTrack(
        client: ClientSession,
        track: Track,
        payload: TrackAudioPayload?,
        cmd: RoomSyncCommand
    ) {
        val lateTxId = "late_${System.currentTimeMillis()}"
        val prepSignal = CompletableDeferred<Boolean>()
        val readySignal = CompletableDeferred<Boolean>()
        val key = "$lateTxId:${client.member.id}"
        clientPrepSignals[key] = prepSignal
        clientReadyAfterTransferSignals[key] = readySignal

        try {
            sendLineBlocking(
                client.writer,
                RoomProtocol.encodePrepareTrack(
                    transferId = lateTxId,
                    track = track,
                    fileHash = payload?.fileHash,
                    fileSize = payload?.fileSize ?: 0L,
                    fileExt = payload?.fileExt,
                    hasBinary = payload != null
                )
            )
            val alreadyHasIt = withTimeoutOrNull(3_000L) { prepSignal.await() } ?: false
            if (!alreadyHasIt && payload != null) {
                streamPayloadToClients(lateTxId, track.title, payload, listOf(client), updateUiProgress = false)
                withTimeoutOrNull(4_000L) { readySignal.await() }
            }
            sendLineBlocking(
                client.writer,
                RoomProtocol.encodeSyncPlay(
                    track = track,
                    positionMs = cmd.positionMs,
                    speed = cmd.speed,
                    isPlaying = cmd.isPlaying,
                    syncDelayMs = 100L,
                    fileHash = payload?.fileHash,
                    fileSize = payload?.fileSize ?: 0L,
                    fileExt = payload?.fileExt
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Late joiner sync error: ${e.message}")
        } finally {
            clientPrepSignals.remove(key)
            clientReadyAfterTransferSignals.remove(key)
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
                statusMessage = "Room started! Pick a song to play for everyone simultaneously.",
                errorMessage = null
            )
        }
        broadcastLineToGuests(RoomProtocol.encodeStartRoom())
    }

    /**
     * Ensures every connected Bluetooth device has the exact audio bytes for [track]
     * (either cached by SHA-256 or transferred live over RFCOMM), then broadcasts SYNC_PLAY
     * and invokes [onReadyToPlayLocal] with a sync start delay so everyone plays the same song together.
     */
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

            val txId = "tx_${seq}_${System.currentTimeMillis()}"
            activeTransferId = txId
            clientPrepSignals.clear()
            clientReadyAfterTransferSignals.clear()

            _roomState.update {
                it.copy(
                    isTransferringTrack = true,
                    transferProgress = 0f,
                    transferTrackTitle = track.title,
                    statusMessage = "Syncing '${track.title}' to ${snapshotClients.size} device(s)..."
                )
            }

            val prepMsg = RoomProtocol.encodePrepareTrack(
                transferId = txId,
                track = track,
                fileHash = payload?.fileHash,
                fileSize = payload?.fileSize ?: 0L,
                fileExt = payload?.fileExt,
                hasBinary = payload != null
            )

            snapshotClients.forEach { client ->
                val cid = client.member.id
                clientPrepSignals[cid] = CompletableDeferred()
                clientReadyAfterTransferSignals[cid] = CompletableDeferred()
                try {
                    sendLineBlocking(client.writer, prepMsg)
                } catch (_: Exception) {
                    clientPrepSignals[cid]?.complete(true)
                    clientReadyAfterTransferSignals[cid]?.complete(true)
                }
            }

            // Wait briefly for guests to check local cache & say TRACK_READY or NEED_FILE
            val needingClients = mutableListOf<ClientSession>()
            snapshotClients.forEach { client ->
                if (!isActive || transferSeq.get() != seq) return@launch
                val cid = client.member.id
                val readyImmediately = withTimeoutOrNull(2_500L) {
                    clientPrepSignals[cid]?.await()
                } ?: false
                if (!readyImmediately) {
                    needingClients.add(client)
                }
            }

            // Stream actual audio file chunks to guests that don't already have this exact song
            if (needingClients.isNotEmpty() && payload != null && isActive && transferSeq.get() == seq) {
                val streamOk = streamPayloadToClients(
                    transferId = txId,
                    trackTitle = track.title,
                    payload = payload,
                    targets = needingClients,
                    updateUiProgress = true
                )
                if (streamOk && isActive && transferSeq.get() == seq) {
                    needingClients.forEach { client ->
                        withTimeoutOrNull(4_000L) {
                            clientReadyAfterTransferSignals[client.member.id]?.await()
                        }
                    }
                }
            } else if (needingClients.isNotEmpty() && payload == null) {
                // Non-binary (starter or remote URL) -> wait briefly for remote URL download readiness
                needingClients.forEach { client ->
                    withTimeoutOrNull(6_000L) {
                        clientReadyAfterTransferSignals[client.member.id]?.await()
                    }
                }
            }

            if (!isActive || transferSeq.get() != seq) return@launch

            val syncDelay = 240L
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
                    statusMessage = "Playing '${track.title}' across ${snapshotClients.size + 1} devices"
                )
            }

            withContext(Dispatchers.Main) {
                if (transferSeq.get() == seq) {
                    onReadyToPlayLocal(syncDelay)
                }
            }
        }
    }

    private fun streamPayloadToClients(
        transferId: String,
        trackTitle: String,
        payload: TrackAudioPayload,
        targets: List<ClientSession>,
        updateUiProgress: Boolean
    ): Boolean {
        return try {
            val startMsg = RoomProtocol.encodeFileStart(
                transferId = transferId,
                fileHash = payload.fileHash,
                fileSize = payload.fileSize,
                fileExt = payload.fileExt,
                title = trackTitle
            )
            targets.forEach { client ->
                try {
                    sendLineBlocking(client.writer, startMsg)
                } catch (_: Exception) {}
            }

            val stream = payload.openStream() ?: return false
            var sentBytes = 0L
            var chunkSeq = 0
            var lastProgressEmit = 0L
            val buffer = ByteArray(RoomProtocol.CHUNK_RAW_BYTES)

            stream.use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    if (_roomState.value.role != RoomRole.HOST) return false
                    if (updateUiProgress && activeTransferId != transferId) return false

                    val encoded = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                    val chunkMsg = RoomProtocol.encodeFileChunk(transferId, chunkSeq++, encoded)
                    targets.forEach { client ->
                        try {
                            sendLineBlocking(client.writer, chunkMsg)
                        } catch (_: Exception) {}
                    }

                    sentBytes += read
                    val now = System.currentTimeMillis()
                    if (updateUiProgress && (now - lastProgressEmit >= 120L || sentBytes >= payload.fileSize)) {
                        lastProgressEmit = now
                        val progress = if (payload.fileSize > 0L) {
                            (sentBytes.toFloat() / payload.fileSize.toFloat()).coerceIn(0f, 0.99f)
                        } else 0f
                        _roomState.update {
                            it.copy(
                                isTransferringTrack = true,
                                transferProgress = progress,
                                transferTrackTitle = trackTitle,
                                statusMessage = "Sending '$trackTitle' to devices (${(progress * 100).toInt()}%)..."
                            )
                        }
                    }
                }
            }

            val endMsg = RoomProtocol.encodeFileEnd(transferId, payload.fileHash)
            targets.forEach { client ->
                try {
                    sendLineBlocking(client.writer, endMsg)
                } catch (_: Exception) {}
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "streamPayloadToClients error: ${e.message}")
            if (updateUiProgress) {
                _roomState.update { it.copy(isTransferringTrack = false) }
            }
            false
        }
    }

    fun broadcastSyncPlay(
        track: Track,
        positionMs: Long = 0L,
        speed: Float = 1.0f,
        isPlaying: Boolean = true,
        syncDelayMs: Long = 240L
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
                statusMessage = "Playing '${track.title}' across ${it.members.size} devices"
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

    @SuppressLint("MissingPermission")
    fun joinRoom(rawCode: String) {
        val cleanCode = rawCode.trim()
        refreshBluetoothStatus()
        val adapter = bluetoothAdapter

        if (!isValidRoomCode(cleanCode)) {
            _roomState.update { it.copy(errorMessage = "Enter a valid 5-digit room code.") }
            return
        }
        if (adapter == null) {
            _roomState.update { it.copy(errorMessage = "Bluetooth is not supported on this device.") }
            return
        }
        if (!hasBluetoothPermissions(appContext)) {
            _roomState.update { it.copy(errorMessage = "Grant Bluetooth permissions first to join a Room.") }
            return
        }
        if (!adapter.isEnabled) {
            _roomState.update { it.copy(errorMessage = "Turn on Bluetooth first to join a Room.") }
            return
        }

        closeInternalSockets(resetState = false)

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
                statusMessage = "Searching nearby Bluetooth devices for Room $cleanCode...",
                errorMessage = null,
                currentSyncedTrackTitle = null
            )
        }

        guestJoinJob = scope.launch {
            val attemptedAddresses = mutableSetOf<String>()
            val pairedDevices = try {
                adapter.bondedDevices?.toList().orEmpty()
            } catch (_: SecurityException) {
                emptyList()
            }

            for (device in pairedDevices) {
                if (!isActive || _roomState.value.role != RoomRole.GUEST) return@launch
                val addr = device.address ?: continue
                attemptedAddresses.add(addr)
                if (tryConnectToHostDevice(adapter, device, cleanCode)) {
                    return@launch
                }
            }

            val candidateQueue = java.util.concurrent.LinkedBlockingQueue<BluetoothDevice>()
            var discoveryCompleted = false

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            if (device != null && !attemptedAddresses.contains(device.address)) {
                                candidateQueue.offer(device)
                            }
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            discoveryCompleted = true
                        }
                    }
                }
            }

            registerDiscoveryReceiverSafely(receiver)
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                adapter.startDiscovery()
            } catch (e: Exception) {
                Log.w(TAG, "startDiscovery error: ${e.message}")
                discoveryCompleted = true
            }

            val deadlineMs = System.currentTimeMillis() + 18_000L
            while (isActive &&
                _roomState.value.role == RoomRole.GUEST &&
                guestSocket == null &&
                System.currentTimeMillis() < deadlineMs
            ) {
                val nextDevice = candidateQueue.poll()
                if (nextDevice != null) {
                    val addr = nextDevice.address
                    if (addr != null && attemptedAddresses.add(addr)) {
                        if (tryConnectToHostDevice(adapter, nextDevice, cleanCode)) {
                            unregisterDiscoveryReceiverSafely()
                            return@launch
                        }
                    }
                } else {
                    if (discoveryCompleted && candidateQueue.isEmpty()) {
                        break
                    }
                    delay(200L)
                }
            }

            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
            } catch (_: Exception) {}
            unregisterDiscoveryReceiverSafely()

            if (guestSocket == null && _roomState.value.role == RoomRole.GUEST) {
                _roomState.update {
                    it.copy(
                        role = RoomRole.NONE,
                        phase = RoomPhase.IDLE,
                        isConnecting = false,
                        isDiscovering = false,
                        statusMessage = null,
                        errorMessage = "Could not find Room $cleanCode. Ensure host has Bluetooth on & is discoverable."
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryConnectToHostDevice(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        roomCode: String
    ): Boolean {
        var socket: BluetoothSocket? = null
        return try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            socket = device.createRfcommSocketToServiceRecord(ROOM_UUID)
            socket.connect()

            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), 65536)
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8), 65536)

            val myName = getLocalDeviceDisplayName()
            sendLineBlocking(writer, RoomProtocol.encodeJoin(roomCode, myName, localMemberId))

            val responseLine = withTimeoutOrNull(4_000L) { reader.readLine() }
            if (responseLine.isNullOrBlank()) {
                socket.close()
                return false
            }

            val respJson = JSONObject(responseLine)
            when (respJson.optString("type")) {
                "WELCOME" -> {
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
                            isConnecting = false,
                            isDiscovering = false,
                            statusMessage = if (parsedPhase == RoomPhase.PLAYING) {
                                "Joined live Room $roomCode!"
                            } else {
                                "Joined Room $roomCode! Waiting for Host to start..."
                            },
                            errorMessage = null
                        )
                    }

                    if (parsedPhase == RoomPhase.PLAYING) {
                        onGuestRoomStarted()
                    }

                    listenToHostMessages(socket, reader, writer)
                    true
                }
                else -> {
                    socket.close()
                    false
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Device ${device.address} did not match room $roomCode: ${e.message}")
            try {
                socket?.close()
            } catch (_: Exception) {}
            false
        }
    }

    private fun listenToHostMessages(
        socket: BluetoothSocket,
        reader: BufferedReader,
        writer: BufferedWriter
    ) {
        scope.launch {
            try {
                while (isActive && _roomState.value.role == RoomRole.GUEST) {
                    val line = reader.readLine() ?: break
                    val json = JSONObject(line)
                    when (json.optString("type")) {
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
                                    statusMessage = "Room started! Ready for synchronized playback."
                                )
                            }
                            onGuestRoomStarted()
                        }
                        "PREPARE_TRACK" -> {
                            handleGuestPrepareTrack(json, writer)
                        }
                        "FILE_START" -> {
                            handleGuestFileStart(json)
                        }
                        "FILE_CHUNK" -> {
                            handleGuestFileChunk(json)
                        }
                        "FILE_END" -> {
                            handleGuestFileEnd(json, writer)
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

                                    _roomState.update {
                                        it.copy(
                                            phase = RoomPhase.PLAYING,
                                            isTransferringTrack = false,
                                            transferTrackTitle = null,
                                            currentSyncedTrackTitle = cmd.title,
                                            statusMessage = "Sync playing: ${cmd.title ?: "Track"}"
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
                Log.d(TAG, "Host stream ended: ${e.message}")
            } finally {
                closeIncomingFileSafely(deletePartial = true)
                if (_roomState.value.role == RoomRole.GUEST && guestSocket == socket) {
                    closeInternalSockets(resetState = true)
                    onRoomEnded("Disconnected from Bluetooth Room")
                }
            }
        }
    }

    private fun handleGuestPrepareTrack(json: JSONObject, writer: BufferedWriter) {
        val transferId = json.optString("transferId")
        val title = json.optString("title", "Shared Song")
        val fileHash = json.optString("fileHash")
        val fileSize = json.optLong("fileSize", 0L)
        val fileExt = json.optString("fileExt", "mp3").ifBlank { "mp3" }
        val hasBinary = json.optBoolean("hasBinary", false)
        val sourceUrl = json.optString("sourceUrl")

        if (hasBinary && fileHash.isNotBlank() && fileSize > 0L) {
            val cachedFile = File(syncCacheDir, "$fileHash.$fileExt")
            val existingCachedPath = syncedFilePaths[fileHash]
                ?: if (cachedFile.exists() && cachedFile.length() == fileSize) cachedFile.absolutePath else null
                ?: onFindLocalFileByHash(fileHash, fileSize, title)

            if (existingCachedPath != null) {
                syncedFilePaths[fileHash] = existingCachedPath
                sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
            } else {
                _roomState.update {
                    it.copy(
                        isTransferringTrack = true,
                        transferProgress = 0f,
                        transferTrackTitle = title,
                        statusMessage = "Receiving '$title' from host..."
                    )
                }
                sendGuestLineAsync(writer, RoomProtocol.encodeNeedFile(transferId, fileHash))
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
                        transferProgress = 0.25f,
                        transferTrackTitle = title,
                        statusMessage = "Downloading '$title'..."
                    )
                }
                scope.launch {
                    downloadRemoteUrlToCache(sourceUrl, cachedUrlFile)?.let {
                        syncedFilePaths[urlKey] = it
                    }
                    _roomState.update { it.copy(isTransferringTrack = false) }
                    sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
                }
            }
        } else {
            sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
        }
    }

    private fun handleGuestFileStart(json: JSONObject) {
        closeIncomingFileSafely(deletePartial = true)
        val transferId = json.optString("transferId")
        val fileHash = json.optString("fileHash")
        val fileSize = json.optLong("fileSize", 0L)
        val fileExt = json.optString("fileExt", "mp3").ifBlank { "mp3" }
        val title = json.optString("title", "Song")

        val finalFile = File(syncCacheDir, "$fileHash.$fileExt")
        val partFile = File(syncCacheDir, "$fileHash.$fileExt.part")
        if (partFile.exists()) partFile.delete()

        incomingFile = IncomingFileState(
            transferId = transferId,
            fileHash = fileHash,
            fileExt = fileExt,
            expectedSize = fileSize,
            title = title,
            partFile = partFile,
            finalFile = finalFile,
            output = BufferedOutputStream(FileOutputStream(partFile), 32768)
        )
        _roomState.update {
            it.copy(
                isTransferringTrack = true,
                transferProgress = 0f,
                transferTrackTitle = title,
                statusMessage = "Receiving '$title' (0%)..."
            )
        }
    }

    private fun handleGuestFileChunk(json: JSONObject) {
        val active = incomingFile ?: return
        val transferId = json.optString("transferId")
        if (transferId != active.transferId) return

        val encoded = json.optString("data")
        if (encoded.isEmpty()) return

        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        active.output.write(bytes)
        active.bytesWritten += bytes.size

        if (active.expectedSize > 0L) {
            val pct = (active.bytesWritten.toFloat() / active.expectedSize.toFloat()).coerceIn(0f, 0.99f)
            _roomState.update {
                it.copy(
                    isTransferringTrack = true,
                    transferProgress = pct,
                    transferTrackTitle = active.title,
                    statusMessage = "Receiving '${active.title}' (${(pct * 100).toInt()}%)..."
                )
            }
        }
    }

    private fun handleGuestFileEnd(json: JSONObject, writer: BufferedWriter) {
        val transferId = json.optString("transferId")
        val fileHash = json.optString("fileHash")
        val active = incomingFile
        if (active != null && active.transferId == transferId) {
            try {
                active.output.flush()
                active.output.close()
                if (active.partFile.exists() && active.partFile.length() > 512L) {
                    if (active.finalFile.exists()) active.finalFile.delete()
                    if (active.partFile.renameTo(active.finalFile)) {
                        syncedFilePaths[active.fileHash] = active.finalFile.absolutePath
                    } else {
                        syncedFilePaths[active.fileHash] = active.partFile.absolutePath
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error finalizing received song: ${e.message}")
            } finally {
                incomingFile = null
            }
        }
        _roomState.update {
            it.copy(
                isTransferringTrack = false,
                transferProgress = 1f,
                transferTrackTitle = null,
                statusMessage = "Song synced! Starting playback..."
            )
        }
        sendGuestLineAsync(writer, RoomProtocol.encodeTrackReady(transferId, fileHash))
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

    private fun downloadRemoteUrlToCache(url: String, targetFile: File): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 20000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 512L) targetFile.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    private fun closeIncomingFileSafely(deletePartial: Boolean) {
        incomingFile?.let { active ->
            try {
                active.output.close()
            } catch (_: Exception) {}
            if (deletePartial && active.partFile.exists()) {
                try {
                    active.partFile.delete()
                } catch (_: Exception) {}
            }
        }
        incomingFile = null
    }

    private fun registerDiscoveryReceiverSafely(receiver: BroadcastReceiver) {
        unregisterDiscoveryReceiverSafely()
        discoveryReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterDiscoveryReceiverSafely() {
        discoveryReceiver?.let { rec ->
            try {
                appContext.unregisterReceiver(rec)
            } catch (_: Exception) {}
        }
        discoveryReceiver = null
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

    @SuppressLint("MissingPermission")
    private fun closeInternalSockets(resetState: Boolean) {
        prepareTrackJob?.cancel()
        prepareTrackJob = null
        activeTransferId = null
        clientPrepSignals.clear()
        clientReadyAfterTransferSignals.clear()
        closeIncomingFileSafely(deletePartial = true)

        unregisterDiscoveryReceiverSafely()
        try {
            if (hasBluetoothPermissions(appContext) && bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } catch (_: Exception) {}

        hostAcceptJob?.cancel()
        hostAcceptJob = null
        guestJoinJob?.cancel()
        guestJoinJob = null

        hostClients.values.forEach { client ->
            try {
                client.socket.close()
            } catch (_: Exception) {}
        }
        hostClients.clear()

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

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
