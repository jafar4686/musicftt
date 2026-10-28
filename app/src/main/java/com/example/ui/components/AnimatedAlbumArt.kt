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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimatedArtStyle
import com.example.model.Track
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HologramPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SoftViolet
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedAlbumArt(
    track: Track,
    isPlaying: Boolean,
    currentArtStyle: AnimatedArtStyle,
    onStyleSelected: (AnimatedArtStyle) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    showStyleSelector: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag("animated_album_art_container")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .padding(8.dp)
        ) {
            when (currentArtStyle) {
                AnimatedArtStyle.VINYL -> VinylRecordArt(track = track, isPlaying = isPlaying, size = size)
                AnimatedArtStyle.NEON_AURA -> NeonAuraArt(track = track, isPlaying = isPlaying, size = size)
                AnimatedArtStyle.FREQUENCY_BARS -> FrequencyBarsArt(track = track, isPlaying = isPlaying, size = size)
                AnimatedArtStyle.HOLOGRAPHIC_ORBIT -> HolographicOrbitArt(track = track, isPlaying = isPlaying, size = size)
            }
        }

        if (showStyleSelector) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                AnimatedArtStyle.entries.forEach { style ->
                    val isSelected = style == currentArtStyle
                    FilterChip(
                        selected = isSelected,
                        onClick = { onStyleSelected(style) },
                        label = {
                            Text(
                                text = when (style) {
                                    AnimatedArtStyle.VINYL -> "Vinyl"
                                    AnimatedArtStyle.NEON_AURA -> "Aura"
                                    AnimatedArtStyle.FREQUENCY_BARS -> "Spectrum"
                                    AnimatedArtStyle.HOLOGRAPHIC_ORBIT -> "Holo"
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (style) {
                                    AnimatedArtStyle.VINYL -> Icons.Default.Radio
                                    AnimatedArtStyle.NEON_AURA -> Icons.Default.AutoAwesome
                                    AnimatedArtStyle.FREQUENCY_BARS -> Icons.Default.GraphicEq
                                    AnimatedArtStyle.HOLOGRAPHIC_ORBIT -> Icons.Default.MusicNote
                                },
                                contentDescription = style.displayName,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan,
                            selectedLeadingIconColor = ElectricCyan
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) ElectricCyan else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun VinylRecordArt(track: Track, isPlaying: Boolean, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Smooth slide out offset when playing
    val slideOffset = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        slideOffset.animateTo(
            targetValue = if (isPlaying) 32f else 8f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        // Vinyl Disc sliding out from sleeve
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.88f)
                .offset(x = slideOffset.value.dp)
                .rotate(if (isPlaying) rotation else 0f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E1E28),
                            Color(0xFF101016),
                            Color(0xFF08080C)
                        )
                    )
                )
                .border(1.dp, Color(0xFF2C2D3E), CircleShape)
        ) {
            // Vinyl grooves canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val maxRadius = this.size.width / 2f

                // Concentric grooves
                for (r in 3..10) {
                    val radius = maxRadius * (r / 11f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f + (r % 2) * 0.02f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                }

                // Grooves light sheen reflection highlights
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(center.x - maxRadius * 0.7f, center.y - maxRadius * 0.7f),
                    end = Offset(center.x + maxRadius * 0.7f, center.y + maxRadius * 0.7f),
                    strokeWidth = 2f
                )
            }

            // Center Label with track initial
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size * 0.32f)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(ElectricCyan, NeonPurple)
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ObsidianBlack)
                )
            }
        }

        // Front Album Jacket Sleeve
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF14151F),
            shadowElevation = 12.dp,
            modifier = Modifier
                .size(size * 0.82f)
                .align(Alignment.CenterStart)
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFF181926),
                                Color(0xFF232438),
                                Color(0xFF151624),
                                Color(0xFF181926)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                // Sleek album jacket artwork
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = track.genre.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) ElectricCyan else Color.Gray)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = when (track.genre) {
                                "Cyberpunk" -> "⚡"
                                "Chillhop" -> "🌧️"
                                "Ambient" -> "🌌"
                                "Synthwave" -> "🌆"
                                else -> "🎵"
                            },
                            fontSize = 42.sp
                        )
                    }

                    Column {
                        Text(
                            text = track.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = track.artist,
                            fontSize = 11.sp,
                            color = SoftViolet,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NeonAuraArt(track: Track, isPlaying: Boolean, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_aura")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val auraRotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auraRotate"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        // Outer radiant pulsating neon aura
        Canvas(modifier = Modifier.size(size * (if (isPlaying) pulse else 1f))) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.width * 0.44f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = if (isPlaying) 0.35f else 0.15f),
                        NeonPurple.copy(alpha = if (isPlaying) 0.25f else 0.1f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.15f
                ),
                radius = baseRadius * 1.15f,
                center = center
            )
        }

        // Inner glowing artwork card
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.78f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.sweepGradient(
                        listOf(
                            ElectricCyan.copy(alpha = 0.8f),
                            NeonPurple.copy(alpha = 0.9f),
                            HologramPink.copy(alpha = 0.8f),
                            ElectricCyan.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(2.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianBlack)
            ) {
                // Background aura nebula
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(if (isPlaying) auraRotate else 0f)
                ) {
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                NeonPurple.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        ),
                        radius = this.size.width * 0.6f,
                        center = center
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "AURA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ElectricCyan,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (track.genre) {
                            "Cyberpunk" -> "⚡"
                            "Chillhop" -> "🌧️"
                            "Ambient" -> "🌌"
                            "Synthwave" -> "🌆"
                            else -> "🎵"
                        },
                        fontSize = 46.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = track.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun FrequencyBarsArt(track: Track, isPlaying: Boolean, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "bars")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size * 0.84f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF11121B))
            .border(1.dp, Color(0xFF2A2B3D), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = 14
            val spacing = this.size.width / (barCount + 1)
            val maxBarHeight = this.size.height * 0.65f
            val centerY = this.size.height / 2f

            for (i in 0 until barCount) {
                val factor = when (i % 3) {
                    0 -> phase1
                    1 -> phase2
                    else -> phase3
                }
                val scale = if (isPlaying) factor * (0.5f + 0.5f * sin((i + 1) * 0.8).toFloat().coerceAtLeast(0.1f)) else 0.15f
                val barHeight = maxBarHeight * scale
                val x = spacing * (i + 1)

                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(ElectricCyan, NeonPurple)
                    ),
                    start = Offset(x, centerY - barHeight / 2f),
                    end = Offset(x, centerY + barHeight / 2f),
                    strokeWidth = 7.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(ObsidianBlack.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${track.bpm} BPM • ${track.musicalKey}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ElectricCyan
            )
        }
    }
}

@Composable
fun HolographicOrbitArt(track: Track, isPlaying: Boolean, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "holo_rings")
    val orbitAngle1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "o1"
    )
    val orbitAngle2 by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "o2"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        // Holographic particle orbital rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val r1 = this.size.width * 0.42f
            val r2 = this.size.width * 0.35f

            // Ring 1
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(ElectricCyan, Color.Transparent, NeonPurple, Color.Transparent, ElectricCyan)
                ),
                radius = r1,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Orbiting node 1
            if (isPlaying) {
                val rad1 = Math.toRadians(orbitAngle1.toDouble())
                val node1X = center.x + (r1 * cos(rad1)).toFloat()
                val node1Y = center.y + (r1 * sin(rad1)).toFloat()
                drawCircle(color = ElectricCyan, radius = 5.dp.toPx(), center = Offset(node1X, node1Y))

                // Orbiting node 2
                val rad2 = Math.toRadians(orbitAngle2.toDouble())
                val node2X = center.x + (r2 * cos(rad2)).toFloat()
                val node2Y = center.y + (r2 * sin(rad2)).toFloat()
                drawCircle(color = HologramPink, radius = 4.dp.toPx(), center = Offset(node2X, node2Y))
            }
        }

        // Center holographic sphere
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.58f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF241638),
                            Color(0xFF100C1C),
                            ObsidianBlack
                        )
                    )
                )
                .border(1.dp, SoftViolet.copy(alpha = 0.5f), CircleShape)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (track.isVideo) "VIDEO" else "HOLO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = HologramPink,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "🌐",
                    fontSize = 32.sp
                )
            }
        }
    }
}
