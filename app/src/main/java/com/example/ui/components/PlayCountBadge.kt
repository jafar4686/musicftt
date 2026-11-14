package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusBlue

/**
 * Crisp, high-resolution play count badge displayed directly under a song title.
 */
@Composable
fun PlayCountBadge(
    playCount: Int,
    isCurrent: Boolean = false,
    trackId: String = "",
    modifier: Modifier = Modifier
) {
    val labelText = when (playCount) {
        0 -> "Played 0 times"
        1 -> "Played 1 time"
        else -> "Played $playCount times"
    }
    val hasPlays = playCount > 0
    val accentColor = if (hasPlays || isCurrent) PlusBlue else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (hasPlays || isCurrent) {
        PlusBlue.copy(alpha = 0.11f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val borderColor = if (hasPlays || isCurrent) {
        PlusBlue.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(0.75.dp, borderColor),
        modifier = modifier.then(
            if (trackId.isNotEmpty()) Modifier.testTag("play_count_badge_$trackId") else Modifier
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = "Play count",
                tint = accentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (hasPlays) FontWeight.Bold else FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                    letterSpacing = 0.1.sp
                ),
                color = accentColor,
                maxLines = 1
            )
        }
    }
}
