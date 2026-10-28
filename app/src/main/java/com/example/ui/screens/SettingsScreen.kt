package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserProfile
import com.example.ui.theme.BorderWarm
import com.example.ui.theme.CardElevated
import com.example.ui.theme.CaramelGold
import com.example.ui.theme.DeepWarmBrown
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.TextCream
import com.example.ui.theme.TextMutedSand
import com.example.ui.theme.WarmCardSurface

@Composable
fun SettingsScreen(
    userProfile: UserProfile,
    isDynamicIslandEnabled: Boolean,
    onToggleDynamicIsland: (Boolean) -> Unit,
    storageSizeFormatted: String,
    onScanDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Screen Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "SETTINGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CaramelGold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Preferences & Profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCream
                )
            }
        }

        // Section: User Profile Login (Picture in a square with brown background)
        item {
            SettingsSectionTitle(title = "Account & Profile")

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DeepWarmBrown,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
                    .testTag("user_profile_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile picture in a neat SQUARE with brown background
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(10.dp)) // Square frame with subtle soft corners
                            .background(WarmCardSurface)
                            .border(1.5.dp, CaramelGold, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = "👤",
                            fontSize = 32.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = userProfile.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCream
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = CaramelGold,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = userProfile.username,
                            fontSize = 12.sp,
                            color = CaramelGold,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = userProfile.bio,
                            fontSize = 11.sp,
                            color = TextMutedSand
                        )
                    }
                }
            }
        }

        // Section: Dynamic Island & Exit Controls
        item {
            Spacer(modifier = Modifier.height(14.dp))
            SettingsSectionTitle(title = "Playback & Dynamic Island")

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DeepWarmBrown,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WarmCardSurface)
                        ) {
                            Icon(imageVector = Icons.Default.Vibration, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Dynamic Island Wave Capsule", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextCream)
                            Text(
                                text = "Live animated sound wave pill at top of screen; tap to expand playback controls",
                                fontSize = 11.sp,
                                color = TextMutedSand
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = isDynamicIslandEnabled,
                        onCheckedChange = onToggleDynamicIsland,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EspressoDark,
                            checkedTrackColor = CaramelGold,
                            uncheckedThumbColor = TextMutedSand,
                            uncheckedTrackColor = WarmCardSurface
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DeepWarmBrown,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(WarmCardSurface)
                    ) {
                        Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Notification Bar Controls", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextCream)
                        Text(text = "Control playback smoothly while using other apps", fontSize = 11.sp, color = TextMutedSand)
                    }
                }
            }
        }

        // Section: Device Audio & Storage
        item {
            Spacer(modifier = Modifier.height(14.dp))
            SettingsSectionTitle(title = "Device Storage & Scan")

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DeepWarmBrown,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(WarmCardSurface)
                        ) {
                            Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Device Music Scanner", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextCream)
                            Text(text = "Storage: $storageSizeFormatted", fontSize = 11.sp, color = TextMutedSand)
                        }
                    }

                    OutlinedButton(
                        onClick = onScanDevice,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CaramelGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CaramelGold),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: App Version & GitHub
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DeepWarmBrown,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .border(1.dp, BorderWarm, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = CaramelGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Aura Music Player", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextCream)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(CaramelGold.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "v2.1.0 (Version 2)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CaramelGold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Repository: jafar4686/musicftt\nClean warm brown aesthetic with zero initial state, link downloader, and Dynamic Island controls.",
                        fontSize = 11.sp,
                        color = TextMutedSand,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = CaramelGold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}
