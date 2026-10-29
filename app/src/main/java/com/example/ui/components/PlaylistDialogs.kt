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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Track
import com.example.ui.theme.BorderWarm
import com.example.ui.theme.CaramelGold
import com.example.ui.theme.DeepWarmBrown
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.TextCream
import com.example.ui.theme.TextMutedSand
import com.example.ui.theme.WarmCardSurface

@Composable
fun CreatePlaylistDialog(
    availableTracks: List<Track>,
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, emoji: String, trackIds: List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🎵") }
    val selectedTrackIds = remember { mutableStateListOf<String>() }

    val emojis = listOf("🎵", "📱", "🎧", "☕", "📻", "✨")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepWarmBrown,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "New Playlist",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextCream
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Choose Icon", fontSize = 12.sp, color = TextMutedSand)
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
                                .background(if (isSelected) CaramelGold.copy(alpha = 0.2f) else WarmCardSurface)
                                .border(1.dp, if (isSelected) CaramelGold else BorderWarm, CircleShape)
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
                    label = { Text("Playlist Name", color = TextMutedSand) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextCream,
                        unfocusedTextColor = TextCream,
                        focusedBorderColor = CaramelGold,
                        unfocusedBorderColor = BorderWarm
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = TextMutedSand) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextCream,
                        unfocusedTextColor = TextCream,
                        focusedBorderColor = CaramelGold,
                        unfocusedBorderColor = BorderWarm
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title.ifBlank { "My Mix" },
                        description.ifBlank { "Device playlist" },
                        selectedEmoji,
                        selectedTrackIds.toList()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CaramelGold, contentColor = EspressoDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = TextMutedSand)
            }
        }
    )
}
