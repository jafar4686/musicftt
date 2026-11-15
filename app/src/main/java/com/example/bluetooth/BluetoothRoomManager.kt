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
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.BluetoothRoomState
import com.example.model.RoomMember
import com.example.model.RoomPhase
import com.example.model.RoomRole
import com.example.model.RoomSyncCommand
import com.example.model.Track
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
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class BluetoothRoomManager(
    private val context: Context,
    private val onGuestSyncCommand: (RoomSyncCommand) -> Unit = {},
    private val onGuestRoomStarted: () -> Unit = {},
    private val onRoomEnded: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "BluetoothRoomManager"
        const val ROOM_SERVICE_NAME = "ASDMusicRoom"
        val ROOM_UUID: UUID = UUID.fromString("8a9d5c10-4b22-4f88-9c31-6e7b31a5d480")

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

    object RoomProtocol {
        fun encodeJoin(code: String, memberName: String, memberId: String): String {
            return JSONObject().apply {
                put("type", "JOIN")
                put("code", code)
                put("name", memberName)
                put("id", memberId)
            }.toString()
        }

        fun encodeWelcome(code: String, phase: RoomPhase, members: List<RoomMember>): String {
            return JSONObject().apply {
                put("type", "WELCOME")
                put("code", code)
                put("phase", phase.name)
                put("members", encodeMembersArray(members))
            }.toString()
        }

        fun encodeReject(reason: String): String {
            return JSONObject().apply {
                put("type", "REJECT")
                put("reason", reason)
            }.toString()
        }

        fun encodeMembers(members: List<RoomMember>): String {
            return JSONObject().apply {
                put("type", "MEMBERS")
                put("members", encodeMembersArray(members))
            }.toString()
        }

        fun encodeStartRoom(): String {
            return JSONObject().apply {
                put("type", "START_ROOM")
            }.toString()
        }

        fun encodeSyncPlay(
            track: Track,
            positionMs: Long,
            speed: Float,
            isPlaying: Boolean,
            syncDelayMs: Long = 220L
        ): String {
            return JSONObject().apply {
                put("type", "SYNC_PLAY")
                put("trackId", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("durationSeconds", track.durationSeconds)
                put("sourceUrl", track.sourceUrl ?: "")
                put("positionMs", positionMs.coerceAtLeast(0L))
                put("speed", speed.toDouble())
                put("isPlaying", isPlaying)
                put("syncDelayMs", syncDelayMs)
            }.toString()
        }

        fun encodeSyncPause(positionMs: Long): String {
            return JSONObject().apply {
                put("type", "SYNC_PAUSE")
                put("positionMs", positionMs.coerceAtLeast(0L))
            }.toString()
        }

        fun encodeSyncSeek(positionMs: Long, isPlaying: Boolean): String {
            return JSONObject().apply {
                put("type", "SYNC_SEEK")
                put("positionMs", positionMs.coerceAtLeast(0L))
                put("isPlaying", isPlaying)
            }.toString()
        }

        fun encodeRoomClosed(reason: String): String {
            return JSONObject().apply {
                put("type", "ROOM_CLOSED")
                put("reason", reason)
            }.toString()
        }

        fun encodeMembersArray(members: List<RoomMember>): JSONArray {
            val arr = JSONArray()
            members.forEach { m ->
                arr.put(
                    JSONObject().apply {
                        put("id", m.id)
                        put("name", m.name)
                        put("isHost", m.isHost)
                        if (m.address != null) put("address", m.address)
                    }
                )
            }
            return arr
        }

        fun parseMembersArray(arr: JSONArray?): List<RoomMember> {
            if (arr == null) return emptyList()
            val result = mutableListOf<RoomMember>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(
                    RoomMember(
                        id = obj.optString("id", "member_$i"),
                        name = obj.optString("name", "Device ${i + 1}"),
                        isHost = obj.optBoolean("isHost", false),
                        address = obj.optString("address").ifBlank { null }
                    )
                )
            }
            return result
        }

        fun parseSyncCommand(json: JSONObject): RoomSyncCommand? {
            return when (val type = json.optString("type")) {
                "SYNC_PLAY" -> RoomSyncCommand(
                    action = type,
                    trackId = json.optString("trackId").ifBlank { null },
                    title = json.optString("title").ifBlank { null },
                    artist = json.optString("artist").ifBlank { null },
                    album = json.optString("album").ifBlank { null },
                    durationSeconds = json.optInt("durationSeconds", 180).coerceAtLeast(10),
                    sourceUrl = json.optString("sourceUrl").ifBlank { null },
                    positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
                    speed = json.optDouble("speed", 1.0).toFloat().coerceIn(0.5f, 2.0f),
                    isPlaying = json.optBoolean("isPlaying", true),
                    syncDelayMs = json.optLong("syncDelayMs", 0L).coerceAtLeast(0L)
                )
                "SYNC_PAUSE" -> RoomSyncCommand(
                    action = type,
                    positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
                    isPlaying = false
                )
                "SYNC_SEEK" -> RoomSyncCommand(
                    action = type,
                    positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
                    isPlaying = json.optBoolean("isPlaying", true)
                )
                else -> null
            }
        }
    }

    private data class ClientSession(
        val member: RoomMember,
        val socket: BluetoothSocket,
        val writer: BufferedWriter
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localMemberId = "dev_${Build.MODEL.hashCode()}_${Random.nextInt(100, 999)}"

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
    private val hostClients = ConcurrentHashMap<String, ClientSession>()
    private var lastSyncedPlayCommand: RoomSyncCommand? = null

    private var guestSocket: BluetoothSocket? = null
    private var guestJoinJob: Job? = null
    private var discoveryReceiver: BroadcastReceiver? = null

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
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

            val handshakeLine = withTimeoutOrNull(8_000L) {
                reader.readLine()
            }

            if (handshakeLine.isNullOrBlank()) {
                socket.close()
                return
            }

            val json = JSONObject(handshakeLine)
            if (json.optString("type") != "JOIN") {
                writer.write(RoomProtocol.encodeReject("Unexpected handshake"))
                writer.newLine()
                writer.flush()
                socket.close()
                return
            }

            val enteredCode = json.optString("code").trim()
            if (enteredCode != expectedCode) {
                writer.write(RoomProtocol.encodeReject("Wrong 5-digit room code"))
                writer.newLine()
                writer.flush()
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

            // Send welcome + current member roster to this guest
            val currentPhase = _roomState.value.phase
            writer.write(RoomProtocol.encodeWelcome(expectedCode, currentPhase, updatedMembers))
            writer.newLine()
            writer.flush()

            // If host already started the room and is playing a track, sync late joiner too
            if (currentPhase == RoomPhase.PLAYING) {
                lastSyncedPlayCommand?.let { lastCmd ->
                    val lateJson = JSONObject().apply {
                        put("type", "SYNC_PLAY")
                        put("trackId", lastCmd.trackId ?: "")
                        put("title", lastCmd.title ?: "Shared Song")
                        put("artist", lastCmd.artist ?: "Host")
                        put("album", lastCmd.album ?: "ASD MUSIC")
                        put("durationSeconds", lastCmd.durationSeconds)
                        put("sourceUrl", lastCmd.sourceUrl ?: "")
                        put("positionMs", lastCmd.positionMs)
                        put("speed", lastCmd.speed.toDouble())
                        put("isPlaying", lastCmd.isPlaying)
                        put("syncDelayMs", 0L)
                    }.toString()
                    writer.write(lateJson)
                    writer.newLine()
                    writer.flush()
                }
            }

            // Broadcast updated member list to every connected guest
            broadcastLineToGuests(RoomProtocol.encodeMembers(updatedMembers), excludeId = guestId)

            // Keep reading until guest disconnects
            while (scope.isActive && _roomState.value.role == RoomRole.HOST) {
                val line = reader.readLine() ?: break
                Log.d(TAG, "Message from guest $guestName: $line")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Guest connection closed: ${e.message}")
        } finally {
            if (assignedClientId != null) {
                hostClients.remove(assignedClientId)
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

    private fun buildCurrentHostMemberList(hostMember: RoomMember): List<RoomMember> {
        return listOf(hostMember) + hostClients.values.map { it.member }
    }

    private fun broadcastLineToGuests(line: String, excludeId: String? = null) {
        scope.launch {
            hostClients.forEach { (id, client) ->
                if (id != excludeId) {
                    try {
                        synchronized(client.writer) {
                            client.writer.write(line)
                            client.writer.newLine()
                            client.writer.flush()
                        }
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

    fun broadcastSyncPlay(
        track: Track,
        positionMs: Long = 0L,
        speed: Float = 1.0f,
        isPlaying: Boolean = true,
        syncDelayMs: Long = 220L
    ) {
        val state = _roomState.value
        if (state.role != RoomRole.HOST || state.phase != RoomPhase.PLAYING) return
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
            syncDelayMs = syncDelayMs
        )
        _roomState.update {
            it.copy(
                currentSyncedTrackTitle = track.title,
                statusMessage = "Playing '${track.title}' across ${it.members.size} devices"
            )
        }
        broadcastLineToGuests(RoomProtocol.encodeSyncPlay(track, positionMs, speed, isPlaying, syncDelayMs))
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

            // Phase 1: Probe already paired/bonded Bluetooth devices immediately
            for (device in pairedDevices) {
                if (!isActive || _roomState.value.role != RoomRole.GUEST) return@launch
                val addr = device.address ?: continue
                attemptedAddresses.add(addr)
                if (tryConnectToHostDevice(adapter, device, cleanCode)) {
                    return@launch
                }
            }

            // Phase 2: Scan nearby discoverable Bluetooth devices & probe as discovered
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

            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

            val myName = getLocalDeviceDisplayName()
            writer.write(RoomProtocol.encodeJoin(roomCode, myName, localMemberId))
            writer.newLine()
            writer.flush()

            val responseLine = withTimeoutOrNull(4_000L) { reader.readLine() }
            if (responseLine.isNullOrBlank()) {
                socket.close()
                return false
            }

            val respJson = JSONObject(responseLine)
            when (respJson.optString("type")) {
                "WELCOME" -> {
                    guestSocket = socket
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

                    listenToHostMessages(socket, reader)
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

    private fun listenToHostMessages(socket: BluetoothSocket, reader: BufferedReader) {
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
                        "SYNC_PLAY", "SYNC_PAUSE", "SYNC_SEEK" -> {
                            val cmd = RoomProtocol.parseSyncCommand(json)
                            if (cmd != null) {
                                if (cmd.action == "SYNC_PLAY") {
                                    _roomState.update {
                                        it.copy(
                                            phase = RoomPhase.PLAYING,
                                            currentSyncedTrackTitle = cmd.title,
                                            statusMessage = "Sync playing: ${cmd.title ?: "Track"}"
                                        )
                                    }
                                }
                                onGuestSyncCommand(cmd)
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
                if (_roomState.value.role == RoomRole.GUEST && guestSocket == socket) {
                    closeInternalSockets(resetState = true)
                    onRoomEnded("Disconnected from Bluetooth Room")
                }
            }
        }
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
                    client.writer.write(closeMsg)
                    client.writer.newLine()
                    client.writer.flush()
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

        lastSyncedPlayCommand = null

        if (resetState) {
            _roomState.update {
                it.copy(
                    role = RoomRole.NONE,
                    phase = RoomPhase.IDLE,
                    roomCode = "",
                    members = emptyList(),
                    isConnecting = false,
                    isDiscovering = false,
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
