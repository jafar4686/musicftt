package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.Track
import com.example.ui.AppScreen
import com.example.ui.MusicViewModel
import com.example.ui.components.AddSongDialog
import com.example.ui.components.DynamicIslandOverlay
import com.example.ui.components.FullPlayerModal
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SongsScreen
import com.example.ui.theme.BorderWarm
import com.example.ui.theme.CaramelGold
import com.example.ui.theme.CocoaBrown
import com.example.ui.theme.DeepWarmBrown
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextCream
import com.example.ui.theme.TextMutedSand

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: MusicViewModel = viewModel()
                val context = LocalContext.current

                // Request Audio and Notification permissions
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { permissions ->
                        val readGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
                        } else {
                            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                        }
                        if (readGranted) {
                            viewModel.scanDeviceAudio()
                        }
                    }
                )

                LaunchedEffect(Unit) {
                    val permissionsToRequest = mutableListOf<String>()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                        }
                    } else {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }

                    if (permissionsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    } else {
                        viewModel.scanDeviceAudio()
                    }
                }

                MainScreenContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainScreenContent(viewModel: MusicViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val storageSizeFormatted by viewModel.storageSizeFormatted.collectAsStateWithLifecycle()
    val showAddSongDialog by viewModel.showAddSongDialog.collectAsStateWithLifecycle()
    val snackMessage by viewModel.snackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackMessage) {
        snackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CocoaBrown,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Mini Player Bar
                if (playbackState.currentTrack != null && !playbackState.isFullPlayerVisible) {
                    MiniPlayerBar(
                        playbackState = playbackState,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onClick = { viewModel.setFullPlayerVisible(true) }
                    )
                }

                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = DeepWarmBrown,
                    contentColor = TextCream,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.SONGS,
                        onClick = { viewModel.navigateTo(AppScreen.SONGS) },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Songs") },
                        label = { Text("Songs", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navigationBarColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppScreen.DOWNLOADS,
                        onClick = { viewModel.navigateTo(AppScreen.DOWNLOADS) },
                        icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Downloads") },
                        label = { Text("Downloads", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navigationBarColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppScreen.SETTINGS,
                        onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navigationBarColors()
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.SONGS -> {
                    SongsScreen(
                        tracks = tracks,
                        currentTrack = playbackState.currentTrack,
                        isPlaying = playbackState.isPlaying,
                        favoriteTrackIds = favoriteTrackIds,
                        onPlayTrack = { viewModel.playTrack(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onRemoveTrack = { viewModel.removeTrack(it) },
                        onScanDevice = { viewModel.scanDeviceAudio() },
                        onOpenAddSong = { viewModel.openAddSongDialog() }
                    )
                }
                AppScreen.DOWNLOADS -> {
                    DownloadsScreen(
                        downloads = downloads,
                        tracks = tracks,
                        currentTrack = playbackState.currentTrack,
                        isPlaying = playbackState.isPlaying,
                        storageSizeFormatted = storageSizeFormatted,
                        onPlayTrack = { viewModel.playTrack(it) },
                        onRemoveDownload = { viewModel.removeTrack(it) },
                        onClearAll = { tracks.forEach { viewModel.removeTrack(it.id) } }
                    )
                }
                AppScreen.PLAYLISTS -> {
                    SongsScreen(
                        tracks = tracks,
                        currentTrack = playbackState.currentTrack,
                        isPlaying = playbackState.isPlaying,
                        favoriteTrackIds = favoriteTrackIds,
                        onPlayTrack = { viewModel.playTrack(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onRemoveTrack = { viewModel.removeTrack(it) },
                        onScanDevice = { viewModel.scanDeviceAudio() },
                        onOpenAddSong = { viewModel.openAddSongDialog() }
                    )
                }
                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        userProfile = userProfile,
                        isDynamicIslandEnabled = playbackState.isDynamicIslandEnabled,
                        onToggleDynamicIsland = { viewModel.setDynamicIslandEnabled(it) },
                        storageSizeFormatted = storageSizeFormatted,
                        onScanDevice = { viewModel.scanDeviceAudio() }
                    )
                }
            }

            // Floating Dynamic Island Overlay (Small circle -> expands to island on tap)
            if (playbackState.currentTrack != null && playbackState.isDynamicIslandEnabled && !playbackState.isFullPlayerVisible) {
                DynamicIslandOverlay(
                    playbackState = playbackState,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.next() },
                    onPrevious = { viewModel.previous() },
                    onSeek = { viewModel.seekTo(it) },
                    onToggleExpand = { viewModel.toggleDynamicIslandExpanded() },
                    onOpenFullPlayer = { viewModel.setFullPlayerVisible(true) },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // Fullscreen Player Modal
            AnimatedVisibility(
                visible = playbackState.isFullPlayerVisible && playbackState.currentTrack != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                val curTrack = playbackState.currentTrack
                if (curTrack != null) {
                    val isFav = favoriteTrackIds.contains(curTrack.id)
                    FullPlayerModal(
                        playbackState = playbackState,
                        isFavorite = isFav,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onPrevious = { viewModel.previous() },
                        onSeek = { viewModel.seekTo(it) },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onToggleRepeat = { viewModel.toggleRepeat() },
                        onToggleFavorite = { viewModel.toggleFavorite(curTrack.id) },
                        onStyleSelected = { viewModel.setAnimatedArtStyle(it) },
                        onClose = { viewModel.setFullPlayerVisible(false) }
                    )
                }
            }
        }
    }

    if (showAddSongDialog) {
        AddSongDialog(
            onDismiss = { viewModel.closeAddSongDialog() },
            onDownloadAndPlay = { url, title, artist, isVideo ->
                viewModel.downloadAndPlayLink(url, title, artist, isVideo)
            }
        )
    }
}

@Composable
private fun navigationBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = CaramelGold,
    selectedTextColor = CaramelGold,
    unselectedIconColor = TextMutedSand,
    unselectedTextColor = TextMutedSand,
    indicatorColor = CaramelGold.copy(alpha = 0.15f)
)
