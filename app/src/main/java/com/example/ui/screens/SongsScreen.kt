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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
fun SongsScreen(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteTrackIds: Set<String>,
    onPlayTrack: (Track) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onScanDevice: () -> Unit,
    onOpenAddSong: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("songs_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AURA MUSIC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CaramelGold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Device Library",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextCream
                        )
                    }

                    // Scan button
                    OutlinedButton(
                        onClick = onScanDevice,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CaramelGold),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Scan Device", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Add Link / Song Button
                Button(
                    onClick = onOpenAddSong,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CaramelGold,
                        contentColor = EspressoDark
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_song_main_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Add Song (TikTok / YouTube Link)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Empty state when library is at zero
        if (tracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = DeepWarmBrown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderWarm, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(WarmCardSurface)
                            ) {
                                Text(text = "🎵", fontSize = 28.sp)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Library Set to Zero",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCream
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Only your device's built-in songs and downloaded links will play here. Tap 'Scan Device' to scan local music files or paste any TikTok / YouTube link to download.",
                                fontSize = 12.sp,
                                color = TextMutedSand,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = onOpenAddSong,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CaramelGold,
                                    contentColor = EspressoDark
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = "Paste Download Link", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tracks (${tracks.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextCream
                    )
                    Text(
                        text = "Device & Offline Ready",
                        fontSize = 11.sp,
                        color = TextMutedSand
                    )
                }
            }

            items(tracks) { track ->
                val isCurrent = currentTrack?.id == track.id
                val isFav = favoriteTrackIds.contains(track.id)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCurrent) CardElevated else DeepWarmBrown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .border(1.dp, if (isCurrent) CaramelGold.copy(alpha = 0.5f) else BorderWarm, RoundedCornerShape(14.dp))
                        .clickable { onPlayTrack(track) }
                        .testTag("track_row_${track.id}")
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) CaramelGold.copy(alpha = 0.2f) else WarmCardSurface)
                        ) {
                            if (isCurrent && isPlaying) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = if (isCurrent) CaramelGold else TextMutedSand, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) CaramelGold else TextCream,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (track.isVideo) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video", tint = CaramelGold, modifier = Modifier.size(12.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${track.artist} • ${track.durationFormatted}",
                                fontSize = 11.sp,
                                color = TextMutedSand,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onToggleFavorite(track.id) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFav) CaramelGold else TextMutedSand,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onRemoveTrack(track.id) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextMutedSand,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
