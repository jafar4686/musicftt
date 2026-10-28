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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnimatedArtStyle
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    isDynamicIslandEnabled: Boolean,
    onToggleDynamicIsland: (Boolean) -> Unit,
    currentArtStyle: AnimatedArtStyle,
    onSelectArtStyle: (AnimatedArtStyle) -> Unit,
    storageSizeFormatted: String,
    onClearAllDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    var notificationControlEnabled by remember { mutableStateOf(true) }
    var highFidelityAudio by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "PREFERENCES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Player & System",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }

        // Section: Playback Controls & Dynamic Island
        item {
            SettingsCategoryHeader(title = "Background Controls & Island")

            SettingsSwitchCard(
                icon = Icons.Default.NotificationsActive,
                iconColor = ElectricCyan,
                title = "Notification Bar Controls",
                subtitle = "Seamless background playback controls with actions when exiting the app",
                checked = notificationControlEnabled,
                onCheckedChange = { notificationControlEnabled = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            SettingsSwitchCard(
                icon = Icons.Default.Vibration,
                iconColor = NeonPurple,
                title = "Dynamic Island Overlay",
                subtitle = "Floating pill at the top for real-time waveform & expanded playback control",
                checked = isDynamicIslandEnabled,
                onCheckedChange = onToggleDynamicIsland,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // Section: Sleek Minimalist Dark Aesthetic
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader(title = "Aesthetic & Visuals")

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF141520),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .border(0.5.dp, Color(0xFF26283C), RoundedCornerShape(16.dp))
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
                                .clip(CircleShape)
                                .background(Color(0xFF222436))
                        ) {
                            Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, tint = SoftViolet)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Interface Aesthetic", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                            Text(text = "Sleek Obsidian Pitch Black", fontSize = 12.sp, color = ElectricCyan)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(ElectricCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                    }
                }
            }
        }

        // Section: Audio Quality & Engine
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader(title = "Audio Engine")

            SettingsSwitchCard(
                icon = Icons.Default.GraphicEq,
                iconColor = EmeraldGlow,
                title = "320kbps Harmonic Synthesizer",
                subtitle = "Real-time algorithmic chord synthesis for offline ambient lo-fi / synthwave",
                checked = highFidelityAudio,
                onCheckedChange = { highFidelityAudio = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // Section: Offline Storage
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader(title = "Offline Storage Vault")

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF141520),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .border(0.5.dp, Color(0xFF26283C), RoundedCornerShape(16.dp))
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
                                .clip(CircleShape)
                                .background(Color(0xFF222436))
                        ) {
                            Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = EmeraldGlow)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Local Vault Usage", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                            Text(text = storageSizeFormatted, fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    Button(
                        onClick = onClearAllDownloads,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2A1C24),
                            contentColor = Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(text = "Clear Vault", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // About
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF11121C),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = SoftViolet)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Aura Music Player v1.0", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = "Curated playlists, offline video/audio downloads, animated vinyl & spectrum art, and Dynamic Island controls.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = SoftViolet,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
fun SettingsSwitchCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF141520),
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0xFF26283C), RoundedCornerShape(16.dp))
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
                        .clip(CircleShape)
                        .background(Color(0xFF222436))
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBlack,
                    checkedTrackColor = ElectricCyan,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = Color(0xFF222436)
                )
            )
        }
    }
}
