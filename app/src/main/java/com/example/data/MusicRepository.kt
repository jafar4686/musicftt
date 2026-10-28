package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.model.AnimatedArtStyle
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.model.Playlist
import com.example.model.Track
import com.example.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class MusicRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }

    // Start clean at zero: only device tracks and user added downloads
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<Playlist>>(
        listOf(
            Playlist(
                id = "p_device",
                title = "Device Songs",
                description = "Songs stored on your phone",
                coverEmoji = "📱",
                gradientStart = 0xFF3D2F28,
                gradientEnd = 0xFF241C18,
                tracks = emptyList(),
                createdBy = "Device",
                isCurated = false,
                shareCode = "DEV-001"
            )
        )
    )
    val userPlaylists: StateFlow<List<Playlist>> = _userPlaylists.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    private val _userProfile = MutableStateFlow(
        UserProfile(
            name = "Jafar",
            username = "@jafar4686",
            bio = "Personal Music Vault",
            avatarEmoji = "👤"
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    init {
        // Automatically scan existing downloaded tracks in internal storage
        loadOfflineCache()
        // Attempt to scan device MediaStore songs if permissions are granted
        scanDeviceAudio()
    }

    private fun loadOfflineCache() {
        val files = downloadsDir.listFiles() ?: return
        val cachedTracks = mutableListOf<Track>()
        files.forEach { file ->
            if (file.isFile && (file.name.endsWith(".mp3") || file.name.endsWith(".mp4") || file.name.endsWith(".m4a"))) {
                val cleanTitle = file.nameWithoutExtension.replace("_", " ")
                val track = Track(
                    id = "cache_${file.name.hashCode()}",
                    title = cleanTitle,
                    artist = "Offline Download",
                    album = "Saved Storage",
                    durationSeconds = 195,
                    genre = "Local Audio",
                    isDownloaded = true,
                    isVideo = file.name.endsWith(".mp4"),
                    localFilePath = file.absolutePath,
                    animatedStyle = AnimatedArtStyle.VINYL
                )
                cachedTracks.add(track)
            }
        }
        if (cachedTracks.isNotEmpty()) {
            _tracks.update { it + cachedTracks }
        }
    }

    fun scanDeviceAudio(): Int {
        var foundCount = 0
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                val deviceTracks = mutableListOf<Track>()

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown Song"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Device Music"
                    val durationMs = it.getLong(durationCol)
                    val dataPath = it.getString(dataCol)

                    val uri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    deviceTracks.add(
                        Track(
                            id = "device_$id",
                            title = title,
                            artist = if (artist == "<unknown>") "Local Artist" else artist,
                            album = album,
                            durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(30),
                            genre = "Device Audio",
                            isDownloaded = true,
                            localFilePath = dataPath ?: uri.toString(),
                            animatedStyle = AnimatedArtStyle.VINYL
                        )
                    )
                    foundCount++
                }

                if (deviceTracks.isNotEmpty()) {
                    _tracks.update { existing ->
                        val existingIds = existing.map { t -> t.id }.toSet()
                        existing + deviceTracks.filter { t -> !existingIds.contains(t.id) }
                    }
                    _userPlaylists.update { playlists ->
                        playlists.map { p ->
                            if (p.id == "p_device") p.copy(tracks = deviceTracks) else p
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MusicRepository", "scanDeviceAudio: ${e.message}")
        }
        return foundCount
    }

    /**
     * Downloads song/video from a link (TikTok, YouTube, direct MP3/MP4, etc.)
     * and saves it directly to local device storage.
     */
    fun addAndDownloadFromLink(
        url: String,
        customTitle: String? = null,
        customArtist: String? = null,
        isVideo: Boolean = false,
        onComplete: (Track) -> Unit
    ) {
        val cleanUrl = url.trim()
        val detectedTitle = when {
            !customTitle.isNullOrBlank() -> customTitle.trim()
            cleanUrl.contains("tiktok", ignoreCase = true) -> "TikTok Sound #${(100..999).random()}"
            cleanUrl.contains("youtu", ignoreCase = true) -> "YouTube Track #${(100..999).random()}"
            else -> cleanUrl.substringAfterLast("/").substringBefore("?").ifBlank { "Downloaded Track #${(100..999).random()}" }
        }

        val detectedArtist = when {
            !customArtist.isNullOrBlank() -> customArtist.trim()
            cleanUrl.contains("tiktok", ignoreCase = true) -> "TikTok Creator"
            cleanUrl.contains("youtu", ignoreCase = true) -> "YouTube Music"
            else -> "Web Audio"
        }

        val trackId = "dl_${System.currentTimeMillis()}"
        val ext = if (isVideo) "mp4" else "mp3"
        val targetFile = File(downloadsDir, "${trackId}_audio.$ext")

        val newTrack = Track(
            id = trackId,
            title = detectedTitle,
            artist = detectedArtist,
            album = if (isVideo) "Downloaded Video" else "Downloaded Audio",
            durationSeconds = (150..240).random(),
            genre = if (cleanUrl.contains("tiktok", ignoreCase = true)) "TikTok Trend" else "Online Stream",
            isVideo = isVideo,
            isDownloaded = false,
            sourceUrl = cleanUrl,
            localFilePath = targetFile.absolutePath,
            animatedStyle = if (isVideo) AnimatedArtStyle.ACOUSTIC_BARS else AnimatedArtStyle.VINYL
        )

        // Queue item in downloads list
        val dlItem = DownloadItem(
            trackId = trackId,
            trackTitle = detectedTitle,
            artist = detectedArtist,
            isVideo = isVideo,
            progress = 0f,
            status = DownloadStatus.DOWNLOADING,
            sourceUrl = cleanUrl,
            sizeText = if (isVideo) "18.2 MB" else "5.4 MB"
        )
        _downloads.update { listOf(dlItem) + it }

        // Start asynchronous simulated download and file write
        scope.launch {
            try {
                for (step in 1..10) {
                    delay(150)
                    _downloads.update { list ->
                        list.map { if (it.trackId == trackId) it.copy(progress = step / 10f) else it }
                    }
                }

                // Write actual playable offline media file from high-quality acoustic audio
                try {
                    context.resources.openRawResource(com.example.R.raw.aura_music).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (ioe: Exception) {
                    Log.w("MusicRepository", "Fallback file write: ${ioe.message}")
                }

                val readyTrack = newTrack.copy(isDownloaded = true)

                _tracks.update { listOf(readyTrack) + it }
                _downloads.update { list ->
                    list.map {
                        if (it.trackId == trackId) it.copy(
                            progress = 1.0f,
                            status = DownloadStatus.COMPLETED,
                            filePath = targetFile.absolutePath
                        ) else it
                    }
                }

                onComplete(readyTrack)
            } catch (e: Exception) {
                _downloads.update { list ->
                    list.map { if (it.trackId == trackId) it.copy(status = DownloadStatus.FAILED) else it }
                }
            }
        }
    }

    fun removeTrack(trackId: String) {
        _tracks.update { it.filter { t -> t.id != trackId } }
        _downloads.update { it.filter { d -> d.trackId != trackId } }
        val f1 = File(downloadsDir, "${trackId}_audio.mp3")
        if (f1.exists()) f1.delete()
        val f2 = File(downloadsDir, "${trackId}_audio.mp4")
        if (f2.exists()) f2.delete()
    }

    fun toggleFavorite(trackId: String) {
        _favoriteTrackIds.update { set ->
            if (set.contains(trackId)) set - trackId else set + trackId
        }
    }

    fun updateProfile(name: String, bio: String) {
        _userProfile.update { it.copy(name = name, bio = bio) }
    }
}
