package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackState
import com.example.model.RepeatMode
import com.example.ui.theme.BorderWarm
import com.example.ui.theme.CaramelGold
import com.example.ui.theme.CocoaBrown
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.TextCream
import com.example.ui.theme.TextMutedSand

/**
 * Fullscreen player modal for AsdMusic.
 * Features the rotating vinyl disc sliding in and out of the folder,
 * full scrubber with elapsed/total minutes, and playback controls.
 */
@Composable
fun FullPlayerModal(
    playbackState: PlaybackState,
    isFavorite: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("full_player_modal"),
        color = CocoaBrown
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_player_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = TextCream,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "AsdMusic PLAYER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CaramelGold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = track.album,
                        fontSize = 12.sp,
                        color = TextMutedSand,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) CaramelGold else TextMutedSand,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // The rotating vinyl disc & folder sleeve
            VinylDiscSleeveView(
                track = track,
                isPlaying = playbackState.isPlaying,
                onTogglePlayPause = onTogglePlayPause,
                size = 250.dp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Track info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${track.artist} • ${track.genre}",
                    fontSize = 14.sp,
                    color = TextMutedSand,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scrubber Slider
            Slider(
                value = playbackState.progress,
                onValueChange = { frac ->
                    onSeek((frac * playbackState.durationMs).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = CaramelGold,
                    activeTrackColor = CaramelGold,
                    inactiveTrackColor = BorderWarm
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Current Time & Total Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val curSec = (playbackState.currentPositionMs / 1000).toInt()
                val totalSec = (playbackState.durationMs / 1000).toInt()
                Text(
                    text = "%d:%02d".format(curSec / 60, curSec % 60),
                    fontSize = 12.sp,
                    color = TextMutedSand
                )
                Text(
                    text = "%d:%02d".format(totalSec / 60, totalSec % 60),
                    fontSize = 12.sp,
                    color = TextMutedSand
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffle) CaramelGold else TextMutedSand,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextCream,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Main Play/Pause Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(CaramelGold)
                        .shadow(8.dp, CircleShape)
                        .clickable { onTogglePlayPause() }
                        .testTag("full_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = EspressoDark,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextCream,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = when (playbackState.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (playbackState.repeatMode != RepeatMode.OFF) CaramelGold else TextMutedSand,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
