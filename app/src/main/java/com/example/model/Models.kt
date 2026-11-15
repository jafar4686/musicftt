package com.example.model

enum class AnimatedArtStyle(val displayName: String, val iconDescription: String) {
    VINYL("Turntable Vinyl", "Warm vintage analog vinyl record with smooth rotation"),
    MINIMAL_WAVE("Sound Waveform", "Clean acoustic audio pulse waveform"),
    ACOUSTIC_BARS("Equalizer Spectrum", "Tidy warm frequency bars"),
    ELEGANT_GLOW("Warm Aura", "Soft amber and caramel radial glow")
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Device Music",
    val durationSeconds: Int = 180,
    val genre: String = "Acoustic",
    val coverUrl: String? = null,
    val animatedStyle: AnimatedArtStyle = AnimatedArtStyle.VINYL,
    val isVideo: Boolean = false,
    val isDownloaded: Boolean = true,
    val downloadSizeBytes: Long = 5_800_000L,
    val localFilePath: String? = null,
    val sourceUrl: String? = null,
    val bpm: Int = 110,
    val musicalKey: String = "G Major",
    val playCount: Int = 0
) {
    val durationFormatted: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

enum class AppThemeMode(val displayName: String) {
    DAY("Day"),
    MORNING("Morning"),
    DARK("Dark")
}

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val coverEmoji: String = "🎵",
    val gradientStart: Long = 0xFF3D2F28,
    val gradientEnd: Long = 0xFF241C18,
    val tracks: List<Track> = emptyList(),
    val createdBy: String = "You",
    val isCurated: Boolean = false,
    val shareCode: String = "VIBE-001",
    val likesCount: Int = 0,
    val isLiked: Boolean = false
) {
    val formattedDuration: String
        get() = "${tracks.sumOf { it.durationSeconds } / 60} mins • ${tracks.size} tracks"
}

data class UserProfile(
    val name: String = "Jafar",
    val username: String = "@jafar4686",
    val bio: String = "Music enthusiast & curator",
    val avatarEmoji: String = "🎧",
    val avatarUrl: String? = null
)

data class DownloadItem(
    val trackId: String,
    val trackTitle: String,
    val artist: String,
    val isVideo: Boolean,
    val progress: Float,
    val status: DownloadStatus,
    val sourceUrl: String? = null,
    val filePath: String? = null,
    val sizeText: String = "5.8 MB"
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
    val isDynamicIslandEnabled: Boolean = true,
    val isDynamicIslandExpanded: Boolean = false,
    val isFullPlayerVisible: Boolean = false
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class RepeatMode {
    OFF, ONE, ALL
}

enum class EqualizerPreset(val title: String) {
    NORMAL("Normal"),
    BASS_BOOST("Bass Boost"),
    ROCK("Rock"),
    POP("Pop"),
    HIP_HOP("Hip Hop"),
    JAZZ("Jazz"),
    CLASSICAL("Classical"),
    DANCE("Dance"),
    ACOUSTIC("Acoustic"),
    FLAT("Flat")
}

data class LarkSoundSettings(
    val isEnabled: Boolean = true,
    val selectedPreset: EqualizerPreset = EqualizerPreset.BASS_BOOST,
    // 5 bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (level in dB from -10 to +10)
    val bandLevels: List<Int> = listOf(4, 2, 0, 3, 5),
    val bassBoostPercent: Int = 45, // 0 - 100
    val virtualizer3DPercent: Int = 30, // 0 - 100
    val playbackSpeed: Float = 1.0f, // 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x
    val reverbEffect: String = "Studio" // Off, Studio, Club, Hall, Concert
)

