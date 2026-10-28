package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CreatePlaylistDialog(
    availableTracks: List<Track>,
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, emoji: String, trackIds: List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("⚡") }
    val selectedTrackIds = remember { mutableStateListOf<String>() }

    val emojis = listOf("⚡", "🎧", "🌌", "🌧️", "💜", "🔥", "🚀", "✨", "🎵")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141522),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Create Playlist",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Choose Cover Emoji", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emojis.forEach { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) ElectricCyan.copy(alpha = 0.2f) else Color(0xFF1F2030))
                                .border(1.dp, if (isSelected) ElectricCyan else Color.Transparent, CircleShape)
                                .clickable { selectedEmoji = emoji }
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Playlist Title", color = TextSecondary) },
                    placeholder = { Text("e.g. Midnight Cyber Run", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF2B2D42)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = TextSecondary) },
                    placeholder = { Text("Curated mood for friends", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF2B2D42)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "Select Starting Tracks (${selectedTrackIds.size})", fontSize = 12.sp, color = SoftViolet)
                Spacer(modifier = Modifier.height(6.dp))

                availableTracks.take(4).forEach { track ->
                    val isChecked = selectedTrackIds.contains(track.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) selectedTrackIds.remove(track.id) else selectedTrackIds.add(track.id)
                            }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                if (it) selectedTrackIds.add(track.id) else selectedTrackIds.remove(track.id)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = ElectricCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "${track.title} - ${track.artist}", fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title.ifBlank { "My Vibe Mix" },
                        description.ifBlank { "Curated with friends" },
                        selectedEmoji,
                        selectedTrackIds.toList()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBlack),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_create_playlist")
            ) {
                Text(text = "Create & Share", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ImportCodeDialog(
    onDismiss: () -> Unit,
    onImport: (code: String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141522),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Enter Playlist Share Code",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste a friend's share code (e.g., CYBER-99, CODE-404, or VIBE-892) to import their curated mix instantly.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Share Code", color = TextSecondary) },
                    placeholder = { Text("e.g. CYBER-99", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ElectricCyan,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF2B2D42)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_code_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("CYBER-99", "CODE-404", "MOTION-88").forEach { suggestion ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E2032),
                            modifier = Modifier
                                .clickable { code = suggestion }
                                .padding(2.dp)
                        ) {
                            Text(
                                text = suggestion,
                                fontSize = 10.sp,
                                color = SoftViolet,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (code.isNotBlank()) onImport(code.trim()) },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBlack),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_import_code")
            ) {
                Text(text = "Import Mix", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = TextSecondary)
            }
        }
    )
}
