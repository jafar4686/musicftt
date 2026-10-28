package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    storageSizeFormatted: String,
    onPlayTrack: (Track) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterType by remember { mutableStateOf("All") } // "All", "Audio", "Video"

    val downloadedTracks = tracks.filter { track ->
        track.isDownloaded || downloads.any { it.trackId == track.id && it.status == DownloadStatus.COMPLETED }
    }

    val filteredList = when (filterType) {
        "Audio" -> downloadedTracks.filter { !it.isVideo }
        "Video" -> downloadedTracks.filter { it.isVideo }
        else -> downloadedTracks
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("downloads_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "OFFLINE VAULT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldGlow,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Downloaded Media",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Storage Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF13141F),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, Color(0xFF26283C), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldGlow.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = EmeraldGlow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Offline Storage Used",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "$storageSizeFormatted / Local Vault",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            if (downloadedTracks.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearAll,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear all downloads",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { (downloadedTracks.size / 10f).coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = EmeraldGlow,
                            trackColor = Color(0xFF242636)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${downloadedTracks.size} files available offline",
                                fontSize = 11.sp,
                                color = EmeraldGlow
                            )
                            Text(
                                text = "Zero data required",
                                fontSize = 11.sp,
                                color = SoftViolet
                            )
                        }
                    }
                }
            }
        }

        // Active downloads in progress (if any)
        val inProgressDownloads = downloads.filter { it.status == DownloadStatus.DOWNLOADING }
        if (inProgressDownloads.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(
                        text = "Downloading Now (${inProgressDownloads.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    inProgressDownloads.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF161726),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.trackTitle,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${(item.progress * 100).toInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = ElectricCyan,
                                    trackColor = Color(0xFF25273C)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Filter pills: All, Audio Only, Video Files
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Audio", "Video").forEach { f ->
                    val isSelected = filterType == f
                    FilterChip(
                        selected = isSelected,
                        onClick = { filterType = f },
                        label = {
                            Text(
                                text = when (f) {
                                    "All" -> "All (${downloadedTracks.size})"
                                    "Audio" -> "Music Audio (${downloadedTracks.count { !it.isVideo }})"
                                    "Video" -> "Video Files (${downloadedTracks.count { it.isVideo }})"
                                    else -> f
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGlow.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldGlow,
                            containerColor = Color(0xFF13141F),
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) EmeraldGlow else Color(0xFF222434)
                        )
                    )
                }
            }
        }

        // Empty State or Downloaded List
        if (filteredList.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161726))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = SoftViolet,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No Offline Downloads Yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tap the download icon on any song or video file to store it locally for instant offline listening.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(filteredList) { track ->
                val isCurrent = currentTrack?.id == track.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141520),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .border(0.5.dp, Color(0xFF26283C), RoundedCornerShape(14.dp))
                        .clickable { onPlayTrack(track) }
                        .testTag("downloaded_track_${track.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (track.isVideo) HologramPink.copy(alpha = 0.2f) else EmeraldGlow.copy(alpha = 0.2f)
                                )
                        ) {
                            if (track.isVideo) {
                                Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video", tint = HologramPink)
                            } else {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Ready", tint = EmeraldGlow)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrent) ElectricCyan else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${track.artist} • ${if (track.isVideo) "Video (24.5 MB)" else "Audio (6.2 MB)"}",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { onRemoveDownload(track.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete download",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
