package com.example.data

import android.content.Context
import com.example.model.AnimatedArtStyle
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.model.FriendActivity
import com.example.model.Playlist
import com.example.model.Track
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

    // Initial rich catalog of tracks
    private val sampleTracks = listOf(
        Track(
            id = "t1",
            title = "Neon Resonance",
            artist = "Aura Wave & Kaelen",
            album = "Cyber City 2099",
            durationSeconds = 214,
            genre = "Cyberpunk",
            coverUrl = "neon_resonance",
            animatedStyle = AnimatedArtStyle.VINYL,
            bpm = 126,
            musicalKey = "D Minor",
            lyrics = listOf(
                "Gleam through electric haze",
                "Chrome reflections in the rain",
                "Speeding past neon towers",
                "Midnight synthetic power",
                "We ride the frequency tonight"
            )
        ),
        Track(
            id = "t2",
            title = "Midnight Shibuya Rain",
            artist = "Sora Chill",
            album = "Rainy Tape Vol. 3",
            durationSeconds = 188,
            genre = "Chillhop",
            coverUrl = "shibuya_rain",
            animatedStyle = AnimatedArtStyle.NEON_AURA,
            bpm = 84,
            musicalKey = "F Major",
            lyrics = listOf(
                "Gentle drops on glass",
                "Warming cup of tea",
                "Tokyo crosswalk glow",
                "Quiet solace in the night"
            )
        ),
        Track(
            id = "t3",
            title = "Deep Code Protocol",
            artist = "Binary Flow",
            album = "Kernel Zero",
            durationSeconds = 250,
            genre = "Ambient",
            coverUrl = "deep_code",
            animatedStyle = AnimatedArtStyle.FREQUENCY_BARS,
            bpm = 92,
            musicalKey = "C Minor",
            lyrics = listOf(
                "Lines of logic flowing",
                "Recursive thoughts unravel",
                "Synchronized states align",
                "Infinite loop of tranquility"
            )
        ),
        Track(
            id = "t4",
            title = "Cosmic Highway (Visualizer)",
            artist = "Hyperion Drift",
            album = "Starlight Odyssey",
            durationSeconds = 230,
            genre = "Synthwave",
            coverUrl = "cosmic_highway",
            animatedStyle = AnimatedArtStyle.HOLOGRAPHIC_ORBIT,
            isVideo = true,
            bpm = 132,
            musicalKey = "G Minor",
            downloadSizeBytes = 24_500_000L,
            lyrics = listOf(
                "Retrograde thrusters firing",
                "Rays across the event horizon",
                "Speed of light in crimson violet",
                "Never looking back to Earth"
            )
        ),
        Track(
            id = "t5",
            title = "Analog Sunset",
            artist = "Velvet Tape",
            album = "Golden Hour Sessions",
            durationSeconds = 175,
            genre = "Lo-Fi",
            coverUrl = "analog_sunset",
            animatedStyle = AnimatedArtStyle.VINYL,
            bpm = 78,
            musicalKey = "A Minor",
            lyrics = listOf(
                "Dust on 35mm film",
                "Warm saturation of dusk",
                "Hiss of the cassette ribbon",
                "A gentle breeze on memory lane"
            )
        ),
        Track(
            id = "t6",
            title = "Quantum Echoes",
            artist = "NeuroPulse",
            album = "Synapse",
            durationSeconds = 205,
            genre = "Cyberpunk",
            coverUrl = "quantum_echoes",
            animatedStyle = AnimatedArtStyle.NEON_AURA,
            bpm = 120,
            musicalKey = "E Minor",
            lyrics = listOf(
                "Neural signals cascading",
                "Digital pulse through veins",
                "Echoes of artificial dreams"
            )
        )
    )

    // Curated playlists
    private val curatedPlaylists = listOf(
        Playlist(
            id = "p_cyberpunk",
            title = "Cyberpunk Night Drive",
            description = "High-octane dark synthwave & futuristic basslines for night crusades.",
            coverEmoji = "⚡",
            gradientStart = 0xFF7C4DFF,
            gradientEnd = 0xFF00E5FF,
            tracks = listOf(sampleTracks[0], sampleTracks[3], sampleTracks[5]),
            createdBy = "Aura Editorial",
            isCurated = true,
            shareCode = "CYBER-99",
            likesCount = 1420
        ),
        Playlist(
            id = "p_lofi",
            title = "Deep Code & Flow State",
            description = "Hypnotic ambient frequencies & lo-fi beats designed for focus and flow.",
            coverEmoji = "💻",
            gradientStart = 0xFF00C853,
            gradientEnd = 0xFF00B0FF,
            tracks = listOf(sampleTracks[1], sampleTracks[2], sampleTracks[4]),
            createdBy = "Aura Editorial",
            isCurated = true,
            shareCode = "CODE-404",
            likesCount = 2890
        ),
        Playlist(
            id = "p_visual",
            title = "Animated Video Essentials",
            description = "Curated video tracks with dynamic visualizer motion and offline video downloads.",
            coverEmoji = "🎬",
            gradientStart = 0xFFFF2A85,
            gradientEnd = 0xFF7C4DFF,
            tracks = listOf(sampleTracks[3], sampleTracks[0]),
            createdBy = "Visual Lab",
            isCurated = true,
            shareCode = "MOTION-88",
            likesCount = 940
        )
    )

    // User created playlists
    private val _userPlaylists = MutableStateFlow<List<Playlist>>(
        listOf(
            Playlist(
                id = "p_fav",
                title = "My Vibe Library",
                description = "Personal curated selection of favorite tracks.",
                coverEmoji = "💜",
                gradientStart = 0xFF9C27B0,
                gradientEnd = 0xFFE91E63,
                tracks = listOf(sampleTracks[0], sampleTracks[1]),
                createdBy = "You",
                isCurated = false,
                shareCode = "MINE-001",
                likesCount = 3
            )
        )
    )
    val userPlaylists: StateFlow<List<Playlist>> = _userPlaylists.asStateFlow()

    // All available playlists (curated + user)
    private val _allPlaylists = MutableStateFlow<List<Playlist>>(curatedPlaylists + _userPlaylists.value)
    val allPlaylists: StateFlow<List<Playlist>> = _allPlaylists.asStateFlow()

    // Download tracking
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    // Tracks state (with download status reflected)
    private val _tracks = MutableStateFlow<List<Track>>(sampleTracks)
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    // Social Friends Activity
    private val _friendsActivity = MutableStateFlow<List<FriendActivity>>(
        listOf(
            FriendActivity(
                id = "f1",
                friendName = "Maya Lin",
                avatarEmoji = "🎧",
                statusText = "Vibing in dark mode",
                currentTrack = sampleTracks[0],
                listeningWithCount = 4,
                isLive = true,
                sharedPlaylistCode = "CYBER-99"
            ),
            FriendActivity(
                id = "f2",
                friendName = "Liam Vance",
                avatarEmoji = "🚀",
                statusText = "Coding with Ambient beats",
                currentTrack = sampleTracks[2],
                listeningWithCount = 2,
                isLive = true,
                sharedPlaylistCode = "CODE-404"
            ),
            FriendActivity(
                id = "f3",
                friendName = "Chloe Chen",
                avatarEmoji = "✨",
                statusText = "Downloaded Tokyo Rain offline",
                currentTrack = sampleTracks[1],
                listeningWithCount = 0,
                isLive = false
            )
        )
    )
    val friendsActivity: StateFlow<List<FriendActivity>> = _friendsActivity.asStateFlow()

    // Favorite track IDs
    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(setOf("t1", "t2"))
    val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    fun createPlaylist(title: String, description: String, emoji: String, initialTrackIds: List<String> = emptyList()): Playlist {
        val selectedTracks = _tracks.value.filter { initialTrackIds.contains(it.id) }
        val randomSuffix = (100..999).random()
        val code = "VIBE-$randomSuffix"

        val gradients = listOf(
            0xFF8B5CF6 to 0xFF00E5FF,
            0xFFFF2A85 to 0xFFFFB300,
            0xFF00E676 to 0xFF00B0FF,
            0xFF9C27B0 to 0xFF673AB7
        )
        val selectedGradient = gradients.random()

        val newPlaylist = Playlist(
            id = "user_p_${System.currentTimeMillis()}",
            title = title.ifBlank { "New Mix #$randomSuffix" },
            description = description.ifBlank { "Curated by you" },
            coverEmoji = emoji.ifBlank { "🎵" },
            gradientStart = selectedGradient.first,
            gradientEnd = selectedGradient.second,
            tracks = selectedTracks,
            createdBy = "You",
            isCurated = false,
            shareCode = code,
            likesCount = 1
        )

        _userPlaylists.update { listOf(newPlaylist) + it }
        _allPlaylists.update { listOf(newPlaylist) + it }
        return newPlaylist
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        _userPlaylists.update { list ->
            list.map { p ->
                if (p.id == playlistId && !p.tracks.any { it.id == track.id }) {
                    p.copy(tracks = p.tracks + track)
                } else p
            }
        }
        _allPlaylists.update { list ->
            list.map { p ->
                if (p.id == playlistId && !p.tracks.any { it.id == track.id }) {
                    p.copy(tracks = p.tracks + track)
                } else p
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        _userPlaylists.update { list ->
            list.map { p ->
                if (p.id == playlistId) {
                    p.copy(tracks = p.tracks.filter { it.id != trackId })
                } else p
            }
        }
        _allPlaylists.update { list ->
            list.map { p ->
                if (p.id == playlistId) {
                    p.copy(tracks = p.tracks.filter { it.id != trackId })
                } else p
            }
        }
    }

    fun importPlaylistByCode(code: String): Playlist? {
        val cleanCode = code.trim().uppercase()
        // Check existing playlists first
        val existing = _allPlaylists.value.find { it.shareCode.equals(cleanCode, ignoreCase = true) }
        if (existing != null) {
            // Already imported or present
            return existing
        }

        // Generate imported playlist from code
        val randomTracks = _tracks.value.shuffled().take((3..5).random())
        val imported = Playlist(
            id = "imported_${System.currentTimeMillis()}",
            title = "Shared Mix ($cleanCode)",
            description = "Shared playlist imported via code $cleanCode",
            coverEmoji = "🎁",
            gradientStart = 0xFF7C4DFF,
            gradientEnd = 0xFFFF007F,
            tracks = randomTracks,
            createdBy = "Friend",
            isCurated = false,
            shareCode = cleanCode,
            likesCount = (10..80).random()
        )
        _userPlaylists.update { listOf(imported) + it }
        _allPlaylists.update { listOf(imported) + it }
        return imported
    }

    fun toggleLikePlaylist(playlistId: String) {
        _allPlaylists.update { list ->
            list.map { p ->
                if (p.id == playlistId) {
                    val newLiked = !p.isLiked
                    p.copy(isLiked = newLiked, likesCount = if (newLiked) p.likesCount + 1 else p.likesCount - 1)
                } else p
            }
        }
    }

    fun toggleFavoriteTrack(trackId: String) {
        _favoriteTrackIds.update { set ->
            if (set.contains(trackId)) set - trackId else set + trackId
        }
    }

    fun downloadTrack(track: Track, asVideo: Boolean = false) {
        val existingDownload = _downloads.value.find { it.trackId == track.id }
        if (existingDownload != null && existingDownload.status == DownloadStatus.COMPLETED) {
            return
        }

        val downloadItem = DownloadItem(
            trackId = track.id,
            trackTitle = track.title,
            artist = track.artist,
            isVideo = asVideo || track.isVideo,
            progress = 0f,
            status = DownloadStatus.DOWNLOADING,
            sizeText = if (asVideo || track.isVideo) "24.5 MB" else "6.2 MB"
        )

        _downloads.update { list ->
            listOf(downloadItem) + list.filter { it.trackId != track.id }
        }

        scope.launch {
            try {
                // Simulate progressive chunked download with realistic stages
                for (step in 1..10) {
                    delay(120)
                    _downloads.update { list ->
                        list.map {
                            if (it.trackId == track.id) it.copy(progress = step / 10f) else it
                        }
                    }
                }

                // Write actual local offline record file in context.filesDir
                val ext = if (asVideo || track.isVideo) "mp4" else "mp3"
                val file = File(downloadsDir, "${track.id}_offline.$ext")
                file.writeText("AURA_AUDIO_OFFLINE_CACHE:${track.id}:${track.title}")

                _downloads.update { list ->
                    list.map {
                        if (it.trackId == track.id) it.copy(
                            progress = 1.0f,
                            status = DownloadStatus.COMPLETED,
                            filePath = file.absolutePath
                        ) else it
                    }
                }

                _tracks.update { list ->
                    list.map {
                        if (it.id == track.id) it.copy(isDownloaded = true, localFilePath = file.absolutePath) else it
                    }
                }
            } catch (e: Exception) {
                _downloads.update { list ->
                    list.map {
                        if (it.trackId == track.id) it.copy(status = DownloadStatus.FAILED) else it
                    }
                }
            }
        }
    }

    fun removeDownload(trackId: String) {
        _downloads.update { it.filter { item -> item.trackId != trackId } }
        _tracks.update { list ->
            list.map {
                if (it.id == trackId) it.copy(isDownloaded = false, localFilePath = null) else it
            }
        }
        val file = File(downloadsDir, "${trackId}_offline.mp3")
        if (file.exists()) file.delete()
        val videoFile = File(downloadsDir, "${trackId}_offline.mp4")
        if (videoFile.exists()) videoFile.delete()
    }

    fun clearAllDownloads() {
        _downloads.value.forEach { removeDownload(it.trackId) }
    }
}
