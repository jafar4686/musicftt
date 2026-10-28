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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.Track
import com.example.ui.AppScreen
import com.example.ui.MusicViewModel
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.DynamicIslandOverlay
import com.example.ui.components.FullPlayerModal
import com.example.ui.components.ImportCodeDialog
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SocialScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: MusicViewModel = viewModel()
                val context = LocalContext.current

                // Request POST_NOTIFICATIONS permission for Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { /* Permission handled */ }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // Handle deep link if app launched with auramusic://playlist?code=...
                    intent?.data?.let { uri ->
                        val code = uri.getQueryParameter("code")
                        if (!code.isNullOrBlank()) {
                            viewModel.importPlaylistByCode(code)
                        }
                    }
                }

                MainScreenContent(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun MainScreenContent(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val friendsActivity by viewModel.friendsActivity.collectAsStateWithLifecycle()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val selectedGenreFilter by viewModel.selectedGenreFilter.collectAsStateWithLifecycle()
    val storageSizeFormatted by viewModel.offlineStorageSizeFormatted.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.showCreatePlaylistDialog.collectAsStateWithLifecycle()
    val showImportDialog by viewModel.showImportCodeDialog.collectAsStateWithLifecycle()
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
        containerColor = ObsidianBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Mini Player Bar (shown when full player is not visible)
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
                    containerColor = Color(0xFF0E0F17),
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.DISCOVER && selectedPlaylist == null,
                        onClick = {
                            viewModel.selectPlaylist(null)
                            viewModel.navigateTo(AppScreen.DISCOVER)
                        },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                        label = { Text("Discover", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navigationBarItemColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppScreen.PLAYLISTS || selectedPlaylist != null,
                        onClick = { viewModel.navigateTo(AppScreen.PLAYLISTS) },
                        icon = { Icon(Icons.Default.QueueMusic, contentDescription = "Playlists") },
                        label = { Text("Playlists", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navigationBarItemColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppScreen.DOWNLOADS && selectedPlaylist == null,
                        onClick = {
                            viewModel.selectPlaylist(null)
                            viewModel.navigateTo(AppScreen.DOWNLOADS)
                        },
                        icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Offline") },
                        label = { Text("Offline", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navigationBarItemColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppScreen.SOCIAL && selectedPlaylist == null,
                        onClick = {
                            viewModel.selectPlaylist(null)
                            viewModel.navigateTo(AppScreen.SOCIAL)
                        },
                        icon = { Icon(Icons.Default.Group, contentDescription = "Friends") },
                        label = { Text("Friends", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navigationBarItemColors()
                    )

                    NavigationBarItem(
                        selected = currentScreen == AppScreen.SETTINGS && selectedPlaylist == null,
                        onClick = {
                            viewModel.selectPlaylist(null)
                            viewModel.navigateTo(AppScreen.SETTINGS)
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navigationBarItemColors()
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
            // Main Content Screens
            when (currentScreen) {
                AppScreen.DISCOVER -> {
                    DiscoverScreen(
                        playlists = allPlaylists,
                        tracks = tracks,
                        currentTrack = playbackState.currentTrack,
                        isPlaying = playbackState.isPlaying,
                        favoriteTrackIds = favoriteTrackIds,
                        downloads = downloads,
                        friendsActivity = friendsActivity,
                        selectedGenre = selectedGenreFilter,
                        onSelectGenre = { viewModel.setGenreFilter(it) },
                        onSelectPlaylist = {
                            viewModel.selectPlaylist(it)
                            viewModel.navigateTo(AppScreen.PLAYLISTS)
                        },
                        onPlayTrack = { viewModel.playTrack(it) },
                        onToggleFavorite = { viewModel.toggleFavoriteTrack(it) },
                        onDownloadAudio = { viewModel.downloadTrack(it, asVideo = false) },
                        onDownloadVideo = { viewModel.downloadTrack(it, asVideo = true) },
                        onRemoveDownload = { viewModel.removeDownload(it) },
                        onSharePlaylist = { viewModel.sharePlaylist(context, it) }
                    )
                }
                AppScreen.PLAYLISTS -> {
                    PlaylistsScreen(
                        allPlaylists = allPlaylists,
                        userPlaylists = userPlaylists,
                        selectedPlaylist = selectedPlaylist,
                        currentTrack = playbackState.currentTrack,
                        isPlaying = playbackState.isPlaying,
                        favoriteTrackIds = favoriteTrackIds,
                        downloads = downloads,
                        onSelectPlaylist = { viewModel.selectPlaylist(it) },
                        onOpenCreateDialog = { viewModel.openCreatePlaylistDialog() },
                        onOpenImportDialog = { viewModel.openImportCodeDialog() },
                        onPlayTrack = { viewModel.playTrack(it) },
                        onPlayPlaylist = { playlist, startIndex -> viewModel.playPlaylist(playlist, startIndex) },
                        onToggleLikePlaylist = { viewModel.toggleLikePlaylist(it) },
                        onToggleFavorite = { viewModel.toggleFavoriteTrack(it) },
                        onDownloadAudio = { viewModel.downloadTrack(it, asVideo = false) },
                        onDownloadVideo = { viewModel.downloadTrack(it, asVideo = true) },
                        onRemoveDownload = { viewModel.removeDownload(it) },
                        onSharePlaylist = { viewModel.sharePlaylist(context, it) }
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
                        onRemoveDownload = { viewModel.removeDownload(it) },
                        onClearAll = { viewModel.clearAllDownloads() }
                    )
                }
                AppScreen.SOCIAL -> {
                    SocialScreen(
                        friendsActivity = friendsActivity,
                        onTuneIn = { viewModel.playTrack(it) },
                        onImportSharedPlaylist = { viewModel.importPlaylistByCode(it) },
                        onShareMyVibe = {
                            val p = userPlaylists.firstOrNull() ?: allPlaylists.first()
                            viewModel.sharePlaylist(context, p)
                        }
                    )
                }
                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        isDynamicIslandEnabled = playbackState.isDynamicIslandVisible,
                        onToggleDynamicIsland = { viewModel.setDynamicIslandVisible(it) },
                        currentArtStyle = playbackState.currentArtStyle,
                        onSelectArtStyle = { viewModel.setAnimatedArtStyle(it) },
                        storageSizeFormatted = storageSizeFormatted,
                        onClearAllDownloads = { viewModel.clearAllDownloads() }
                    )
                }
            }

            // Floating Dynamic Island at top
            if (playbackState.currentTrack != null && playbackState.isDynamicIslandVisible && !playbackState.isFullPlayerVisible) {
                DynamicIslandOverlay(
                    playbackState = playbackState,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.next() },
                    onPrevious = { viewModel.previous() },
                    onSeek = { viewModel.seekTo(it) },
                    onExpandIsland = { viewModel.setDynamicIslandExpanded(it) },
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
                val currentTrack = playbackState.currentTrack
                if (currentTrack != null) {
                    val isFav = favoriteTrackIds.contains(currentTrack.id)
                    val downloadItem = downloads.find { it.trackId == currentTrack.id }

                    FullPlayerModal(
                        playbackState = playbackState,
                        isFavorite = isFav,
                        downloadItem = downloadItem,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onPrevious = { viewModel.previous() },
                        onSeek = { viewModel.seekTo(it) },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onToggleRepeat = { viewModel.toggleRepeat() },
                        onToggleFavorite = { viewModel.toggleFavoriteTrack(currentTrack.id) },
                        onStyleSelected = { viewModel.setAnimatedArtStyle(it) },
                        onDownloadTrack = { track, isVideo -> viewModel.downloadTrack(track, isVideo) },
                        onShareTrack = {
                            val playlist = selectedPlaylist ?: allPlaylists.first()
                            viewModel.sharePlaylist(context, playlist)
                        },
                        onClose = { viewModel.setFullPlayerVisible(false) }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showCreateDialog) {
        CreatePlaylistDialog(
            availableTracks = tracks,
            onDismiss = { viewModel.closeCreatePlaylistDialog() },
            onCreate = { title, desc, emoji, ids ->
                viewModel.createPlaylist(title, desc, emoji, ids)
            }
        )
    }

    if (showImportDialog) {
        ImportCodeDialog(
            onDismiss = { viewModel.closeImportCodeDialog() },
            onImport = { code ->
                viewModel.importPlaylistByCode(code)
            }
        )
    }
}

@Composable
private fun navigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = ElectricCyan,
    selectedTextColor = ElectricCyan,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary,
    indicatorColor = ElectricCyan.copy(alpha = 0.15f)
)
