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
 * High-definition YouTube Play Button Logo.
 * Crisp red rounded badge with white centered play triangle.
 */
@Composable
fun YouTubeLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color(0xFFFF0000)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.45f)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height / 2f)
                lineTo(0f, this@Canvas.size.height)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
    }
}

/**
 * Modern Google & Web Link Icon.
 * Multi-colored badge with Google Blue accent.
 */
@Composable
fun GoogleWebIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE0E0E0), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.65f)) {
            val strokeW = this.size.width * 0.12f
            val radius = (this.size.width - strokeW) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Draw Google 4-color segmented ring
            // Red (top)
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = 200f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW)
            )
            // Yellow (bottom-left)
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 120f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW)
            )
            // Green (bottom)
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 30f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW)
            )
            // Blue (right & crossbar)
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = 300f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeW)
            )
            // Crossbar
            drawLine(
                color = Color(0xFF4285F4),
                start = Offset(center.x, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = strokeW
            )
        }
    }
}

/**
 * Universal Downloader Dialog:
 * - Downloads from TikTok, YouTube, and Google / External Web links
 * - Saves directly to phone's public storage (/Music/ASDMusic/)
 * - Toggle Audio (Compressed HQ MP3) vs Video (HD MP4)
 * - Auto-detects titles, artists, and cover art
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

    val clean = urlText.trim()
    val isYouTube = clean.contains("youtube.com") || clean.contains("youtu.be")
    val isTikTok = clean.contains("tiktok.com")
    val isWebOrGoogle = clean.startsWith("http://") || clean.startsWith("https://")
    val isValidLink = clean.isNotBlank() && (isYouTube || isTikTok || isWebOrGoogle)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TikTokLogoIcon(size = 24.dp)
                    YouTubeLogoIcon(size = 24.dp)
                    GoogleWebIcon(size = 24.dp)
                }
                Column {
                    Text(
                        text = "Download to Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "TikTok • YouTube • Google / Web Links",
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
                    text = "Paste any TikTok, YouTube, or Google/web link. Files are saved directly to your phone's Music storage so they never disappear and play with all apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Link Input field with dynamic leading icon
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Song or Video Link") },
                    placeholder = { Text("TikTok, YouTube, or Web URL") },
                    leadingIcon = {
                        when {
                            isYouTube -> YouTubeLogoIcon(size = 20.dp)
                            isTikTok -> TikTokLogoIcon(size = 20.dp)
                            else -> GoogleWebIcon(size = 20.dp)
                        }
                    },
                    singleLine = true,
                    isError = urlText.isNotBlank() && !isValidLink,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlusBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_link_input")
                )

                if (urlText.isNotBlank() && !isValidLink) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please enter a valid TikTok, YouTube, or Web media link.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick One-Tap Sample Chips for TikTok, YouTube, and Google Web
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("TikTok", "https://www.tiktok.com/@scout2015/video/6718335390845095173", "tt"),
                        Triple("YouTube", "https://www.youtube.com/watch?v=kJQP7kiw5Fk", "yt"),
                        Triple("Web Audio", "https://filesamples.com/samples/audio/mp3/sample3.mp3", "web")
                    ).forEach { (label, sampleUrl, type) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { urlText = sampleUrl }
                                .border(0.5.dp, PlusBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                when (type) {
                                    "tt" -> TikTokLogoIcon(size = 14.dp)
                                    "yt" -> YouTubeLogoIcon(size = 14.dp)
                                    else -> GoogleWebIcon(size = 14.dp)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Song Title (Optional)
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Song Title (Optional)") },
                    placeholder = { Text("Auto-detect from link") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlusBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Artist Name (Optional)
                OutlinedTextField(
                    value = artistText,
                    onValueChange = { artistText = it },
                    label = { Text("Artist / Channel (Optional)") },
                    placeholder = { Text("Auto-detect from link") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlusBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Format switch: Audio (HQ Compressed MP3) vs Video (HD MP4)
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
                                text = if (isVideo) "Video (HD MP4)" else "Audio (HQ Compressed MP3)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isVideo) "Includes video stream" else "Compressed audio track",
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

                Spacer(modifier = Modifier.height(10.dp))

                // Reassuring Device Storage Banner per user request
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PlusBlue.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Download,
                            contentDescription = null,
                            tint = PlusBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Saves to Device (/Music/ASDMusic/) — available in all music players.",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                enabled = isValidLink,
                colors = ButtonDefaults.buttonColors(containerColor = PlusBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_download_button")
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Download to Device", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
