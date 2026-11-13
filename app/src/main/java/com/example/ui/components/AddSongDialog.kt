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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LinkDetector
import com.example.data.LinkSource
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
 * Universal Auto-Detect Downloader Dialog:
 * - Zero manual platform selection: automatically recognizes YouTube, TikTok, Google, or any Web link
 * - Auto-inspects clipboard on open and dynamically displays the recognized app's logo
 * - Toggle Audio (Compressed HQ) vs Video (HD MP4)
 * - Saves directly to phone's public Music storage (/Music/ASDMusic/)
 */
@Composable
fun AddSongDialog(
    onDismiss: () -> Unit,
    onDownloadAndPlay: (url: String, title: String?, artist: String?, isVideo: Boolean) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var urlText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var artistText by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(false) }

    // Automatically recognize copied link from clipboard if available (no selection needed)
    LaunchedEffect(Unit) {
        val clip = clipboardManager.getText()?.text?.trim() ?: ""
        if (clip.isNotBlank() && LinkDetector.isValidMediaInput(clip)) {
            urlText = clip
        }
    }

    val isValidLink = remember(urlText) { LinkDetector.isValidMediaInput(urlText) }
    val detectedSource = remember(urlText, isValidLink) {
        if (isValidLink) LinkDetector.detectSource(urlText) else null
    }
    val isDriveLink = remember(urlText) {
        LinkDetector.extractGoogleDriveFileId(LinkDetector.normalizeUrl(urlText)) != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dynamically displays the recognized app's logo, or clean download badge before a link is entered
                when (detectedSource) {
                    LinkSource.YOUTUBE -> YouTubeLogoIcon(size = 36.dp)
                    LinkSource.TIKTOK -> TikTokLogoIcon(size = 36.dp)
                    LinkSource.GOOGLE, LinkSource.WEB -> GoogleWebIcon(size = 36.dp)
                    null -> {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PlusBlue.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Auto Download",
                                tint = PlusBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Column {
                    Text(
                        text = "Download to Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (detectedSource) {
                            LinkSource.YOUTUBE -> "YouTube link recognized automatically"
                            LinkSource.TIKTOK -> "TikTok link recognized automatically"
                            LinkSource.GOOGLE -> if (isDriveLink) "Google Drive link recognized" else "Google link recognized automatically"
                            LinkSource.WEB -> "Web media link recognized"
                            null -> "Auto-recognizes YouTube, TikTok & Google"
                        },
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
                    text = "Paste any link — the app automatically recognizes it, displays its logo, and downloads directly to your phone's Music folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Link Input field with dynamic recognized app logo & quick Paste/Clear button
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Paste Link") },
                    placeholder = { Text("YouTube, TikTok, Google, or Web URL") },
                    leadingIcon = {
                        when (detectedSource) {
                            LinkSource.YOUTUBE -> YouTubeLogoIcon(size = 22.dp)
                            LinkSource.TIKTOK -> TikTokLogoIcon(size = 22.dp)
                            LinkSource.GOOGLE, LinkSource.WEB -> GoogleWebIcon(size = 22.dp)
                            null -> Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Link",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingIcon = {
                        if (urlText.isNotEmpty()) {
                            IconButton(onClick = { urlText = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Link",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.trim()?.let { clip ->
                                    if (clip.isNotBlank()) urlText = clip
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste Link",
                                    tint = PlusBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                        text = "Please paste a valid YouTube, TikTok, Google, or Web link.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Recognized App Logo Badge (appears automatically when a link is recognized)
                AnimatedVisibility(
                    visible = detectedSource != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    detectedSource?.let { src ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PlusBlue.copy(alpha = 0.10f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .border(1.dp, PlusBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .testTag("recognized_source_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    when (src) {
                                        LinkSource.YOUTUBE -> YouTubeLogoIcon(size = 26.dp)
                                        LinkSource.TIKTOK -> TikTokLogoIcon(size = 26.dp)
                                        LinkSource.GOOGLE, LinkSource.WEB -> GoogleWebIcon(size = 26.dp)
                                    }
                                    Column {
                                        Text(
                                            text = when (src) {
                                                LinkSource.YOUTUBE -> "YouTube App"
                                                LinkSource.TIKTOK -> "TikTok App"
                                                LinkSource.GOOGLE -> if (isDriveLink) "Google Drive" else "Google Search / Web"
                                                LinkSource.WEB -> "Web Media Source"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Detected automatically • Ready to download",
                                            fontSize = 10.sp,
                                            color = PlusBlue,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Detected",
                                    tint = PlusBlue,
                                    modifier = Modifier.size(18.dp)
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

                // Format switch: Audio (HQ Compressed) vs Video (HD MP4)
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
                                text = if (isVideo) "Video (HD MP4)" else "Audio (HQ Compressed)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isVideo) "Includes video stream" else "Compressed high-quality audio",
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

                // Reassuring Device Storage Banner
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
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = PlusBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Saves directly to Device (/Music/ASDMusic/) — plays in all apps.",
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
                    if (isValidLink) {
                        onDownloadAndPlay(
                            LinkDetector.normalizeUrl(urlText),
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
                Text(text = "Download", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
