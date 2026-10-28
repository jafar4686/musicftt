package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimatedArtStyle
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.model.PlaybackState
import com.example.model.RepeatMode
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
fun FullPlayerModal(
    playbackState: PlaybackState,
    isFavorite: Boolean,
    downloadItem: DownloadItem?,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStyleSelected: (AnimatedArtStyle) -> Unit,
    onDownloadTrack: (Track, Boolean) -> Unit,
    onShareTrack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    var isVideoMode by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("full_player_modal"),
        color = ObsidianBlack
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1B122C),
                            Color(0xFF101018),
                            ObsidianBlack
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse Player",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PLAYING FROM AURA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SoftViolet,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = track.album,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = onShareTrack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Audio / Video Switcher (if video available)
                if (track.isVideo) {
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF1B1C28), RoundedCornerShape(20.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!isVideoMode) ElectricCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { isVideoMode = false }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = "Audio mode",
                                    tint = if (!isVideoMode) ElectricCyan else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Audio",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!isVideoMode) ElectricCyan else TextSecondary
                                )
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isVideoMode) HologramPink.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { isVideoMode = true }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Video mode",
                                    tint = if (isVideoMode) HologramPink else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Video View",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isVideoMode) HologramPink else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Center Animated Art or Video Canvas
                if (isVideoMode) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0F0F16))
                            .border(1.dp, HologramPink.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            FrequencyBarsArt(track = track, isPlaying = playbackState.isPlaying, size = 180.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "HD Video Motion Visualizer Active",
                                fontSize = 12.sp,
                                color = HologramPink,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    AnimatedAlbumArt(
                        track = track,
                        isPlaying = playbackState.isPlaying,
                        currentArtStyle = playbackState.currentArtStyle,
                        onStyleSelected = onStyleSelected,
                        size = 250.dp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Track Title, Artist, Heart, Download
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${track.artist} • ${track.genre}",
                            fontSize = 14.sp,
                            color = SoftViolet,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Offline Download Button
                        IconButton(
                            onClick = { onDownloadTrack(track, track.isVideo) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (track.isDownloaded || (downloadItem != null && downloadItem.status == DownloadStatus.COMPLETED)) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = EmeraldGlow,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else if (downloadItem != null && downloadItem.status == DownloadStatus.DOWNLOADING) {
                                CircularProgressIndicator(
                                    progress = { downloadItem.progress },
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download offline",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Favorite Heart
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "Unlike" else "Like",
                                tint = if (isFavorite) HologramPink else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrubber Timeline Slider
                Slider(
                    value = playbackState.progress,
                    onValueChange = { frac ->
                        onSeek((frac * playbackState.durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = Color(0xFF232536)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Current time and total time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val curSec = (playbackState.currentPositionMs / 1000).toInt()
                    val totalSec = (playbackState.durationMs / 1000).toInt()
                    Text(
                        text = "%d:%02d".format(curSec / 60, curSec % 60),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "%d:%02d".format(totalSec / 60, totalSec % 60),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Playback Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.isShuffle) ElectricCyan else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Main illuminated glowing Play/Pause FAB
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .shadow(16.dp, CircleShape, spotColor = ElectricCyan)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricCyan, NeonPurple)
                                )
                            )
                            .clickable { onTogglePlayPause() }
                            .testTag("full_player_play_pause")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = when (playbackState.repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (playbackState.repeatMode != RepeatMode.OFF) ElectricCyan else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Lyrics / Audio Specs Drawer Toggle
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF13141F),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLyrics = !showLyrics }
                        .border(0.5.dp, Color(0xFF242638), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LYRICS & TRACK SPECS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoftViolet,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (showLyrics) "Hide" else "Show",
                                fontSize = 11.sp,
                                color = ElectricCyan
                            )
                        }

                        if (showLyrics) {
                            Spacer(modifier = Modifier.height(10.dp))
                            track.lyrics.forEach { line ->
                                Text(
                                    text = line,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "BPM: ${track.bpm} • Key: ${track.musicalKey} • Format: 320kbps High-Fidelity",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
