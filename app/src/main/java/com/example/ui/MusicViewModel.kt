package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.model.AnimatedArtStyle
import com.example.model.DownloadItem
import com.example.model.PlaybackState
import com.example.model.Playlist
import com.example.model.Track
import com.example.model.UserProfile
import com.example.service.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class AppScreen {
    SONGS, PLAYLISTS, DOWNLOADS, SETTINGS
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    val playbackState: StateFlow<PlaybackState> = PlaybackManager.playbackState

    val tracks: StateFlow<List<Track>> = repository.tracks
    val userPlaylists: StateFlow<List<Playlist>> = repository.userPlaylists
    val downloads: StateFlow<List<DownloadItem>> = repository.downloads
    val favoriteTrackIds: StateFlow<Set<String>> = repository.favoriteTrackIds
    val userProfile: StateFlow<UserProfile> = repository.userProfile

    private val _currentScreen = MutableStateFlow(AppScreen.SONGS)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _showAddSongDialog = MutableStateFlow(false)
    val showAddSongDialog: StateFlow<Boolean> = _showAddSongDialog.asStateFlow()

    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    val storageSizeFormatted: StateFlow<String> = repository.downloads.combine(repository.tracks) { dlList, trackList ->
        val completedCount = dlList.count { it.status == com.example.model.DownloadStatus.COMPLETED }
        val sizeMb = completedCount * 5.4f + dlList.count { it.isVideo && it.status == com.example.model.DownloadStatus.COMPLETED } * 12.8f
        "%.1f MB".format(sizeMb)
    }.stateIn(viewModelScope, SharingStarted.Lazily, "0.0 MB")

    init {
        PlaybackManager.initialize(application)
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun playTrack(track: Track) {
        PlaybackManager.playTrack(track, getApplication())
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
        _snackMessage.value = "Downloading ${if (isVideo) "video" else "audio"} from link..."
        repository.addAndDownloadFromLink(url, title, artist, isVideo) { downloadedTrack ->
            _snackMessage.value = "Downloaded '${downloadedTrack.title}'!"
            playTrack(downloadedTrack)
        }
    }

    fun clearSnackMessage() {
        _snackMessage.value = null
    }
}
