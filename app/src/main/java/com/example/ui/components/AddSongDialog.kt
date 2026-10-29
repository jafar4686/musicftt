package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderWarm
import com.example.ui.theme.CaramelGold
import com.example.ui.theme.DeepWarmBrown
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.TextCream
import com.example.ui.theme.TextMutedSand
import com.example.ui.theme.WarmCardSurface

@Composable
fun AddSongDialog(
    onDismiss: () -> Unit,
    onDownloadAndPlay: (url: String, title: String?, artist: String?, isVideo: Boolean) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var artistText by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepWarmBrown,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = CaramelGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Song from Link",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCream
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste a TikTok or YouTube link, or direct audio link to download and play offline.",
                    fontSize = 12.sp,
                    color = TextMutedSand
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Link URL", color = TextMutedSand) },
                    placeholder = { Text("e.g. tiktok.com/@user/video/... or youtube.com/...", color = TextMutedSand) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextCream,
                        unfocusedTextColor = TextCream,
                        focusedBorderColor = CaramelGold,
                        unfocusedBorderColor = BorderWarm
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_link_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick sample links chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "TikTok Sound" to "https://tiktok.com/@vibe/sound/7829104",
                        "YouTube Mix" to "https://youtube.com/watch?v=chillhop_beats"
                    ).forEach { (label, sampleUrl) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WarmCardSurface,
                            modifier = Modifier
                                .clickable { urlText = sampleUrl }
                                .border(0.5.dp, BorderWarm, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                color = CaramelGold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Song Title (Optional)", color = TextMutedSand) },
                    placeholder = { Text("Leave empty for auto-detect", color = TextMutedSand) },
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
                    value = artistText,
                    onValueChange = { artistText = it },
                    label = { Text("Artist Name (Optional)", color = TextMutedSand) },
                    placeholder = { Text("Leave empty for auto-detect", color = TextMutedSand) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextCream,
                        unfocusedTextColor = TextCream,
                        focusedBorderColor = CaramelGold,
                        unfocusedBorderColor = BorderWarm
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarmCardSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "Download as Video", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextCream)
                            Text(text = if (isVideo) "Full MP4 video playback" else "MP3 audio file", fontSize = 10.sp, color = TextMutedSand)
                        }
                    }

                    Switch(
                        checked = isVideo,
                        onCheckedChange = { isVideo = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EspressoDark,
                            checkedTrackColor = CaramelGold,
                            uncheckedThumbColor = TextMutedSand,
                            uncheckedTrackColor = BorderWarm
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        onDownloadAndPlay(
                            urlText.trim(),
                            titleText.ifBlank { null },
                            artistText.ifBlank { null },
                            isVideo
                        )
                    }
                },
                enabled = urlText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CaramelGold,
                    contentColor = EspressoDark
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_download_button")
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Download & Play", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = TextMutedSand)
            }
        }
    )
}
