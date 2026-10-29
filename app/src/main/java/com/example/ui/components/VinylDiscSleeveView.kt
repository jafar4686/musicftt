package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.model.Track
import com.example.ui.theme.BorderWarm
import com.example.ui.theme.CaramelGold
import com.example.ui.theme.DeepWarmBrown
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.TextCream
import com.example.ui.theme.TextMutedSand
import com.example.ui.theme.WarmBeige

/**
 * Interactive Vinyl Disc & Album Sleeve / Folder Component.
 * - When playing: The vinyl disc smoothly slides OUT of the album folder and rotates continuously.
 * - When paused / stopped: The vinyl disc stops spinning and slides BACK inside the folder.
 */
@Composable
fun VinylDiscSleeveView(
    track: Track,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 270.dp
) {
    // Rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    // Slide out/in horizontal offset
    val slideAnim = remember { Animatable(if (isPlaying) 92f else 6f) }

    LaunchedEffect(isPlaying) {
        val targetOffset = if (isPlaying) 92f else 6f
        slideAnim.animateTo(
            targetValue = targetOffset,
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .size(width = size + 80.dp, height = size)
            .testTag("vinyl_sleeve_view")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTogglePlayPause
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // --- 1. THE VINYL DISC (Layered behind/sliding out of the folder) ---
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.90f)
                .offset(x = slideAnim.value.dp)
                .rotate(if (isPlaying) spinRotation else 0f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF28201C),
                            Color(0xFF1B1411),
                            Color(0xFF0F0B09)
                        )
                    )
                )
                .border(1.5.dp, BorderWarm.copy(alpha = 0.6f), CircleShape)
                .shadow(12.dp, CircleShape)
                .zIndex(1f)
        ) {
            // Concentric sound grooves
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val maxR = this.size.width / 2f
                for (step in 3..11) {
                    val r = maxR * (step / 12.5f)
                    drawCircle(
                        color = WarmBeige.copy(alpha = if (step % 2 == 0) 0.08f else 0.04f),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }

                // Vinyl sheen light sweep reflections
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.25f to Color.White.copy(alpha = 0.06f),
                        0.5f to Color.Transparent,
                        0.75f to Color.White.copy(alpha = 0.06f),
                        1.0f to Color.Transparent
                    ),
                    radius = maxR * 0.95f,
                    center = center
                )
            }

            // Center Record Label
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size * 0.34f)
                    .clip(CircleShape)
                    .background(CaramelGold)
                    .border(1.dp, EspressoDark.copy(alpha = 0.4f), CircleShape)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        text = "ASD MUSIC",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = EspressoDark,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.title,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EspressoDark.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                // Turntable center spindle hole
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(EspressoDark)
                )
            }
        }

        // --- 2. THE ALBUM JACKET / FOLDER (Covers the vinyl disc on the left) ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepWarmBrown,
            shadowElevation = 14.dp,
            modifier = Modifier
                .size(size * 0.88f)
                .border(1.5.dp, BorderWarm, RoundedCornerShape(16.dp))
                .zIndex(2f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header on folder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ASD MUSIC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CaramelGold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "VINYL EDITION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMutedSand,
                            letterSpacing = 1.sp
                        )
                    }

                    // Center Cover Art on the vinyl folder jacket
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        SongCoverArtView(
                            track = track,
                            size = 100.dp,
                            shapeRadius = 12.dp
                        )
                    }

                    // Bottom info on folder
                    Column {
                        Text(
                            text = track.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextCream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            fontSize = 12.sp,
                            color = TextMutedSand,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
