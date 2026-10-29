package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.Track
import com.example.ui.theme.PlusBlue

/**
 * High-performance, memory-efficient Cover Art component.
 * Uses Coil with hardware bitmap caching to render genuine album cover art
 * smoothly without any frame drops.
 */
@Composable
fun SongCoverArtView(
    track: Track,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shapeRadius: Dp = 10.dp
) {
    val context = LocalContext.current
    val imageShape = RoundedCornerShape(shapeRadius)

    Box(
        modifier = modifier
            .size(size)
            .clip(imageShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val artSource = track.coverUrl ?: track.localFilePath

        if (!artSource.isNullOrEmpty()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artSource)
                    .crossfade(150)
                    .build(),
                contentDescription = "${track.title} Cover Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    DefaultFallbackArt()
                },
                error = {
                    DefaultFallbackArt()
                }
            )
        } else {
            DefaultFallbackArt()
        }
    }
}

@Composable
private fun DefaultFallbackArt() {
    Image(
        painter = painterResource(id = R.drawable.asd_music_logo),
        contentDescription = "Cover Art Fallback",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}
