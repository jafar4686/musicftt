package com.example.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.BluetoothRoomManager
import com.example.model.BluetoothRoomState
import com.example.model.RoomMember
import com.example.model.RoomPhase
import com.example.model.RoomRole
import com.example.model.Track
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.PlusBlue
import com.example.ui.theme.PlusViolet

private enum class PendingRoomAction {
    NONE, CREATE, JOIN
}

@Composable
fun BluetoothRoomCard(
    roomState: BluetoothRoomState,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onRefreshBluetoothStatus: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onStartRoom: () -> Unit,
    onPlayTrackInRoom: (Track) -> Unit,
    onLeaveRoom: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var roomCodeInput by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf(PendingRoomAction.NONE) }
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshBluetoothStatus()
    }

    val discoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshBluetoothStatus()
    }

    fun requestDiscoverableIfPermitted() {
        if (BluetoothRoomManager.hasBluetoothPermissions(context)) {
            try {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
                discoverableLauncher.launch(intent)
            } catch (_: Exception) {}
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshBluetoothStatus()
        if (BluetoothRoomManager.hasBluetoothPermissions(context)) {
            when (pendingAction) {
                PendingRoomAction.CREATE -> {
                    pendingAction = PendingRoomAction.NONE
                    onCreateRoom()
                    requestDiscoverableIfPermitted()
                }
                PendingRoomAction.JOIN -> {
                    pendingAction = PendingRoomAction.NONE
                    if (roomCodeInput.length == 5) {
                        onJoinRoom(roomCodeInput)
                    }
                }
                PendingRoomAction.NONE -> Unit
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onRefreshBluetoothStatus()
        if (BluetoothRoomManager.hasBluetoothPermissions(context)) {
            val isBtOn = roomState.isBluetoothEnabled || try {
                val bm = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                bm?.adapter?.isEnabled == true
            } catch (_: Exception) {
                false
            }
            if (!isBtOn) {
                try {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (_: Exception) {
                    pendingAction = PendingRoomAction.NONE
                }
            } else {
                when (pendingAction) {
                    PendingRoomAction.CREATE -> {
                        pendingAction = PendingRoomAction.NONE
                        onCreateRoom()
                        requestDiscoverableIfPermitted()
                    }
                    PendingRoomAction.JOIN -> {
                        pendingAction = PendingRoomAction.NONE
                        if (roomCodeInput.length == 5) {
                            onJoinRoom(roomCodeInput)
                        }
                    }
                    PendingRoomAction.NONE -> Unit
                }
            }
        } else {
            pendingAction = PendingRoomAction.NONE
        }
    }

    fun ensureBluetoothAndRun(action: PendingRoomAction) {
        onClearError()
        onRefreshBluetoothStatus()
        pendingAction = action
        if (!BluetoothRoomManager.hasBluetoothPermissions(context)) {
            permissionLauncher.launch(BluetoothRoomManager.requiredBluetoothPermissions())
            return
        }
        if (!roomState.isBluetoothEnabled) {
            try {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: Exception) {
                pendingAction = PendingRoomAction.NONE
            }
            return
        }
        pendingAction = PendingRoomAction.NONE
        when (action) {
            PendingRoomAction.CREATE -> {
                onCreateRoom()
                requestDiscoverableIfPermitted()
            }
            PendingRoomAction.JOIN -> {
                onJoinRoom(roomCodeInput)
            }
            PendingRoomAction.NONE -> Unit
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (roomState.phase != RoomPhase.IDLE) {
                    PlusBlue.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                },
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("bluetooth_room_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (roomState.phase != RoomPhase.IDLE) PlusBlue.copy(alpha = 0.16f)
                                else PlusViolet.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (roomState.phase != RoomPhase.IDLE) {
                                Icons.Default.BluetoothConnected
                            } else {
                                Icons.Default.Bluetooth
                            },
                            contentDescription = "Bluetooth Multi-Device Room",
                            tint = if (roomState.phase != RoomPhase.IDLE) PlusBlue else PlusViolet,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Listen Together Room",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Play the same song simultaneously via Bluetooth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Live Bluetooth Status Pill
                val statusLabel = when {
                    !roomState.isBluetoothSupported -> "No BT"
                    !roomState.hasBluetoothPermission -> "Permit"
                    !roomState.isBluetoothEnabled -> "BT Off"
                    roomState.phase == RoomPhase.PLAYING -> "Live Sync"
                    roomState.phase == RoomPhase.LOBBY -> "Lobby"
                    else -> "BT Ready"
                }
                val statusColor = when {
                    !roomState.isBluetoothSupported || !roomState.hasBluetoothPermission || !roomState.isBluetoothEnabled -> MaterialTheme.colorScheme.error
                    roomState.phase == RoomPhase.PLAYING -> AccentGreen
                    else -> PlusBlue
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            // Bluetooth Permission / Adapter Helper Banner (when idle & Bluetooth not yet ready)
            if (roomState.phase == RoomPhase.IDLE &&
                roomState.isBluetoothSupported &&
                (!roomState.hasBluetoothPermission || !roomState.isBluetoothEnabled)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PlusBlue.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PlusBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (!roomState.hasBluetoothPermission) {
                                "Allow Bluetooth so devices can connect"
                            } else {
                                "Enable Bluetooth on this device"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { ensureBluetoothAndRun(PendingRoomAction.NONE) },
                            colors = ButtonDefaults.buttonColors(containerColor = PlusBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("enable_bluetooth_button")
                        ) {
                            Text(
                                text = if (!roomState.hasBluetoothPermission) "Allow BT" else "Turn On",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Error message banner
            AnimatedVisibility(visible = !roomState.errorMessage.isNullOrBlank()) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = roomState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onClearError,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (roomState.phase == RoomPhase.IDLE) {
                // --- CREATE ROOM OR JOIN BY 5-DIGIT CODE ---
                Button(
                    onClick = { ensureBluetoothAndRun(PendingRoomAction.CREATE) },
                    enabled = roomState.isBluetoothSupported && !roomState.isConnecting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PlusBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("create_room_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RadioButtonChecked,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create Room (Generate 5-Digit Code)",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Text(
                        text = "  OR JOIN ROOM  ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(5)
                            roomCodeInput = digits
                            if (!roomState.errorMessage.isNullOrBlank()) onClearError()
                        },
                        placeholder = {
                            Text(
                                text = "5-digit code",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        enabled = !roomState.isConnecting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PlusBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("room_code_input")
                    )

                    Button(
                        onClick = { ensureBluetoothAndRun(PendingRoomAction.JOIN) },
                        enabled = roomCodeInput.length == 5 && !roomState.isConnecting && roomState.isBluetoothSupported,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PlusViolet),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .height(54.dp)
                            .testTag("join_room_button")
                    ) {
                        if (roomState.isConnecting) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("Join", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (roomState.isConnecting) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = roomState.statusMessage ?: "Searching nearby Bluetooth devices...",
                            style = MaterialTheme.typography.bodySmall,
                            color = PlusBlue,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onLeaveRoom,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                // --- ACTIVE ROOM (LOBBY OR PLAYING) ---
                // 5-Digit Code Display Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PlusBlue.copy(alpha = 0.1f),
                    border = BorderStroke(1.5.dp, PlusBlue.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (roomState.role == RoomRole.HOST) PlusBlue else PlusViolet
                            ) {
                                Text(
                                    text = if (roomState.role == RoomRole.HOST) "HOST" else "GUEST",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            if (roomState.role == RoomRole.HOST) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { requestDiscoverableIfPermitted() }
                                ) {
                                    Text(
                                        text = "Make Discoverable",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = PlusBlue,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "ROOM CODE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.5.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = roomState.roomCode.toCharArray().joinToString(" "),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = PlusBlue,
                                modifier = Modifier.testTag("room_code_display")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(roomState.roomCode))
                                    copiedFeedback = true
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (copiedFeedback) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                                    contentDescription = "Copy room code",
                                    tint = if (copiedFeedback) AccentGreen else PlusBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        roomState.statusMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // People in Room Roster
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = PlusBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "People in Room (${roomState.members.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("room_members_count")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    roomState.members.forEach { member ->
                        RoomMemberRow(member = member)
                    }
                    if (roomState.role == RoomRole.HOST && roomState.members.size <= 1) {
                        Text(
                            text = "Ask 2–3 friends to open Settings on their phones, enable Bluetooth, enter ${roomState.roomCode}, and tap Join.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Host Start Room Button (in LOBBY) OR Song Chooser (in PLAYING)
                if (roomState.role == RoomRole.HOST) {
                    if (roomState.phase == RoomPhase.LOBBY) {
                        Button(
                            onClick = onStartRoom,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("start_room_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Start Room",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }
                    } else if (roomState.phase == RoomPhase.PLAYING) {
                        Text(
                            text = "Choose a Song to Play for Everyone",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            tracks.forEach { track ->
                                val isCurrentRoomTrack = currentTrack?.id == track.id
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCurrentRoomTrack) {
                                        PlusBlue.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    },
                                    border = if (isCurrentRoomTrack) {
                                        BorderStroke(1.2.dp, PlusBlue)
                                    } else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onPlayTrackInRoom(track) }
                                        .testTag("room_pick_track_${track.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrentRoomTrack && isPlaying) {
                                                Icons.Default.GraphicEq
                                            } else {
                                                Icons.Default.MusicNote
                                            },
                                            contentDescription = null,
                                            tint = if (isCurrentRoomTrack) PlusBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isCurrentRoomTrack) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isCurrentRoomTrack) PlusBlue else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (isCurrentRoomTrack && isPlaying) "Synced" else "Play All",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrentRoomTrack) AccentGreen else PlusBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Guest Room view
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (roomState.phase == RoomPhase.PLAYING) {
                            AccentGreen.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (roomState.phase == RoomPhase.PLAYING) AccentGreen else PlusBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (roomState.phase == RoomPhase.PLAYING) {
                                    "Synced Now: ${roomState.currentSyncedTrackTitle ?: currentTrack?.title ?: "Waiting for host song..."}"
                                } else {
                                    "Connected! Waiting for Host to tap Start Room..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onLeaveRoom,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("leave_room_button")
                ) {
                    Text(
                        text = if (roomState.role == RoomRole.HOST) "End Room" else "Leave Room",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomMemberRow(member: RoomMember) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (member.isHost) PlusBlue.copy(alpha = 0.16f) else PlusViolet.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (member.isHost) Icons.Default.Star else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (member.isHost) PlusBlue else PlusViolet,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = member.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (member.isHost) PlusBlue.copy(alpha = 0.14f) else AccentGreen.copy(alpha = 0.14f)
            ) {
                Text(
                    text = if (member.isHost) "Host" else "Joined",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (member.isHost) PlusBlue else AccentGreen,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
