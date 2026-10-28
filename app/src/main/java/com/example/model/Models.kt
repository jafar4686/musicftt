package com.example.model

enum class AnimatedArtStyle(val displayName: String, val iconDescription: String) {
    VINYL("Vinyl Spin & Glow", "Classic analog turntable vinyl record with grooves and reflection"),
    NEON_AURA("Neon Aura Waves", "Pulsing radiant cosmic glow waves responsive to tempo"),
    FREQUENCY_BARS("Equalizer Visualizer", "Minimalist multi-band frequency spectrum visualizer"),
    HOLOGRAPHIC_ORBIT("Hologram Matrix", "Futuristic orbiting particle rings with dynamic shimmer")
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val genre: String,
    val coverUrl: String, // Can be resource identifier or mock URL
    val animatedStyle: AnimatedArtStyle = AnimatedArtStyle.VINYL,
    val isVideo: Boolean = false,
    val videoPreviewUrl: String? = null,
    val isDownloaded: Boolean = false,
    val downloadSizeBytes: Long = 6_200_000L, // ~6MB audio or 22MB video
    val localFilePath: String? = null,
    val lyrics: List<String> = emptyList(),
    val bpm: Int = 120,
    val musicalKey: String = "C Minor"
) {
    val durationFormatted: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val coverEmoji: String = "🎵",
    val gradientStart: Long = 0xFF8B5CF6,
    val gradientEnd: Long = 0xFF00E5FF,
    val tracks: List<Track> = emptyList(),
    val createdBy: String = "Curated",
    val isCurated: Boolean = true,
    val shareCode: String = "VIBE-0000",
    val likesCount: Int = 120,
    val isLiked: Boolean = false
) {
    val totalDurationSeconds: Int
        get() = tracks.sumOf { it.durationSeconds }

    val formattedDuration: String
        get() {
            val totalMin = totalDurationSeconds / 60
            return "$totalMin mins • ${tracks.size} tracks"
        }
}

data class FriendActivity(
    val id: String,
    val friendName: String,
    val avatarEmoji: String,
    val statusText: String,
    val currentTrack: Track?,
    val listeningWithCount: Int = 0,
    val isLive: Boolean = true,
    val sharedPlaylistCode: String? = null
)

data class DownloadItem(
    val trackId: String,
    val trackTitle: String,
    val artist: String,
    val isVideo: Boolean,
    val progress: Float, // 0.0f to 1.0f
    val status: DownloadStatus,
    val filePath: String? = null,
    val sizeText: String = "6.4 MB"
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 180_000L,
    val isBuffering: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isShuffle: Boolean = false,
    val currentArtStyle: AnimatedArtStyle = AnimatedArtStyle.VINYL,
    val isDynamicIslandVisible: Boolean = true,
    val isDynamicIslandExpanded: Boolean = false,
    val isFullPlayerVisible: Boolean = false
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class RepeatMode {
    OFF, ONE, ALL
}
