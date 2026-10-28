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
import com.example.ui.theme.WarmBeige

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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = if (isExpanded) 330.dp else 46.dp, max = if (isExpanded) 360.dp else 46.dp)
                .height(if (isExpanded) 152.dp else 46.dp)
                .shadow(elevation = 14.dp, shape = RoundedCornerShape(targetCornerRadius))
                .clip(RoundedCornerShape(targetCornerRadius))
                .background(
                    Brush.verticalGradient(
                        listOf(DeepWarmBrown, CocoaBrown)
                    )
                )
                .border(
                    width = 1.dp,
                    color = BorderWarm,
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
                // Idle State: Neat Small Circle
                SmallCircleIndicator(
                    isPlaying = playbackState.isPlaying,
                    onClick = onToggleExpand
                )
            } else {
                // Tapped State: Enlarged Island Capsule
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

@Composable
private fun SmallCircleIndicator(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "disc_rot")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clickable { onClick() }
    ) {
        // Rotating disc indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .rotate(if (isPlaying) rotation else 0f)
                .clip(CircleShape)
                .background(CardElevated)
                .border(1.dp, CaramelGold.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Playing",
                tint = if (isPlaying) CaramelGold else TextMutedSand,
                modifier = Modifier.size(16.dp)
            )
        }

        // Little active play dot
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp, top = 4.dp)
                    .clip(CircleShape)
                    .background(CaramelGold)
            )
        }
    }
}

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
        // Header Row: Badge & Action controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (playbackState.isPlaying) CaramelGold else TextMutedSand)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AURA ISLAND",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CaramelGold,
                    letterSpacing = 1.sp
                )
            }

            Row {
                IconButton(onClick = onOpenFullPlayer, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Open Full Player",
                        tint = TextMutedSand,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onCollapse, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Collapse",
                        tint = TextMutedSand,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title and Artist
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardElevated)
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

        // Timeline Scrubber
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

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = TextCream, modifier = Modifier.size(18.dp))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(CaramelGold)
                    .clickable { onTogglePlayPause() }
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = EspressoDark,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = TextCream, modifier = Modifier.size(18.dp))
            }
        }
    }
}
