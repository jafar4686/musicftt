package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.model.AppThemeMode
import com.example.model.SyncRoomState
import com.example.model.Track
import com.example.ui.components.NetworkRoomCard
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.MorningAmber
import com.example.ui.theme.PlusBlue
import com.example.ui.theme.PlusViolet

/**
 * Settings screen for ASD MUSIC featuring:
 * - Theme Mode Switcher (Day, Morning, Dark)
 * - Multi-Device Network Room ("Listen Together Room")
 * - App Version card (v4.8) at the bottom
 */
@Composable
fun SettingsScreen(
    themeMode: AppThemeMode = AppThemeMode.DAY,
    onSelectThemeMode: (AppThemeMode) -> Unit = {},
    roomState: SyncRoomState = SyncRoomState(),
    tracks: List<Track> = emptyList(),
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    onRefreshNetworkStatus: () -> Unit = {},
    onCreateRoom: () -> Unit = {},
    onJoinRoom: (String, String?) -> Unit = { _, _ -> },
    onStartRoom: () -> Unit = {},
    onPlayTrackInRoom: (Track) -> Unit = {},
    onLeaveRoom: () -> Unit = {},
    onClearRoomError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("settings_screen")
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PlusBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = PlusBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Theme Mode Card (Day, Morning, Dark)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("theme_mode_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PlusBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (themeMode) {
                                AppThemeMode.DAY -> Icons.Default.LightMode
                                AppThemeMode.MORNING -> Icons.Default.WbTwilight
                                AppThemeMode.DARK -> Icons.Default.DarkMode
                            },
                            contentDescription = "Theme Mode",
                            tint = when (themeMode) {
                                AppThemeMode.DAY -> AccentOrange
                                AppThemeMode.MORNING -> MorningAmber
                                AppThemeMode.DARK -> PlusViolet
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Switch between Day, Morning, and Dark modes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeModeChip(
                        label = "Day",
                        icon = Icons.Default.LightMode,
                        accentColor = AccentOrange,
                        selected = themeMode == AppThemeMode.DAY,
                        onClick = { onSelectThemeMode(AppThemeMode.DAY) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_mode_day_button")
                    )
                    ThemeModeChip(
                        label = "Morning",
                        icon = Icons.Default.WbTwilight,
                        accentColor = MorningAmber,
                        selected = themeMode == AppThemeMode.MORNING,
                        onClick = { onSelectThemeMode(AppThemeMode.MORNING) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_mode_morning_button")
                    )
                    ThemeModeChip(
                        label = "Dark",
                        icon = Icons.Default.DarkMode,
                        accentColor = PlusViolet,
                        selected = themeMode == AppThemeMode.DARK,
                        onClick = { onSelectThemeMode(AppThemeMode.DARK) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_mode_dark_button")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Multi-Device Simultaneous Playback via Wi-Fi / Hotspot Room
        NetworkRoomCard(
            roomState = roomState,
            tracks = tracks,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onRefreshNetworkStatus = onRefreshNetworkStatus,
            onCreateRoom = onCreateRoom,
            onJoinRoom = onJoinRoom,
            onStartRoom = onStartRoom,
            onPlayTrackInRoom = onPlayTrackInRoom,
            onLeaveRoom = onLeaveRoom,
            onClearError = onClearRoomError
        )

        Spacer(modifier = Modifier.weight(1f, fill = true))
        Spacer(modifier = Modifier.height(20.dp))

        // Settings Section at the bottom with App Name & Version Number
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("settings_version_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.asd_music_logo),
                        contentDescription = "ASD MUSIC",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, PlusBlue, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ASD MUSIC",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "App Version",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PlusBlue.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = PlusBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = PlusBlue,
                            modifier = Modifier.testTag("settings_app_version_text")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$label Mode",
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
