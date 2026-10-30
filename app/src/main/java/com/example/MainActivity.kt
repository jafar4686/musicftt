package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MusicViewModel
import com.example.ui.components.AddSongDialog
import com.example.ui.components.FullPlayerModal
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.SongsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MusicViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = isDarkMode) {
                val context = LocalContext.current

                var isSplashActive by remember { mutableStateOf(true) }
                var hasPermissions by remember {
                    val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                    } else {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }
                    mutableStateOf(audioPerm)
                }

                // Launcher for audio and notifications
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { permissions ->
                        val readGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
                        } else {
                            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                        }
                        if (readGranted) {
                            hasPermissions = true
                            viewModel.scanDeviceAudio()
                        }
                    }
                )

                fun requestRequiredPermissions() {
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
                        hasPermissions = true
                        viewModel.scanDeviceAudio()
                    }
                }

                if (isSplashActive) {
                    SplashScreen(
                        onFinished = {
                            isSplashActive = false
                            requestRequiredPermissions()
                        }
                    )
                } else {
                    MainScreenContent(
                        viewModel = viewModel,
                        isDarkMode = isDarkMode,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = { requestRequiredPermissions() }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreenContent(
    viewModel: MusicViewModel,
    isDarkMode: Boolean,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val soundSettings by viewModel.soundSettings.collectAsStateWithLifecycle()
    val showSoundSettings by viewModel.showSoundSettings.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val showAddSongDialog by viewModel.showAddSongDialog.collectAsStateWithLifecycle()
    val snackMessage by viewModel.snackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackMessage) {
        snackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackMessage()
        }
    }

    // Handle back button for full player
    BackHandler(enabled = playbackState.isFullPlayerVisible) {
        viewModel.setFullPlayerVisible(false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Mini Player Bar docked at the bottom with navigation bar insets
            if (playbackState.currentTrack != null && !playbackState.isFullPlayerVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    MiniPlayerBar(
                        playbackState = playbackState,
                        playbackSpeed = soundSettings.playbackSpeed,
                        onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onClick = { viewModel.setFullPlayerVisible(true) }
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
            // Main Song Library View
            SongsScreen(
                tracks = tracks,
                currentTrack = playbackState.currentTrack,
                isPlaying = playbackState.isPlaying,
                isDarkMode = isDarkMode,
                hasPermissions = hasPermissions,
                favoriteTrackIds = favoriteTrackIds,
                playbackSpeed = soundSettings.playbackSpeed,
                onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                onToggleDarkMode = { viewModel.toggleDarkMode() },
                onRequestPermissions = onRequestPermissions,
                onPlayTrack = { tr, list -> viewModel.playTrack(tr, list) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onOpenAddSong = { viewModel.openAddSongDialog() }
            )

            // Fullscreen Player Modal with Rotating Vinyl Disc & Sleeve Folder
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
                        playbackSpeed = soundSettings.playbackSpeed,
                        onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                        isFavorite = isFav,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onPrevious = { viewModel.previous() },
                        onSeek = { viewModel.seekTo(it) },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onToggleRepeat = { viewModel.toggleRepeat() },
                        onToggleFavorite = { viewModel.toggleFavorite(curTrack.id) },
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
