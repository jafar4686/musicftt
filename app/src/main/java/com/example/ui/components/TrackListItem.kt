package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.model.Track
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.HologramPink
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TrackListItem(
    track: Track,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    downloadItem: DownloadItem?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadAudio: () -> Unit,
    onDownloadVideo: () -> Unit,
    onRemoveDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentTrack) SurfaceElevated else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("track_item_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Icon or Live Equalizer
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isCurrentTrack) ElectricCyan.copy(alpha = 0.2f) else Color(0xFF191A26)
                )
        ) {
            if (isCurrentTrack && isPlaying) {
                MiniEqualizerBars(isPlaying = true)
            } else {
                Text(
                    text = when (track.genre) {
                        "Cyberpunk" -> "⚡"
                        "Chillhop" -> "🌧️"
                        "Ambient" -> "🌌"
                        "Synthwave" -> "🌆"
                        else -> "🎵"
                    },
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title, Artist, Tags
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentTrack) ElectricCyan else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isVideo) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(HologramPink.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video",
                                tint = HologramPink,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "VIDEO", fontSize = 8.sp, color = HologramPink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${track.artist} • ${track.durationFormatted}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Download status badge
                if (track.isDownloaded || (downloadItem != null && downloadItem.status == DownloadStatus.COMPLETED)) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Offline ready",
                        tint = EmeraldGlow,
                        modifier = Modifier.size(13.dp)
                    )
                } else if (downloadItem != null && downloadItem.status == DownloadStatus.DOWNLOADING) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CircularProgressIndicator(
                        progress = { downloadItem.progress },
                        strokeWidth = 2.dp,
                        color = ElectricCyan,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Favorite Toggle
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Unlike" else "Like",
                tint = if (isFavorite) HologramPink else TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }

        // More Options Menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More actions",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF1B1C28))
            ) {
                if (track.isDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Delete Offline Download", color = Color.Red, fontSize = 13.sp) },
                        onClick = {
                            showMenu = false
                            onRemoveDownload()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Download Audio (${track.downloadSizeBytes / 1_000_000} MB)", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            showMenu = false
                            onDownloadAudio()
                        }
                    )
                    if (track.isVideo) {
                        DropdownMenuItem(
                            text = { Text("Download Video File (24.5 MB)", color = ElectricCyan, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onDownloadVideo()
                            }
                        )
                    }
                }
            }
        }
    }
}
