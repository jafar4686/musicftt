package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HologramPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DynamicIslandOverlay(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onExpandIsland: (Boolean) -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    if (!playbackState.isDynamicIslandVisible) return

    val isExpanded = playbackState.isDynamicIslandExpanded

    // Animated dimensions for dynamic pill
    val targetCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 28.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "corner"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 210.dp, max = if (isExpanded) 380.dp else 260.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(targetCornerRadius))
                .clip(RoundedCornerShape(targetCornerRadius))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF13141E),
                            Color(0xFF090A10)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            ElectricCyan.copy(alpha = if (playbackState.isPlaying) 0.6f else 0.2f),
                            NeonPurple.copy(alpha = if (playbackState.isPlaying) 0.6f else 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(targetCornerRadius)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onExpandIsland(!isExpanded)
                }
                .animateContentSize(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f))
                .testTag("dynamic_island_capsule")
        ) {
            if (!isExpanded) {
                // Compact Dynamic Island Pill
                CompactDynamicIsland(
                    trackTitle = track.title,
                    artist = track.artist,
                    isPlaying = playbackState.isPlaying,
                    progress = playbackState.progress,
                    onTogglePlay = onTogglePlayPause
                )
            } else {
                // Expanded Interactive Dynamic Island Card
                ExpandedDynamicIsland(
                    playbackState = playbackState,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onCollapse = { onExpandIsland(false) },
                    onOpenFullPlayer = onOpenFullPlayer
                )
            }
        }
    }
}

@Composable
private fun CompactDynamicIsland(
    trackTitle: String,
    artist: String,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "island_rot")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )

    Column {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Glowing Mini Vinyl Disc
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .rotate(if (isPlaying) rotation else 0f)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1C28))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) ElectricCyan else Color.Gray)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Center: Track title & artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = trackTitle,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    color = SoftViolet,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Animated live waveform bars & Play toggle
            MiniEqualizerBars(isPlaying = isPlaying)

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan.copy(alpha = 0.15f))
                    .clickable { onTogglePlay() }
                    .testTag("island_play_toggle")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Tiny bottom progress line
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp),
            color = ElectricCyan,
            trackColor = Color.Transparent
        )
    }
}

@Composable
private fun ExpandedDynamicIsland(
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    val track = playbackState.currentTrack ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header: Badge + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (playbackState.isPlaying) ElectricCyan else Color.Gray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AURA DYNAMIC ISLAND",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = ElectricCyan
                )
            }

            Row {
                IconButton(
                    onClick = onOpenFullPlayer,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Open Full Player",
                        tint = SoftViolet,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Collapse",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Track Details
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini Art Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(NeonPurple, ElectricCyan)))
            ) {
                Text(
                    text = when (track.genre) {
                        "Cyberpunk" -> "⚡"
                        "Chillhop" -> "🌧️"
                        "Ambient" -> "🌌"
                        "Synthwave" -> "🌆"
                        else -> "🎵"
                    },
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scrubber Slider
        Slider(
            value = playbackState.progress,
            onValueChange = { frac ->
                onSeek((frac * playbackState.durationMs).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = ElectricCyan,
                activeTrackColor = ElectricCyan,
                inactiveTrackColor = Color(0xFF242636)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val curSeconds = (playbackState.currentPositionMs / 1000).toInt()
            val totalSeconds = (playbackState.durationMs / 1000).toInt()
            Text(
                text = "%d:%02d".format(curSeconds / 60, curSeconds % 60),
                fontSize = 10.sp,
                color = TextSecondary
            )
            Text(
                text = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Playback Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ElectricCyan, NeonPurple)))
                    .clickable { onTogglePlayPause() }
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = ObsidianBlack,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun MiniEqualizerBars(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mini_eq")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(240, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Canvas(modifier = modifier.size(width = 16.dp, height = 14.dp)) {
        val barWidth = 3.dp.toPx()
        val spacing = 2.dp.toPx()
        val maxHeight = this.size.height

        val heights = if (isPlaying) listOf(h1, h2, h3) else listOf(0.2f, 0.2f, 0.2f)

        heights.forEachIndexed { i, factor ->
            val barH = maxHeight * factor
            val x = i * (barWidth + spacing)
            drawLine(
                color = ElectricCyan,
                start = Offset(x, maxHeight - barH),
                end = Offset(x, maxHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
