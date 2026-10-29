package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackState
import com.example.ui.theme.BorderWarm
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CaramelGold
import com.example.ui.theme.CocoaBrown
import com.example.ui.theme.DeepWarmBrown
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.MutedAmber
import com.example.ui.theme.TextCream
import com.example.ui.theme.TextMutedSand
import com.example.ui.theme.WarmCardSurface

/**
 * Polished, high-performance Dynamic Island with live animated sound wave.
 * Anchored at the top under system status bars.
 * Compact mode: Elegant pill displaying rotating vinyl, title/artist, live audio wave, and play button.
 * Expanded mode: Full interactive capsule with timeline scrubber, controls, and full player launcher.
 */
@Composable
fun DynamicIslandOverlay(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleExpand: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    if (!playbackState.isDynamicIslandEnabled) return

    val isExpanded = playbackState.isDynamicIslandExpanded

    val targetCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 24.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "corner_radius"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(
                    min = if (isExpanded) 330.dp else 260.dp,
                    max = if (isExpanded) 370.dp else 295.dp
                )
                .shadow(elevation = 14.dp, shape = RoundedCornerShape(targetCornerRadius))
                .clip(RoundedCornerShape(targetCornerRadius))
                .background(
                    Brush.verticalGradient(
                        listOf(DeepWarmBrown, CocoaBrown)
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            CaramelGold.copy(alpha = if (playbackState.isPlaying) 0.65f else 0.35f),
                            BorderWarm
                        )
                    ),
                    shape = RoundedCornerShape(targetCornerRadius)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onToggleExpand()
                }
                .animateContentSize(animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f))
                .testTag("dynamic_island_overlay")
        ) {
            if (!isExpanded) {
                // Reverted & Enhanced: The Original Wave Dynamic Island Pill
                CompactWaveDynamicIsland(
                    trackTitle = track.title,
                    artist = track.artist,
                    isPlaying = playbackState.isPlaying,
                    progress = playbackState.progress,
                    onTogglePlay = onTogglePlayPause
                )
            } else {
                // Tapped State: Enlarged Island Capsule with scrubber and controls
                EnlargedIslandCapsule(
                    trackTitle = track.title,
                    artist = track.artist,
                    playbackState = playbackState,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onCollapse = onToggleExpand,
                    onOpenFullPlayer = onOpenFullPlayer
                )
            }
        }
    }
}

/**
 * Compact Dynamic Island Pill featuring:
 * 1. Mini rotating vinyl record
 * 2. Track Title & Artist
 * 3. Animated live audio wave / frequency bars ("الموجة")
 * 4. Instant Play/Pause toggle
 * 5. Hairline track progress indicator along bottom edge
 */
@Composable
private fun CompactWaveDynamicIsland(
    trackTitle: String,
    artist: String,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "island_rot")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Rotating vinyl disc with glowing caramel center
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .rotate(if (isPlaying) rotation else 0f)
                    .clip(CircleShape)
                    .background(CardElevated)
                    .border(1.dp, CaramelGold.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Playing",
                    tint = if (isPlaying) CaramelGold else TextMutedSand,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Center: Track Title & Artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = trackTitle,
                    color = TextCream,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    color = TextMutedSand,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Animated Live Waveform ("الموجة")
            LiveWaveformEqualizer(
                isPlaying = isPlaying,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Play/Pause Action Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CaramelGold)
                    .clickable { onTogglePlay() }
                    .testTag("island_play_toggle")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = EspressoDark,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Micro Progress Bar at the very bottom edge of the pill
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(1.8.dp),
            color = CaramelGold,
            trackColor = Color.Transparent
        )
    }
}

/**
 * Silky smooth animated live wave / equalizer bars ("الموجة").
 * Uses 5 harmonic vertical audio bars rendered via Canvas with zero runtime heap allocations.
 */
@Composable
fun LiveWaveformEqualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live_wave")

    val h1 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.85f, targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(240, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.90f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w4"
    )
    val h5 by transition.animateFloat(
        initialValue = 0.30f, targetValue = 0.80f,
        animationSpec = infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w5"
    )

    Canvas(modifier = modifier.size(width = 22.dp, height = 15.dp)) {
        val barWidth = 2.4.dp.toPx()
        val spacing = 2.0.dp.toPx()
        val maxHeight = size.height

        val heights = if (isPlaying) {
            floatArrayOf(h1, h2, h3, h4, h5)
        } else {
            floatArrayOf(0.25f, 0.25f, 0.25f, 0.25f, 0.25f)
        }

        for (i in heights.indices) {
            val barH = maxHeight * heights[i]
            val x = i * (barWidth + spacing) + barWidth / 2f
            drawLine(
                color = CaramelGold,
                start = Offset(x, maxHeight - barH),
                end = Offset(x, maxHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Expanded Dynamic Island Capsule with full controls, track scrubbing, and quick actions.
 */
@Composable
private fun EnlargedIslandCapsule(
    trackTitle: String,
    artist: String,
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Header Row: Badge with mini wave & Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveWaveformEqualizer(isPlaying = playbackState.isPlaying)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AURA DYNAMIC ISLAND",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CaramelGold,
                    letterSpacing = 1.2.sp
                )
            }
            Row {
                IconButton(onClick = onOpenFullPlayer, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Open Full Player",
                        tint = TextCream,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onCollapse, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Collapse",
                        tint = TextMutedSand,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title, Artist and Album Art Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WarmCardSurface)
                    .border(1.dp, BorderWarm, RoundedCornerShape(8.dp))
            ) {
                Text(text = "🎵", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    fontSize = 11.sp,
                    color = TextMutedSand,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Timeline Scrubber Slider
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
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val curSec = (playbackState.currentPositionMs / 1000).toInt()
            val totSec = (playbackState.durationMs / 1000).toInt()
            Text(
                text = "%d:%02d".format(curSec / 60, curSec % 60),
                fontSize = 10.sp,
                color = TextMutedSand
            )
            Text(
                text = "%d:%02d".format(totSec / 60, totSec % 60),
                fontSize = 10.sp,
                color = TextMutedSand
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Playback Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = TextCream,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CaramelGold)
                    .clickable { onTogglePlayPause() }
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = EspressoDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = TextCream,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
