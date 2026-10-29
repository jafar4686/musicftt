package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * Main Song Library Screen for AsdMusic.
 * Displays total count of detected songs and ordered track list
 * showing index, full name, artist, and minutes:seconds duration.
 */
@Composable
fun SongsScreen(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    hasPermissions: Boolean,
    favoriteTrackIds: Set<String>,
    onRequestPermissions: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onScanDevice: () -> Unit,
    onOpenAddSong: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("songs_screen"),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // --- 1. HEADER SECTION ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AsdMusic",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = CaramelGold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Music Player",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMutedSand
                        )
                    }

                    // Rescan button
                    OutlinedButton(
                        onClick = onScanDevice,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CaramelGold),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("scan_device_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Song count and status badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DeepWarmBrown,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(CaramelGold.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = CaramelGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${tracks.size} Songs in Library",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCream
                            )
                        }

                        Text(
                            text = if (tracks.isNotEmpty()) "Ready to Play" else "Scanning...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CaramelGold
                        )
                    }
                }
            }
        }

        // --- 2. PERMISSIONS PROMPT (If not yet granted) ---
        if (!hasPermissions) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DeepWarmBrown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .border(1.dp, CaramelGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CaramelGold.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = CaramelGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Permissions Required",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextCream
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "AsdMusic needs permission to access your audio files and show playback controls in the notification bar.",
                            fontSize = 12.sp,
                            color = TextMutedSand,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CaramelGold,
                                contentColor = EspressoDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("grant_permissions_button")
                        ) {
                            Text(text = "Grant Permissions", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 3. EMPTY STATE ---
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
                            Text(text = "🎧", fontSize = 42.sp)

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "No Songs Found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCream
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Tap 'Scan' above to search your device for audio files, or tap the button below to add a link.",
                                fontSize = 12.sp,
                                color = TextMutedSand,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = onOpenAddSong,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CaramelGold,
                                    contentColor = EspressoDark
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Add Song Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // --- 4. ORDERED SONG LIST (Index, Full Name, Artist, Duration) ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#   SONG TITLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMutedSand,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "DURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMutedSand,
                        letterSpacing = 1.sp
                    )
                }
            }

            itemsIndexed(tracks) { index, track ->
                val isCurrent = currentTrack?.id == track.id
                val isFav = favoriteTrackIds.contains(track.id)
                val trackNumber = String.format("%02d", index + 1)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCurrent) CardElevated else DeepWarmBrown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .border(
                            width = 1.dp,
                            color = if (isCurrent) CaramelGold else BorderWarm,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onPlayTrack(track) }
                        .testTag("track_item_${track.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Track index / equalizer animation
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isCurrent && isPlaying) {
                                MiniLiveEqualizer()
                            } else {
                                Text(
                                    text = trackNumber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) CaramelGold else TextMutedSand
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Full Title & Artist Name
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isCurrent) CaramelGold else TextCream,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${track.artist} • ${track.album}",
                                fontSize = 12.sp,
                                color = TextMutedSand,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Duration in minutes:seconds
                        Text(
                            text = track.durationFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isCurrent) CaramelGold else TextMutedSand
                        )

                        // Favorite heart toggle
                        IconButton(
                            onClick = { onToggleFavorite(track.id) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) CaramelGold else TextMutedSand.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated mini 3-bar equalizer when a track is actively playing.
 */
@Composable
fun MiniLiveEqualizer() {
    val transition = rememberInfiniteTransition(label = "eq")
    val b1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val b2 by transition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val b3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((16 * b1).dp)
                .background(CaramelGold, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((16 * b2).dp)
                .background(CaramelGold, RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((16 * b3).dp)
                .background(CaramelGold, RoundedCornerShape(2.dp))
        )
    }
}
