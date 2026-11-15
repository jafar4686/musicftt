package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothRoomManager
import com.example.bluetooth.RoomProtocol
import com.example.data.LinkDetector
import com.example.data.MusicRepository
import com.example.model.AnimatedArtStyle
import com.example.model.AppThemeMode
import com.example.model.BluetoothRoomState
import com.example.model.DownloadItem
import com.example.model.EqualizerPreset
import com.example.model.LarkSoundSettings
import com.example.model.PlaybackState
import com.example.model.Playlist
import com.example.model.RoomPhase
import com.example.model.RoomRole
import com.example.model.RoomSyncCommand
import com.example.model.Track
import com.example.model.UserProfile
import com.example.service.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    SONGS, SETTINGS
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    val playbackState: StateFlow<PlaybackState> = PlaybackManager.playbackState
    val soundSettings: StateFlow<LarkSoundSettings> = PlaybackManager.soundSettings

    val tracks: StateFlow<List<Track>> = repository.tracks
    val userPlaylists: StateFlow<List<Playlist>> = repository.userPlaylists
    val downloads: StateFlow<List<DownloadItem>> = repository.downloads
    val favoriteTrackIds: StateFlow<Set<String>> = repository.favoriteTrackIds
    val userProfile: StateFlow<UserProfile> = repository.userProfile

    private val _themeMode = MutableStateFlow(AppThemeMode.DAY)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false) // Default is pure white as requested!
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _showSoundSettings = MutableStateFlow(false)
    val showSoundSettings: StateFlow<Boolean> = _showSoundSettings.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.SONGS)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _showAddSongDialog = MutableStateFlow(false)
    val showAddSongDialog: StateFlow<Boolean> = _showAddSongDialog.asStateFlow()

    private val _pendingSharedUrl = MutableStateFlow<String?>(null)
    val pendingSharedUrl: StateFlow<String?> = _pendingSharedUrl.asStateFlow()

    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    private val roomManager = BluetoothRoomManager(
        context = application,
        onGuestSyncCommand = { cmd ->
            viewModelScope.launch(Dispatchers.Main) {
                handleGuestSyncCommand(cmd)
            }
        },
        onGuestRoomStarted = {
            viewModelScope.launch(Dispatchers.Main) {
                _snackMessage.value = "Room started! Host is choosing a song..."
            }
        },
        onRoomEnded = { reason ->
            viewModelScope.launch(Dispatchers.Main) {
                _snackMessage.value = reason
            }
        },
        onFindLocalFileByHash = { fileHash, fileSize, title ->
            findMatchingLocalLibraryFile(fileHash, fileSize, title)
        }
    )

    val roomState: StateFlow<BluetoothRoomState> = roomManager.roomState

    val storageSizeFormatted: StateFlow<String> = repository.downloads.combine(repository.tracks) { dlList, _ ->
        val completedCount = dlList.count { it.status == com.example.model.DownloadStatus.COMPLETED }
        val sizeMb = completedCount * 5.4f + dlList.count { it.isVideo && it.status == com.example.model.DownloadStatus.COMPLETED } * 12.8f
        "%.1f MB".format(sizeMb)
    }.stateIn(viewModelScope, SharingStarted.Lazily, "0.0 MB")

    init {
        PlaybackManager.initialize(application)
        PlaybackManager.setOnValidTrackPlayedListener { trackId ->
            val newCount = repository.incrementPlayCount(trackId)
            PlaybackManager.updateCurrentTrackPlayCount(trackId, newCount)
        }

        PlaybackManager.isHostRoomActive = {
            val rs = roomManager.roomState.value
            rs.role == RoomRole.HOST && rs.phase == RoomPhase.PLAYING
        }
        PlaybackManager.onPrepareHostTrackForRoom = { track, speed, onReadyLocal ->
            roomManager.prepareAndPlayTrack(
                track = track,
                positionMs = 0L,
                speed = speed,
                onReadyToPlayLocal = onReadyLocal
            )
        }
        PlaybackManager.onHostRoomPlaybackEvent = { event ->
            when (event) {
                is PlaybackManager.HostRoomPlaybackEvent.Play -> {
                    roomManager.broadcastSyncPlay(
                        track = event.track,
                        positionMs = event.positionMs,
                        speed = event.speed,
                        isPlaying = event.isPlaying
                    )
                }
                is PlaybackManager.HostRoomPlaybackEvent.Pause -> {
                    roomManager.broadcastSyncPause(event.positionMs)
                }
                is PlaybackManager.HostRoomPlaybackEvent.Seek -> {
                    roomManager.broadcastSyncSeek(event.positionMs, event.isPlaying)
                }
            }
        }

        val savedMode = repository.loadManualThemeMode()
        _themeMode.value = savedMode
        _isDarkMode.value = savedMode == AppThemeMode.DARK
    }

    fun refreshBluetoothStatus() {
        roomManager.refreshBluetoothStatus()
    }

    fun createBluetoothRoom() {
        val code = roomManager.createRoom()
        if (code != null) {
            _snackMessage.value = "Room $code created! Share this 5-digit code to join."
        }
    }

    fun joinBluetoothRoom(code: String) {
        roomManager.joinRoom(code)
    }

    fun startBluetoothRoom() {
        roomManager.startRoom()
        _snackMessage.value = "Room started! Pick a song to play for everyone simultaneously."
    }

    fun playTrackInRoom(track: Track) {
        val rs = roomManager.roomState.value
        if (rs.role == RoomRole.HOST && rs.phase == RoomPhase.LOBBY) {
            roomManager.startRoom()
        }
        playTrack(track, tracks.value)
    }

    fun leaveBluetoothRoom() {
        roomManager.leaveOrCloseRoom()
        _snackMessage.value = "Left Bluetooth Room"
    }

    fun clearRoomFeedback() {
        roomManager.clearFeedback()
    }

    private fun handleGuestSyncCommand(cmd: RoomSyncCommand) {
        when (cmd.action) {
            "SYNC_PLAY" -> {
                val resolvedTrack = resolveTrackForSync(cmd)
                viewModelScope.launch(Dispatchers.Main) {
                    if (cmd.syncDelayMs > 0L) {
                        delay(cmd.syncDelayMs.coerceAtMost(400L))
                    }
                    PlaybackManager.applySyncedTrackPlayback(
                        track = resolvedTrack,
                        positionMs = cmd.positionMs,
                        speed = cmd.speed,
                        isPlaying = cmd.isPlaying,
                        context = getApplication()
                    )
                }
            }
            "SYNC_PAUSE" -> {
                PlaybackManager.applySyncedPause(cmd.positionMs, getApplication())
            }
            "SYNC_SEEK" -> {
                PlaybackManager.applySyncedSeek(cmd.positionMs, cmd.isPlaying, getApplication())
            }
        }
    }

    private fun findMatchingLocalLibraryFile(
        fileHash: String,
        fileSize: Long,
        title: String?
    ): String? {
        if (fileHash.isBlank()) return null
        val normTitle = title?.trim()?.lowercase()
        val candidates = tracks.value.filter { tr ->
            val lp = tr.localFilePath
            !lp.isNullOrBlank() && !lp.startsWith("content://")
        }.sortedByDescending { tr ->
            if (!normTitle.isNullOrBlank() && tr.title.trim().lowercase() == normTitle) 1 else 0
        }

        for (candidate in candidates) {
            val file = java.io.File(candidate.localFilePath ?: continue)
            if (file.exists() && (fileSize <= 0L || file.length() == fileSize)) {
                val hash = RoomProtocol.computeFileSha256(file)
                if (hash == fileHash) {
                    return file.absolutePath
                }
            }
        }
        return null
    }

    private fun resolveTrackForSync(cmd: RoomSyncCommand): Track {
        // 1. Always prefer the exact Bluetooth-transferred or SHA-256-verified audio file
        val syncedPath = cmd.localSyncedFilePath
        if (!syncedPath.isNullOrBlank()) {
            return Track(
                id = "room_sync_${cmd.fileHash ?: cmd.trackId ?: System.currentTimeMillis()}",
                title = cmd.title ?: "Shared Room Track",
                artist = cmd.artist ?: "Room Host",
                album = cmd.album ?: "Bluetooth Room Sync",
                durationSeconds = cmd.durationSeconds.coerceAtLeast(10),
                localFilePath = syncedPath,
                sourceUrl = cmd.sourceUrl,
                isDownloaded = true
            )
        }

        // 2. Built-in bundled starter tracks share identical IDs across installs
        val starterId = cmd.trackId
        if (!starterId.isNullOrBlank() && starterId.startsWith("asd_starter_")) {
            tracks.value.firstOrNull { it.id == starterId }?.let { return it }
        }

        // 3. Otherwise stream from remote URL (never collide on foreign device_* IDs!)
        return Track(
            id = "room_sync_${cmd.fileHash ?: cmd.trackId ?: System.currentTimeMillis()}",
            title = cmd.title ?: "Shared Room Track",
            artist = cmd.artist ?: "Room Host",
            album = cmd.album ?: "Bluetooth Room Sync",
            durationSeconds = cmd.durationSeconds.coerceAtLeast(10),
            sourceUrl = cmd.sourceUrl,
            isDownloaded = false
        )
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        _isDarkMode.value = mode == AppThemeMode.DARK
        repository.saveManualThemeMode(mode)
    }

    fun toggleDarkMode() {
        val nextMode = if (_themeMode.value == AppThemeMode.DARK) AppThemeMode.DAY else AppThemeMode.DARK
        setThemeMode(nextMode)
    }

    fun setDarkMode(dark: Boolean) {
        setThemeMode(if (dark) AppThemeMode.DARK else AppThemeMode.DAY)
    }

    fun handleSharedLink(rawText: String?): Boolean {
        val extractedUrl = LinkDetector.extractFirstUrl(rawText)
        return if (extractedUrl != null) {
            _currentScreen.value = AppScreen.SONGS
            _pendingSharedUrl.value = extractedUrl
            true
        } else {
            if (!rawText.isNullOrBlank()) {
                _snackMessage.value = "Could not find a valid media link in the shared text"
            }
            false
        }
    }

    fun dismissSharedLinkPrompt() {
        _pendingSharedUrl.value = null
    }

    fun downloadSharedLink(url: String, isVideo: Boolean) {
        _pendingSharedUrl.value = null
        downloadAndPlayLink(
            url = url,
            title = null,
            artist = null,
            isVideo = isVideo
        )
    }

    fun openSoundSettings() {
        _showSoundSettings.value = true
    }

    fun closeSoundSettings() {
        _showSoundSettings.value = false
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        PlaybackManager.setEqualizerPreset(preset)
    }

    fun setBandLevel(bandIndex: Int, levelDb: Int) {
        PlaybackManager.setBandLevel(bandIndex, levelDb)
    }

    fun setBassBoost(percent: Int) {
        PlaybackManager.setBassBoost(percent)
    }

    fun setVirtualizer(percent: Int) {
        PlaybackManager.setVirtualizer(percent)
    }

    fun setPlaybackSpeed(speed: Float) {
        PlaybackManager.setPlaybackSpeed(speed)
    }

    fun toggleEqualizerEnabled() {
        PlaybackManager.toggleEqualizerEnabled()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun playTrack(track: Track, currentList: List<Track>? = null) {
        val list = currentList ?: tracks.value
        PlaybackManager.playTrack(track, list, getApplication())
    }

    fun togglePlayPause() {
        PlaybackManager.togglePlayPause(getApplication())
    }

    fun next() {
        PlaybackManager.next(getApplication())
    }

    fun previous() {
        PlaybackManager.previous(getApplication())
    }

    fun seekTo(positionMs: Long) {
        PlaybackManager.seekTo(positionMs)
    }

    fun toggleShuffle() {
        PlaybackManager.toggleShuffle()
    }

    fun toggleRepeat() {
        PlaybackManager.toggleRepeat()
    }

    fun setAnimatedArtStyle(style: AnimatedArtStyle) {
        PlaybackManager.setAnimatedArtStyle(style)
    }

    fun toggleDynamicIslandExpanded() {
        PlaybackManager.toggleDynamicIslandExpanded()
    }

    fun setDynamicIslandEnabled(enabled: Boolean) {
        PlaybackManager.setDynamicIslandEnabled(enabled)
    }

    fun setFullPlayerVisible(visible: Boolean) {
        PlaybackManager.setFullPlayerVisible(visible)
    }

    fun toggleFavorite(trackId: String) {
        repository.toggleFavorite(trackId)
    }

    fun removeTrack(trackId: String) {
        repository.removeTrack(trackId)
        _snackMessage.value = "Track removed from library"
    }

    fun scanDeviceAudio() {
        val count = repository.scanDeviceAudio()
        _snackMessage.value = if (count > 0) "Found $count audio files on device" else "No audio files detected on device storage"
    }

    fun openAddSongDialog() {
        _showAddSongDialog.value = true
    }

    fun closeAddSongDialog() {
        _showAddSongDialog.value = false
    }

    fun downloadAndPlayLink(url: String, title: String?, artist: String?, isVideo: Boolean) {
        _showAddSongDialog.value = false
        _snackMessage.value = "Downloading ${if (isVideo) "video" else "audio"} to device storage..."
        repository.addAndDownloadFromLink(
            url = url,
            customTitle = title,
            customArtist = artist,
            isVideo = isVideo,
            onComplete = { downloadedTrack ->
                _snackMessage.value = "Saved '${downloadedTrack.title}' to device Music!"
                playTrack(downloadedTrack)
            },
            onError = { err ->
                _snackMessage.value = err
            }
        )
    }

    fun clearSnackMessage() {
        _snackMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        PlaybackManager.onHostRoomPlaybackEvent = null
        PlaybackManager.onPrepareHostTrackForRoom = null
        PlaybackManager.isHostRoomActive = { false }
        roomManager.release()
    }
}
