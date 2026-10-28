package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = SurfaceHighlight,
    onPrimaryContainer = ElectricCyan,
    secondary = NeonPurple,
    onSecondary = Color.White,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = SoftViolet,
    tertiary = HologramPink,
    onTertiary = Color.White,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = DeepCharcoal,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = SurfaceHighlight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek minimalist dark aesthetic
    dynamicColor: Boolean = false, // Keep intentional obsidian aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
