package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicRepository
import com.example.model.AnimatedArtStyle
import com.example.model.DownloadItem
import com.example.model.FriendActivity
import com.example.model.PlaybackState
import com.example.model.Playlist
import com.example.model.Track
import com.example.service.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class AppScreen {
    DISCOVER, PLAYLISTS, DOWNLOADS, SOCIAL, SETTINGS
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    val playbackState: StateFlow<PlaybackState> = PlaybackManager.playbackState

    val allPlaylists: StateFlow<List<Playlist>> = repository.allPlaylists
    val userPlaylists: StateFlow<List<Playlist>> = repository.userPlaylists
    val tracks: StateFlow<List<Track>> = repository.tracks
    val downloads: StateFlow<List<DownloadItem>> = repository.downloads
    val friendsActivity: StateFlow<List<FriendActivity>> = repository.friendsActivity
    val favoriteTrackIds: StateFlow<Set<String>> = repository.favoriteTrackIds

    private val _currentScreen = MutableStateFlow(AppScreen.DISCOVER)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenreFilter = MutableStateFlow<String?>(null)
    val selectedGenreFilter: StateFlow<String?> = _selectedGenreFilter.asStateFlow()

    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog: StateFlow<Boolean> = _showCreatePlaylistDialog.asStateFlow()

    private val _showImportCodeDialog = MutableStateFlow(false)
    val showImportCodeDialog: StateFlow<Boolean> = _showImportCodeDialog.asStateFlow()

    private val _snackMessage = MutableStateFlow<String?>(null)
    val snackMessage: StateFlow<String?> = _snackMessage.asStateFlow()

    // Offline storage calculation
    val offlineStorageSizeFormatted: StateFlow<String> = repository.downloads.combine(repository.tracks) { downloadsList, _ ->
        val completedCount = downloadsList.count { it.status == com.example.model.DownloadStatus.COMPLETED }
        val sizeMb = completedCount * 6.4f + downloadsList.count { it.isVideo && it.status == com.example.model.DownloadStatus.COMPLETED } * 18.1f
        "%.1f MB".format(sizeMb)
    }.stateIn(viewModelScope, SharingStarted.Lazily, "0.0 MB")

    init {
        PlaybackManager.initialize(application)
        // If initial playlist is available, set queue
        val initialTracks = repository.tracks.value
        if (initialTracks.isNotEmpty()) {
            PlaybackManager.setPlaylist(initialTracks, startIndex = 0, autoPlay = false, context = application)
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setGenreFilter(genre: String?) {
        _selectedGenreFilter.value = if (_selectedGenreFilter.value == genre) null else genre
    }

    fun playTrack(track: Track) {
        PlaybackManager.playTrack(track, getApplication())
    }

    fun playPlaylist(playlist: Playlist, startIndex: Int = 0) {
        if (playlist.tracks.isNotEmpty()) {
            PlaybackManager.setPlaylist(playlist.tracks, startIndex = startIndex, autoPlay = true, context = getApplication())
        }
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

    fun setDynamicIslandExpanded(expanded: Boolean) {
        PlaybackManager.setDynamicIslandExpanded(expanded)
    }

    fun setDynamicIslandVisible(visible: Boolean) {
        PlaybackManager.setDynamicIslandVisible(visible)
    }

    fun setFullPlayerVisible(visible: Boolean) {
        PlaybackManager.setFullPlayerVisible(visible)
    }

    fun toggleLikePlaylist(playlistId: String) {
        repository.toggleLikePlaylist(playlistId)
        // update selected playlist if open
        _selectedPlaylist.update { cur ->
            if (cur?.id == playlistId) {
                cur.copy(isLiked = !cur.isLiked, likesCount = if (!cur.isLiked) cur.likesCount + 1 else cur.likesCount - 1)
            } else cur
        }
    }

    fun toggleFavoriteTrack(trackId: String) {
        repository.toggleFavoriteTrack(trackId)
    }

    fun downloadTrack(track: Track, asVideo: Boolean = false) {
        repository.downloadTrack(track, asVideo)
        _snackMessage.value = if (asVideo) "Downloading video for offline listening..." else "Downloading audio track..."
    }

    fun removeDownload(trackId: String) {
        repository.removeDownload(trackId)
        _snackMessage.value = "Removed from offline downloads"
    }

    fun clearAllDownloads() {
        repository.clearAllDownloads()
        _snackMessage.value = "All offline downloads cleared"
    }

    fun openCreatePlaylistDialog() {
        _showCreatePlaylistDialog.value = true
    }

    fun closeCreatePlaylistDialog() {
        _showCreatePlaylistDialog.value = false
    }

    fun createPlaylist(title: String, description: String, emoji: String, trackIds: List<String>) {
        val created = repository.createPlaylist(title, description, emoji, trackIds)
        _showCreatePlaylistDialog.value = false
        _snackMessage.value = "Created playlist '${created.title}' (Code: ${created.shareCode})"
        _selectedPlaylist.value = created
    }

    fun openImportCodeDialog() {
        _showImportCodeDialog.value = true
    }

    fun closeImportCodeDialog() {
        _showImportCodeDialog.value = false
    }

    fun importPlaylistByCode(code: String) {
        val imported = repository.importPlaylistByCode(code)
        _showImportCodeDialog.value = false
        if (imported != null) {
            _selectedPlaylist.value = imported
            _snackMessage.value = "Imported '${imported.title}' with ${imported.tracks.size} tracks!"
        } else {
            _snackMessage.value = "Invalid or expired share code."
        }
    }

    fun sharePlaylist(context: Context, playlist: Playlist) {
        val shareText = buildString {
            append("🎧 Listen to '${playlist.title}' on Aura Music!\n")
            append("✨ Curated vibe with ${playlist.tracks.size} tracks (${playlist.formattedDuration})\n")
            append("🔑 Share Code: ${playlist.shareCode}\n\n")
            append("Tracks:\n")
            playlist.tracks.take(5).forEachIndexed { i, t ->
                append("${i + 1}. ${t.title} - ${t.artist}\n")
            }
            if (playlist.tracks.size > 5) {
                append("+ ${playlist.tracks.size - 5} more tracks\n")
            }
            append("\nOpen in Aura Music: auramusic://playlist?code=${playlist.shareCode}")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_TITLE, playlist.title)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Playlist with Friends")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun clearSnackMessage() {
        _snackMessage.value = null
    }
}
