package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.audio.AudioEngine
import com.example.model.AnimatedArtStyle
import com.example.model.PlaybackState
import com.example.model.RepeatMode
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PlaybackManager {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _soundSettings = MutableStateFlow(com.example.model.LarkSoundSettings())
    val soundSettings: StateFlow<com.example.model.LarkSoundSettings> = _soundSettings.asStateFlow()

    private var audioEngine: AudioEngine? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentPlaylist: List<Track> = emptyList()
    private var currentIndex: Int = 0
    private var appContext: Context? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (audioEngine == null) {
            audioEngine = AudioEngine(
                context = context.applicationContext,
                onTrackCompleted = {
                    handleTrackCompletion()
                },
                onError = { err ->
                    Log.w("PlaybackManager", "Audio playback notification: $err")
                }
            )
        }
        try {
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuraMusic:PlaybackWakeLock")
        } catch (_: Exception) {}
    }

    private fun handleTrackCompletion() {
        val state = _playbackState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                play(appContext)
            }
            RepeatMode.ALL -> {
                next(appContext)
            }
            RepeatMode.OFF -> {
                if (currentIndex < currentPlaylist.size - 1) {
                    next(appContext)
                } else {
                    pause(appContext)
                    _playbackState.update { it.copy(currentPositionMs = 0L) }
                }
            }
        }
    }

    fun setPlaylist(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true, context: Context? = null) {
        val ctx = context ?: appContext
        currentPlaylist = tracks
        currentIndex = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        if (tracks.isNotEmpty()) {
            val track = tracks[currentIndex]
            _playbackState.update {
                it.copy(
                    currentTrack = track,
                    currentPositionMs = 0L,
                    durationMs = track.durationSeconds * 1000L,
                    currentArtStyle = track.animatedStyle
                )
            }
            if (autoPlay) {
                play(ctx)
            }
        }
    }

    fun playTrack(track: Track, context: Context? = null) {
        val ctx = context ?: appContext
        val foundIndex = currentPlaylist.indexOfFirst { it.id == track.id }
        if (foundIndex != -1) {
            currentIndex = foundIndex
        } else {
            currentPlaylist = listOf(track) + currentPlaylist
            currentIndex = 0
        }

        _playbackState.update {
            it.copy(
                currentTrack = track,
                currentPositionMs = 0L,
                durationMs = track.durationSeconds * 1000L,
                currentArtStyle = track.animatedStyle
            )
        }
        play(ctx)
    }

    fun play(context: Context? = null) {
        val ctx = context ?: appContext
        val track = _playbackState.value.currentTrack ?: return

        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours timeout safety
            }
        } catch (_: Exception) {}

        _playbackState.update { it.copy(isPlaying = true) }
        audioEngine?.playTrack(track)
        startProgressTracking()

        // Trigger Foreground Service for notification bar & background playback
        ctx?.let { c ->
            try {
                val serviceIntent = Intent(c, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_UPDATE_NOTIFICATION
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    c.startForegroundService(serviceIntent)
                } else {
                    c.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.w("PlaybackManager", "Service start handled: ${e.message}")
            }
        }
    }

    fun pause(context: Context? = null) {
        val ctx = context ?: appContext
        _playbackState.update { it.copy(isPlaying = false) }
        audioEngine?.pause()
        stopProgressTracking()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        ctx?.let { c ->
            try {
                val serviceIntent = Intent(c, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_UPDATE_NOTIFICATION
                }
                c.startService(serviceIntent)
            } catch (_: Exception) {}
        }
    }

    fun togglePlayPause(context: Context? = null) {
        if (_playbackState.value.isPlaying) {
            pause(context)
        } else {
            if (_playbackState.value.currentTrack != null) {
                audioEngine?.resume(_playbackState.value.currentTrack)
                _playbackState.update { it.copy(isPlaying = true) }
                startProgressTracking()
                val ctx = context ?: appContext
                ctx?.let { c ->
                    try {
                        val serviceIntent = Intent(c, MusicPlaybackService::class.java).apply {
                            action = MusicPlaybackService.ACTION_UPDATE_NOTIFICATION
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            c.startForegroundService(serviceIntent)
                        } else {
                            c.startService(serviceIntent)
                        }
                    } catch (_: Exception) {}
                }
            } else {
                play(context)
            }
        }
    }

    fun next(context: Context? = null) {
        if (currentPlaylist.isEmpty()) return
        val nextIndex = if (_playbackState.value.isShuffle) {
            currentPlaylist.indices.random()
        } else {
            (currentIndex + 1) % currentPlaylist.size
        }
        currentIndex = nextIndex
        val nextTrack = currentPlaylist[currentIndex]
        _playbackState.update {
            it.copy(
                currentTrack = nextTrack,
                currentPositionMs = 0L,
                durationMs = nextTrack.durationSeconds * 1000L,
                currentArtStyle = nextTrack.animatedStyle
            )
        }
        play(context)
    }

    fun previous(context: Context? = null) {
        if (currentPlaylist.isEmpty()) return
        if (_playbackState.value.currentPositionMs > 3000L) {
            seekTo(0L)
            return
        }
        val prevIndex = if (currentIndex - 1 < 0) currentPlaylist.size - 1 else currentIndex - 1
        currentIndex = prevIndex
        val prevTrack = currentPlaylist[currentIndex]
        _playbackState.update {
            it.copy(
                currentTrack = prevTrack,
                currentPositionMs = 0L,
                durationMs = prevTrack.durationSeconds * 1000L,
                currentArtStyle = prevTrack.animatedStyle
            )
        }
        play(context)
    }

    fun seekTo(positionMs: Long) {
        audioEngine?.seekTo(positionMs)
        _playbackState.update {
            it.copy(currentPositionMs = positionMs.coerceIn(0L, it.durationMs))
        }
    }

    fun toggleShuffle() {
        _playbackState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun toggleRepeat() {
        _playbackState.update {
            val nextRepeat = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            it.copy(repeatMode = nextRepeat)
        }
    }

    fun setAnimatedArtStyle(style: AnimatedArtStyle) {
        _playbackState.update { it.copy(currentArtStyle = style) }
    }

    fun setDynamicIslandExpanded(expanded: Boolean) {
        _playbackState.update { it.copy(isDynamicIslandExpanded = expanded) }
    }

    fun toggleDynamicIslandExpanded() {
        _playbackState.update { it.copy(isDynamicIslandExpanded = !it.isDynamicIslandExpanded) }
    }

    fun setDynamicIslandEnabled(enabled: Boolean) {
        _playbackState.update { it.copy(isDynamicIslandEnabled = enabled) }
    }

    fun setFullPlayerVisible(visible: Boolean) {
        _playbackState.update { it.copy(isFullPlayerVisible = visible) }
    }

    fun updateSoundSettings(settings: com.example.model.LarkSoundSettings) {
        _soundSettings.value = settings
        audioEngine?.applySoundSettings(settings)
    }

    fun setEqualizerPreset(preset: com.example.model.EqualizerPreset) {
        val bands = when (preset) {
            com.example.model.EqualizerPreset.NORMAL -> listOf(0, 0, 0, 0, 0)
            com.example.model.EqualizerPreset.BASS_BOOST -> listOf(6, 4, 1, 2, 4)
            com.example.model.EqualizerPreset.ROCK -> listOf(5, 3, -1, 3, 5)
            com.example.model.EqualizerPreset.POP -> listOf(-1, 2, 5, 2, -2)
            com.example.model.EqualizerPreset.HIP_HOP -> listOf(5, 3, 0, 2, 4)
            com.example.model.EqualizerPreset.JAZZ -> listOf(3, 1, 2, 2, 3)
            com.example.model.EqualizerPreset.CLASSICAL -> listOf(4, 3, 0, 2, 3)
            com.example.model.EqualizerPreset.DANCE -> listOf(5, 4, 2, 3, 4)
            com.example.model.EqualizerPreset.ACOUSTIC -> listOf(3, 2, 3, 2, 1)
            com.example.model.EqualizerPreset.FLAT -> listOf(0, 0, 0, 0, 0)
        }
        val bassBoost = if (preset == com.example.model.EqualizerPreset.BASS_BOOST) 75 else _soundSettings.value.bassBoostPercent
        val updated = _soundSettings.value.copy(
            selectedPreset = preset,
            bandLevels = bands,
            bassBoostPercent = bassBoost
        )
        updateSoundSettings(updated)
    }

    fun setBandLevel(bandIndex: Int, levelDb: Int) {
        val currentBands = _soundSettings.value.bandLevels.toMutableList()
        if (bandIndex in 0 until currentBands.size) {
            currentBands[bandIndex] = levelDb.coerceIn(-10, 10)
            updateSoundSettings(_soundSettings.value.copy(bandLevels = currentBands))
        }
    }

    fun setBassBoost(percent: Int) {
        updateSoundSettings(_soundSettings.value.copy(bassBoostPercent = percent.coerceIn(0, 100)))
    }

    fun setVirtualizer(percent: Int) {
        updateSoundSettings(_soundSettings.value.copy(virtualizer3DPercent = percent.coerceIn(0, 100)))
    }

    fun setPlaybackSpeed(speed: Float) {
        updateSoundSettings(_soundSettings.value.copy(playbackSpeed = speed))
    }

    fun toggleEqualizerEnabled() {
        updateSoundSettings(_soundSettings.value.copy(isEnabled = !_soundSettings.value.isEnabled))
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                delay(300)
                val engine = audioEngine
                val realPos = engine?.currentPositionMs ?: 0L
                val realDur = engine?.durationMs ?: 0L

                _playbackState.update { state ->
                    val effectiveDur = if (realDur > 0L) realDur else state.durationMs
                    val effectivePos = if (realPos > 0L) realPos else (state.currentPositionMs + 300L)
                    state.copy(
                        currentPositionMs = effectivePos.coerceAtMost(effectiveDur),
                        durationMs = effectiveDur
                    )
                }
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
