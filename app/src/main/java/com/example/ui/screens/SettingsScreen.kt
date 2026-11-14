package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.ThemeTimerSettings
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.MorningAmber
import com.example.ui.theme.PlusBlue
import com.example.ui.theme.PlusViolet

private enum class EditingTimerTarget {
    MORNING, DAY, NIGHT
}

/**
 * Settings screen for ASD MUSIC featuring:
 * - Theme Mode Switcher (Day, Morning, Dark) moved cleanly into Settings
 * - Night & Day Timer scheduler for automatic switching
 * - App Version card (v4.8) at the bottom
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: AppThemeMode = AppThemeMode.DAY,
    themeTimerSettings: ThemeTimerSettings = ThemeTimerSettings(),
    onSelectThemeMode: (AppThemeMode) -> Unit = {},
    onSetAutoTimerEnabled: (Boolean) -> Unit = {},
    onSetMorningStartTime: (Int, Int) -> Unit = { _, _ -> },
    onSetDayStartTime: (Int, Int) -> Unit = { _, _ -> },
    onSetNightStartTime: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var editingTarget by remember { mutableStateOf<EditingTimerTarget?>(null) }

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

        // Theme Mode & Night/Day Timer Card
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
                .testTag("night_day_timer_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Section Header: Theme Mode Switcher
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

                // 3-Way Mode Selector (Day | Morning | Dark)
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

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                Spacer(modifier = Modifier.height(14.dp))

                // Night & Day Auto Timer Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PlusBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Night and Day Timer",
                                tint = PlusBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Night & Day Timer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Auto-switch Morning, Day & Dark by time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = themeTimerSettings.isAutoTimerEnabled,
                        onCheckedChange = onSetAutoTimerEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PlusBlue,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("auto_theme_timer_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Morning Start Time Selector
                TimerTimeRow(
                    title = "Morning Mode Starts",
                    subtitle = "Warm sunrise theme",
                    formattedTime = themeTimerSettings.formatTime(
                        themeTimerSettings.morningStartHour,
                        themeTimerSettings.morningStartMinute
                    ),
                    icon = Icons.Default.WbTwilight,
                    accentColor = MorningAmber,
                    enabled = themeTimerSettings.isAutoTimerEnabled,
                    onClick = { editingTarget = EditingTimerTarget.MORNING },
                    modifier = Modifier.testTag("morning_timer_row")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Day Start Time Selector
                TimerTimeRow(
                    title = "Day Mode Starts",
                    subtitle = "Pure white light theme",
                    formattedTime = themeTimerSettings.formatTime(
                        themeTimerSettings.dayStartHour,
                        themeTimerSettings.dayStartMinute
                    ),
                    icon = Icons.Default.LightMode,
                    accentColor = AccentOrange,
                    enabled = themeTimerSettings.isAutoTimerEnabled,
                    onClick = { editingTarget = EditingTimerTarget.DAY },
                    modifier = Modifier.testTag("day_timer_row")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Night Start Time Selector
                TimerTimeRow(
                    title = "Dark Mode Starts",
                    subtitle = "Sleek dark night theme",
                    formattedTime = themeTimerSettings.formatTime(
                        themeTimerSettings.nightStartHour,
                        themeTimerSettings.nightStartMinute
                    ),
                    icon = Icons.Default.DarkMode,
                    accentColor = PlusViolet,
                    enabled = themeTimerSettings.isAutoTimerEnabled,
                    onClick = { editingTarget = EditingTimerTarget.NIGHT },
                    modifier = Modifier.testTag("night_timer_row")
                )

                AnimatedVisibility(visible = themeTimerSettings.isAutoTimerEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PlusBlue.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val morningStr = themeTimerSettings.formatTime(
                                themeTimerSettings.morningStartHour,
                                themeTimerSettings.morningStartMinute
                            )
                            val dayStr = themeTimerSettings.formatTime(
                                themeTimerSettings.dayStartHour,
                                themeTimerSettings.dayStartMinute
                            )
                            val nightStr = themeTimerSettings.formatTime(
                                themeTimerSettings.nightStartHour,
                                themeTimerSettings.nightStartMinute
                            )
                            Text(
                                text = "Scheduled: Morning $morningStr • Day $dayStr • Dark $nightStr",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = PlusBlue,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                                    .testTag("timer_schedule_summary")
                            )
                        }
                    }
                }
            }
        }

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

    // Material 3 Time Picker Dialog for Morning / Day / Night Start Time
    val currentTarget = editingTarget
    if (currentTarget != null) {
        val (initialHour, initialMinute) = when (currentTarget) {
            EditingTimerTarget.MORNING -> themeTimerSettings.morningStartHour to themeTimerSettings.morningStartMinute
            EditingTimerTarget.DAY -> themeTimerSettings.dayStartHour to themeTimerSettings.dayStartMinute
            EditingTimerTarget.NIGHT -> themeTimerSettings.nightStartHour to themeTimerSettings.nightStartMinute
        }
        val pickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { editingTarget = null },
            title = {
                Text(
                    text = when (currentTarget) {
                        EditingTimerTarget.MORNING -> "Set Morning Mode Start Time"
                        EditingTimerTarget.DAY -> "Set Day Mode Start Time"
                        EditingTimerTarget.NIGHT -> "Set Dark Mode Start Time"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(
                        state = pickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialSelectedContentColor = Color.White,
                            selectorColor = PlusBlue
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (currentTarget) {
                            EditingTimerTarget.MORNING -> onSetMorningStartTime(pickerState.hour, pickerState.minute)
                            EditingTimerTarget.DAY -> onSetDayStartTime(pickerState.hour, pickerState.minute)
                            EditingTimerTarget.NIGHT -> onSetNightStartTime(pickerState.hour, pickerState.minute)
                        }
                        editingTarget = null
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = PlusBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTarget = null }) {
                    Text("Cancel")
                }
            }
        )
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

@Composable
private fun TimerTimeRow(
    title: String,
    subtitle: String,
    formattedTime: String,
    icon: ImageVector,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.4f),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) accentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
