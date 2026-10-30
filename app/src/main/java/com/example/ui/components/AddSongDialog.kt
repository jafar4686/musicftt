package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusBlue
import com.example.ui.theme.PlusCyan
import com.example.ui.theme.PlusViolet

/**
 * High-definition TikTok Musical Note Logo.
 * Accurately rendered with TikTok's trademark Cyan and Pink/Magenta neon 3D displacement layers.
 */
@Composable
fun TikTokLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF010101)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.75f)) {
            val w = this.size.width
            val h = this.size.height

            // Cyan chromatic aberration layer
            drawTikTokNote(
                offset = Offset(-w * 0.04f, h * 0.03f),
                color = Color(0xFF25F4EE),
                width = w,
                height = h
            )

            // Magenta/Red chromatic aberration layer
            drawTikTokNote(
                offset = Offset(w * 0.04f, -h * 0.03f),
                color = Color(0xFFFE2C55),
                width = w,
                height = h
            )

            // Primary crisp white note layer
            drawTikTokNote(
                offset = Offset.Zero,
                color = Color.White,
                width = w,
                height = h
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTikTokNote(
    offset: Offset,
    color: Color,
    width: Float,
    height: Float
) {
    val strokeW = width * 0.16f

    // Note stem
    val stemX = width * 0.60f + offset.x
    val stemTopY = height * 0.12f + offset.y
    val stemBottomY = height * 0.66f + offset.y

    drawLine(
        color = color,
        start = Offset(stemX, stemTopY),
        end = Offset(stemX, stemBottomY),
        strokeWidth = strokeW
    )

    // Note Flag / Head Curve at top
    val flagPath = Path().apply {
        moveTo(stemX, height * 0.36f + offset.y)
        cubicTo(
            stemX + width * 0.22f, height * 0.36f + offset.y,
            width * 0.90f + offset.x, height * 0.24f + offset.y,
            width * 0.92f + offset.x, stemTopY
        )
    }
    drawPath(
        path = flagPath,
        color = color,
        style = Stroke(width = strokeW)
    )

    // Bottom note circle
    val circleCenter = Offset(stemX - width * 0.22f, stemBottomY)
    drawCircle(
        color = color,
        radius = width * 0.19f,
        center = circleCenter
    )
}

/**
 * Dedicated TikTok Song Downloader Dialog:
 * - Prominent TikTok logo
 * - Downloads full TikTok songs and audio
 * - Automatic title/artist detection if empty
 * - Strictly validates TikTok links
 */
@Composable
fun AddSongDialog(
    onDismiss: () -> Unit,
    onDownloadAndPlay: (url: String, title: String?, artist: String?, isVideo: Boolean) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var artistText by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(false) }

    val isTikTokLink = urlText.isBlank() || urlText.contains("tiktok", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TikTokLogoIcon(size = 34.dp)
                Column {
                    Text(
                        text = "Download TikTok Song",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "yt-dlp TikTok Engine",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PlusBlue
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste any TikTok video, sound, or music link to download the full song directly to your device library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Link Input field with TikTok Logo leading icon
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("TikTok Link URL") },
                    placeholder = { Text("https://vm.tiktok.com/... or tiktok.com/@user/...") },
                    leadingIcon = {
                        TikTokLogoIcon(size = 20.dp)
                    },
                    singleLine = true,
                    isError = urlText.isNotBlank() && !isTikTokLink,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlusBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_link_input")
                )

                if (urlText.isNotBlank() && !isTikTokLink) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please enter a valid TikTok song link.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick TikTok Sample Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Viral Pet Sound" to "https://www.tiktok.com/@scout2015/video/6718335390845095173",
                        "TikTok Sound" to "https://www.tiktok.com/@scout2015/video/6718335390845095173"
                    ).forEach { (label, sampleUrl) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { urlText = sampleUrl }
                                .border(0.5.dp, PlusBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TikTokLogoIcon(size = 14.dp)
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Song Title (Optional)
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Song Title (Optional)") },
                    placeholder = { Text("Auto-detect from TikTok") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlusBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Artist Name (Optional)
                OutlinedTextField(
                    value = artistText,
                    onValueChange = { artistText = it },
                    label = { Text("Artist / Creator (Optional)") },
                    placeholder = { Text("Auto-detect from TikTok") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlusBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Format switch: Full MP3 Audio or MP4 Video
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = PlusBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isVideo) "Download Full Video" else "Download Full Audio (MP3)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isVideo) "Includes video stream" else "HQ TikTok sound track",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isVideo,
                        onCheckedChange = { isVideo = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PlusBlue
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
                enabled = urlText.isNotBlank() && isTikTokLink,
                colors = ButtonDefaults.buttonColors(containerColor = PlusBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_download_button")
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Download TikTok Song", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
