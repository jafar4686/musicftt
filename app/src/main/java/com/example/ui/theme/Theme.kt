package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmBrownColorScheme = darkColorScheme(
    primary = CaramelGold,
    onPrimary = EspressoDark,
    primaryContainer = CardHighlight,
    onPrimaryContainer = WarmBeige,
    secondary = MutedAmber,
    onSecondary = EspressoDark,
    secondaryContainer = WarmCardSurface,
    onSecondaryContainer = WarmBeige,
    tertiary = TerracottaAccent,
    onTertiary = Color.White,
    background = CocoaBrown,
    onBackground = TextCream,
    surface = DeepWarmBrown,
    onSurface = TextCream,
    surfaceVariant = WarmCardSurface,
    onSurfaceVariant = TextMutedSand,
    outline = BorderWarm,
    outlineVariant = BorderSubtleWarm
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WarmBrownColorScheme,
        typography = Typography,
        content = content
    )
}
