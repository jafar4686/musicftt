package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.AppThemeMode

private val PureWhiteColorScheme = lightColorScheme(
    primary = PlusBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = PlusBlue,
    secondary = PlusViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = PlusViolet,
    tertiary = AccentOrange,
    onTertiary = Color.White,
    background = PureWhite,
    onBackground = TextPrimaryLight,
    surface = OffWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = SurfaceBorderLight,
    outlineVariant = Color(0xFFF3F4F6)
)

private val WarmMorningColorScheme = lightColorScheme(
    primary = MorningAmber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF92400E),
    secondary = PlusBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = PlusBlue,
    tertiary = AccentOrange,
    onTertiary = Color.White,
    background = MorningCreamBg,
    onBackground = MorningTextPrimary,
    surface = MorningSurface,
    onSurface = MorningTextPrimary,
    surfaceVariant = MorningSurfaceElevated,
    onSurfaceVariant = MorningTextSecondary,
    outline = MorningBorder,
    outlineVariant = Color(0xFFFAF0DC)
)

private val SleekDarkColorScheme = darkColorScheme(
    primary = PlusBlueLight,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFBFDBFE),
    secondary = PlusViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF581C87),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = AccentOrange,
    onTertiary = Color.White,
    background = PureDark,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkBorder,
    outlineVariant = Color(0xFF1E222D)
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.DAY,
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val effectiveMode = if (darkTheme && themeMode == AppThemeMode.DAY) AppThemeMode.DARK else themeMode
    val colorScheme = when (effectiveMode) {
        AppThemeMode.DAY -> PureWhiteColorScheme
        AppThemeMode.MORNING -> WarmMorningColorScheme
        AppThemeMode.DARK -> SleekDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
