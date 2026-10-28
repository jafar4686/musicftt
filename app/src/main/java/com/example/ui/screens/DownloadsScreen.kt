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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.model.Track
import com.example.ui.theme.BorderWarm
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CaramelGold
import com.example.ui.theme.DeepWarmBrown
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.TextCream
import com.example.ui.theme.TextMutedSand
import com.example.ui.theme.WarmCardSurface

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
    val downloadedTracks = tracks.filter { it.isDownloaded }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("downloads_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "STORAGE VAULT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CaramelGold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Downloaded Media",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCream
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Storage Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DeepWarmBrown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(WarmCardSurface)
                                ) {
                                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = "Local Device Storage", fontSize = 11.sp, color = TextMutedSand)
                                    Text(text = storageSizeFormatted, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextCream)
                                }
                            }

                            if (downloadedTracks.isNotEmpty()) {
                                IconButton(onClick = onClearAll, modifier = Modifier.size(32.dp)) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear all", tint = TextMutedSand, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { (downloadedTracks.size / 10f).coerceIn(0.08f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = CaramelGold,
                            trackColor = BorderWarm
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "${downloadedTracks.size} items saved offline", fontSize = 11.sp, color = CaramelGold)
                            Text(text = "Playable without internet", fontSize = 11.sp, color = TextMutedSand)
                        }
                    }
                }
            }
        }

        // Active downloads
        val activeDownloads = downloads.filter { it.status == DownloadStatus.DOWNLOADING }
        if (activeDownloads.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(text = "Downloading Now (${activeDownloads.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CaramelGold)
                    Spacer(modifier = Modifier.height(6.dp))
                    activeDownloads.forEach { dl ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DeepWarmBrown,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, BorderWarm, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = dl.trackTitle, fontSize = 13.sp, color = TextCream, fontWeight = FontWeight.SemiBold)
                                    Text(text = "${(dl.progress * 100).toInt()}%", fontSize = 12.sp, color = CaramelGold, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { dl.progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = CaramelGold,
                                    trackColor = BorderWarm
                                )
                            }
                        }
                    }
                }
            }
        }

        if (downloadedTracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(WarmCardSurface)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "No Downloads Saved", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextCream)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Paste a TikTok or YouTube link on the Songs screen to download audio or video directly to your device.",
                        fontSize = 12.sp,
                        color = TextMutedSand,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(downloadedTracks) { track ->
                val isCurrent = currentTrack?.id == track.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCurrent) CardElevated else DeepWarmBrown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .border(1.dp, if (isCurrent) CaramelGold.copy(alpha = 0.5f) else BorderWarm, RoundedCornerShape(14.dp))
                        .clickable { onPlayTrack(track) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(WarmCardSurface)
                        ) {
                            if (track.isVideo) {
                                Icon(imageVector = Icons.Default.Videocam, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrent) CaramelGold else TextCream,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${track.artist} • ${if (track.isVideo) "Video File" else "Audio MP3"}",
                                fontSize = 11.sp,
                                color = TextMutedSand,
                                maxLines = 1
                            )
                        }

                        IconButton(onClick = { onRemoveDownload(track.id) }, modifier = Modifier.size(34.dp)) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TextMutedSand, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
