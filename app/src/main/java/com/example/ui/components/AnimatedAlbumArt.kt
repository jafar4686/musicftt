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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import com.example.ui.theme.BorderWarm
import com.example.ui.theme.CaramelGold
import com.example.ui.theme.CocoaBrown
import com.example.ui.theme.DeepWarmBrown
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.MutedAmber
import com.example.ui.theme.TerracottaAccent
import com.example.ui.theme.TextCream
import com.example.ui.theme.TextMutedSand
import com.example.ui.theme.WarmBeige
import com.example.ui.theme.WarmCardSurface
import kotlin.math.sin

@Composable
fun AnimatedAlbumArt(
    track: Track,
    isPlaying: Boolean,
    currentArtStyle: AnimatedArtStyle,
    onStyleSelected: (AnimatedArtStyle) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 250.dp,
    showStyleSelector: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag("animated_album_art")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .padding(6.dp)
        ) {
            when (currentArtStyle) {
                AnimatedArtStyle.VINYL -> WarmVinylArt(track = track, isPlaying = isPlaying, size = size)
                AnimatedArtStyle.MINIMAL_WAVE -> MinimalWaveArt(track = track, isPlaying = isPlaying, size = size)
                AnimatedArtStyle.ACOUSTIC_BARS -> AcousticBarsArt(track = track, isPlaying = isPlaying, size = size)
                AnimatedArtStyle.ELEGANT_GLOW -> ElegantGlowArt(track = track, isPlaying = isPlaying, size = size)
            }
        }

        if (showStyleSelector) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
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
                                    AnimatedArtStyle.MINIMAL_WAVE -> "Wave"
                                    AnimatedArtStyle.ACOUSTIC_BARS -> "Spectrum"
                                    AnimatedArtStyle.ELEGANT_GLOW -> "Warm Glow"
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (style) {
                                    AnimatedArtStyle.VINYL -> Icons.Default.Radio
                                    AnimatedArtStyle.MINIMAL_WAVE -> Icons.Default.Waves
                                    AnimatedArtStyle.ACOUSTIC_BARS -> Icons.Default.GraphicEq
                                    AnimatedArtStyle.ELEGANT_GLOW -> Icons.Default.Tune
                                },
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CaramelGold.copy(alpha = 0.2f),
                            selectedLabelColor = CaramelGold,
                            containerColor = WarmCardSurface,
                            labelColor = TextMutedSand
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) CaramelGold else BorderWarm
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun WarmVinylArt(track: Track, isPlaying: Boolean, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )

    val slideOffset = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        slideOffset.animateTo(
            targetValue = if (isPlaying) 28f else 6f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size)
    ) {
        // Vinyl record
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.88f)
                .offset(x = slideOffset.value.dp)
                .rotate(if (isPlaying) rotation else 0f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF28201C), Color(0xFF1E1714), Color(0xFF120E0C))
                    )
                )
                .border(1.dp, BorderWarm, CircleShape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val maxR = this.size.width / 2f
                for (r in 3..9) {
                    val radius = maxR * (r / 10f)
                    drawCircle(
                        color = WarmBeige.copy(alpha = 0.05f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                }
            }

            // Center label
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size * 0.32f)
                    .clip(CircleShape)
                    .background(CaramelGold)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EspressoDark))
            }
        }

        // Front Jacket Sleeve (Warm Brown card)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DeepWarmBrown,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(size * 0.82f)
                .align(Alignment.CenterStart)
                .border(1.dp, BorderWarm, RoundedCornerShape(14.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AURA PLAYER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CaramelGold,
                        letterSpacing = 1.sp
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        Text(text = "🎵", fontSize = 42.sp)
                    }

                    Column {
                        Text(
                            text = track.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextCream,
                            maxLines = 1
                        )
                        Text(
                            text = track.artist,
                            fontSize = 11.sp,
                            color = TextMutedSand,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MinimalWaveArt(track: Track, isPlaying: Boolean, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DeepWarmBrown,
        modifier = Modifier
            .size(size * 0.84f)
            .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "SOUND WAVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CaramelGold)

            Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                val midY = this.size.height / 2f
                val points = 30
                val step = this.size.width / points

                for (i in 0 until points - 1) {
                    val x1 = i * step
                    val x2 = (i + 1) * step
                    val amp = if (isPlaying) 28.dp.toPx() else 6.dp.toPx()
                    val y1 = midY + amp * sin(i * 0.4 + phase).toFloat()
                    val y2 = midY + amp * sin((i + 1) * 0.4 + phase).toFloat()

                    drawLine(
                        color = CaramelGold,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Text(text = track.title, fontSize = 12.sp, color = TextCream, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun AcousticBarsArt(track: Track, isPlaying: Boolean, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "bars")
    val p1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p1"
    )
    val p2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p2"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DeepWarmBrown,
        modifier = Modifier
            .size(size * 0.84f)
            .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "FREQUENCY BARS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CaramelGold)

            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                val barCount = 12
                val spacing = this.size.width / (barCount + 1)
                val maxH = this.size.height * 0.7f
                val midY = this.size.height / 2f

                for (i in 0 until barCount) {
                    val factor = if (i % 2 == 0) p1 else p2
                    val scale = if (isPlaying) factor else 0.15f
                    val barH = maxH * scale
                    val x = spacing * (i + 1)

                    drawLine(
                        color = CaramelGold,
                        start = Offset(x, midY - barH / 2f),
                        end = Offset(x, midY + barH / 2f),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Text(text = track.artist, fontSize = 12.sp, color = TextMutedSand, maxLines = 1)
        }
    }
}

@Composable
fun ElegantGlowArt(track: Track, isPlaying: Boolean, size: Dp) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DeepWarmBrown,
        modifier = Modifier
            .size(size * 0.84f)
            .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CaramelGold.copy(alpha = if (isPlaying) 0.35f else 0.12f),
                            TerracottaAccent.copy(alpha = if (isPlaying) 0.2f else 0.05f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = this.size.width * 0.5f
                    ),
                    radius = this.size.width * 0.5f,
                    center = center
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "AURA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CaramelGold, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "✨", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = track.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextCream)
            }
        }
    }
}
