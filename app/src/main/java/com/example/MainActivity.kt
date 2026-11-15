package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.MusicViewModel
import com.example.ui.components.AddSongDialog
import com.example.ui.components.FullPlayerModal
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.ShareDownloadFormatDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SongsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PlusBlue

class MainActivity : ComponentActivity() {

    private val musicViewModel: MusicViewModel by viewModels()

    private fun extractAndHandleShareIntent(intent: Intent?): Boolean {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            intent.action = null
            return musicViewModel.handleSharedLink(sharedText)
        }
        return false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractAndHandleShareIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openedFromShare = extractAndHandleShareIntent(intent)

        setContent {
            val viewModel = musicViewModel
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                val context = LocalContext.current

                var isSplashActive by remember { mutableStateOf(!openedFromShare) }
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
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val soundSettings by viewModel.soundSettings.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val showAddSongDialog by viewModel.showAddSongDialog.collectAsStateWithLifecycle()
    val pendingSharedUrl by viewModel.pendingSharedUrl.collectAsStateWithLifecycle()
    val snackMessage by viewModel.snackMessage.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackMessage) {
        snackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackMessage()
        }
    }

    // Handle back button for full player or Settings tab
    BackHandler(enabled = playbackState.isFullPlayerVisible) {
        viewModel.setFullPlayerVisible(false)
    }
    BackHandler(enabled = !playbackState.isFullPlayerVisible && currentScreen == AppScreen.SETTINGS) {
        viewModel.navigateTo(AppScreen.SONGS)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!playbackState.isFullPlayerVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Mini Player Bar floating cleanly above the Sound / Settings bottom bar
                    if (playbackState.currentTrack != null) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            playbackSpeed = soundSettings.playbackSpeed,
                            onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.next() },
                            onClick = { viewModel.setFullPlayerVisible(true) }
                        )
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.SONGS,
                            onClick = { viewModel.navigateTo(AppScreen.SONGS) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Sound"
                                )
                            },
                            label = {
                                Text(
                                    text = "Sound",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PlusBlue,
                                selectedTextColor = PlusBlue,
                                indicatorColor = PlusBlue.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_sound_button")
                        )

                        NavigationBarItem(
                            selected = currentScreen == AppScreen.SETTINGS,
                            onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = {
                                Text(
                                    text = "Settings",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PlusBlue,
                                selectedTextColor = PlusBlue,
                                indicatorColor = PlusBlue.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_settings_button")
                        )
                    }
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
                        hasPermissions = hasPermissions,
                        favoriteTrackIds = favoriteTrackIds,
                        playbackSpeed = soundSettings.playbackSpeed,
                        onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                        onRequestPermissions = onRequestPermissions,
                        onPlayTrack = { tr, list -> viewModel.playTrack(tr, list) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onOpenAddSong = { viewModel.openAddSongDialog() }
                    )
                }
                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        themeMode = themeMode,
                        onSelectThemeMode = { viewModel.setThemeMode(it) }
                    )
                }
            }

            // Fullscreen Player Modal with Rotating Vinyl Disc & Sleeve Folder (fast & snappy navigation)
            AnimatedVisibility(
                visible = playbackState.isFullPlayerVisible && playbackState.currentTrack != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(170, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(170)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
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

    pendingSharedUrl?.let { sharedUrl ->
        ShareDownloadFormatDialog(
            sharedUrl = sharedUrl,
            onSelectFormat = { isVideo ->
                viewModel.downloadSharedLink(sharedUrl, isVideo)
            },
            onDismiss = { viewModel.dismissSharedLinkPrompt() }
        )
    }
}
