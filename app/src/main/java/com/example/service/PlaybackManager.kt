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

    // Strict beginning-to-end play tracking (disqualifies advancing/seeking forward)
    private var activeSessionTrackId: String? = null
    private var startedFromBeginning: Boolean = false
    private var wasAdvancedForward: Boolean = false
    private var lastNaturalPlaybackMs: Long = 0L
    private var accumulatedPlayedMs: Long = 0L
    private var lastTickWallTimeMs: Long = 0L
    private var onValidTrackPlayedListener: ((String) -> Unit)? = null

    sealed class HostRoomPlaybackEvent {
        data class Play(
            val track: Track,
            val positionMs: Long,
            val speed: Float,
            val isPlaying: Boolean
        ) : HostRoomPlaybackEvent()

        data class Pause(val positionMs: Long) : HostRoomPlaybackEvent()

        data class Seek(val positionMs: Long, val isPlaying: Boolean) : HostRoomPlaybackEvent()
    }

    var isHostRoomActive: () -> Boolean = { false }
    var onHostRoomPlaybackEvent: ((HostRoomPlaybackEvent) -> Unit)? = null
    private var suppressRoomBroadcast: Boolean = false
    private var syncStartJob: Job? = null

    fun setOnValidTrackPlayedListener(listener: ((String) -> Unit)?) {
        onValidTrackPlayedListener = listener
    }

    fun updateCurrentTrackPlayCount(trackId: String, newPlayCount: Int) {
        _playbackState.update { state ->
            val cur = state.currentTrack
            if (cur != null && cur.id == trackId) {
                state.copy(currentTrack = cur.copy(playCount = newPlayCount))
            } else {
                state
            }
        }
    }

    internal fun resetPlaySession(trackId: String?) {
        activeSessionTrackId = trackId
        startedFromBeginning = trackId != null
        wasAdvancedForward = false
        lastNaturalPlaybackMs = 0L
        accumulatedPlayedMs = 0L
        lastTickWallTimeMs = System.currentTimeMillis()
    }

    internal fun recordPlaybackProgress(
        currentPosMs: Long,
        effectiveDurationMs: Long,
        wallDeltaMs: Long
    ) {
        val trackId = _playbackState.value.currentTrack?.id ?: return
        if (activeSessionTrackId != trackId) {
            resetPlaySession(trackId)
        }
        val speed = _soundSettings.value.playbackSpeed.coerceIn(0.25f, 3.0f)
        val maxExpectedStep = (wallDeltaMs * speed).toLong() + 1500L
        val delta = currentPosMs - lastNaturalPlaybackMs
        if (delta in 1L..maxExpectedStep) {
            accumulatedPlayedMs += delta
            lastNaturalPlaybackMs = currentPosMs
        } else if (delta > maxExpectedStep) {
            wasAdvancedForward = true
        }
        if (effectiveDurationMs > 0L && lastNaturalPlaybackMs > effectiveDurationMs) {
            lastNaturalPlaybackMs = effectiveDurationMs
        }
    }

    internal fun evaluateValidCompletionAndNotify(overrideDurationMs: Long? = null): Boolean {
        val completedTrack = _playbackState.value.currentTrack ?: return false
        if (activeSessionTrackId != completedTrack.id) return false
        if (!startedFromBeginning || wasAdvancedForward) return false

        val engineDur = audioEngine?.durationMs ?: 0L
        val effectiveDur = overrideDurationMs ?: if (engineDur > 0L) engineDur else _playbackState.value.durationMs
        if (effectiveDur <= 0L) return false

        // Account for the small interval between the final progress poll and OnCompletionListener firing
        val nearEndThreshold = (effectiveDur - 2500L).coerceAtLeast((effectiveDur * 0.85).toLong())
        if (lastNaturalPlaybackMs < nearEndThreshold && accumulatedPlayedMs < (effectiveDur * 0.85).toLong()) {
            return false
        }

        val tailDelta = (effectiveDur - lastNaturalPlaybackMs).coerceIn(0L, 2500L)
        val totalPlayed = accumulatedPlayedMs + tailDelta
        val qualifies = totalPlayed >= (effectiveDur * 0.85).toLong()
        if (qualifies) {
            onValidTrackPlayedListener?.invoke(completedTrack.id)
        }
        return qualifies
    }

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
        evaluateValidCompletionAndNotify()

        when (state.repeatMode) {
            RepeatMode.ONE -> {
                resetPlaySession(state.currentTrack?.id)
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
                    resetPlaySession(state.currentTrack?.id)
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
            resetPlaySession(track.id)
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

    fun playTrack(track: Track, allTracks: List<Track>? = null, context: Context? = null) {
        val ctx = context ?: appContext
        
        // If the caller provided the full ordered list, update currentPlaylist to maintain exact order
        if (!allTracks.isNullOrEmpty()) {
            currentPlaylist = allTracks
            val foundIdx = currentPlaylist.indexOfFirst { it.id == track.id }
            currentIndex = if (foundIdx != -1) foundIdx else 0
        } else {
            val foundIndex = currentPlaylist.indexOfFirst { it.id == track.id }
            if (foundIndex != -1) {
                currentIndex = foundIndex
            } else {
                currentPlaylist = currentPlaylist + listOf(track)
                currentIndex = currentPlaylist.size - 1
            }
        }

        resetPlaySession(track.id)
        _playbackState.update {
            it.copy(
                currentTrack = track,
                currentPositionMs = 0L,
                durationMs = track.durationSeconds * 1000L,
                currentArtStyle = track.animatedStyle
            )
        }
        if (!suppressRoomBroadcast && isHostRoomActive()) {
            onHostRoomPlaybackEvent?.invoke(
                HostRoomPlaybackEvent.Play(
                    track = track,
                    positionMs = 0L,
                    speed = _soundSettings.value.playbackSpeed,
                    isPlaying = true
                )
            )
            play(ctx, startDelayMs = 220L)
        } else {
            play(ctx)
        }
    }

    fun play(context: Context? = null, startDelayMs: Long = 0L) {
        val ctx = context ?: appContext
        val track = _playbackState.value.currentTrack ?: return
        syncStartJob?.cancel()

        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hours timeout safety
            }
        } catch (_: Exception) {}

        _playbackState.update { it.copy(isPlaying = true) }
        if (startDelayMs > 0L) {
            syncStartJob = scope.launch {
                delay(startDelayMs)
                if (_playbackState.value.isPlaying && _playbackState.value.currentTrack?.id == track.id) {
                    audioEngine?.playTrack(track)
                    startProgressTracking()
                }
            }
        } else {
            audioEngine?.playTrack(track)
            startProgressTracking()
        }

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
        syncStartJob?.cancel()
        _playbackState.update { it.copy(isPlaying = false) }
        audioEngine?.pause()
        stopProgressTracking()

        if (!suppressRoomBroadcast && isHostRoomActive()) {
            onHostRoomPlaybackEvent?.invoke(
                HostRoomPlaybackEvent.Pause(_playbackState.value.currentPositionMs)
            )
        }

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
            val curTrack = _playbackState.value.currentTrack
            if (curTrack != null) {
                audioEngine?.resume(curTrack)
                _playbackState.update { it.copy(isPlaying = true) }
                startProgressTracking()

                if (!suppressRoomBroadcast && isHostRoomActive()) {
                    onHostRoomPlaybackEvent?.invoke(
                        HostRoomPlaybackEvent.Play(
                            track = curTrack,
                            positionMs = _playbackState.value.currentPositionMs,
                            speed = _soundSettings.value.playbackSpeed,
                            isPlaying = true
                        )
                    )
                }

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
        resetPlaySession(nextTrack.id)
        _playbackState.update {
            it.copy(
                currentTrack = nextTrack,
                currentPositionMs = 0L,
                durationMs = nextTrack.durationSeconds * 1000L,
                currentArtStyle = nextTrack.animatedStyle
            )
        }
        if (!suppressRoomBroadcast && isHostRoomActive()) {
            onHostRoomPlaybackEvent?.invoke(
                HostRoomPlaybackEvent.Play(
                    track = nextTrack,
                    positionMs = 0L,
                    speed = _soundSettings.value.playbackSpeed,
                    isPlaying = true
                )
            )
            play(context, startDelayMs = 220L)
        } else {
            play(context)
        }
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
        resetPlaySession(prevTrack.id)
        _playbackState.update {
            it.copy(
                currentTrack = prevTrack,
                currentPositionMs = 0L,
                durationMs = prevTrack.durationSeconds * 1000L,
                currentArtStyle = prevTrack.animatedStyle
            )
        }
        if (!suppressRoomBroadcast && isHostRoomActive()) {
            onHostRoomPlaybackEvent?.invoke(
                HostRoomPlaybackEvent.Play(
                    track = prevTrack,
                    positionMs = 0L,
                    speed = _soundSettings.value.playbackSpeed,
                    isPlaying = true
                )
            )
            play(context, startDelayMs = 220L)
        } else {
            play(context)
        }
    }

    fun seekTo(positionMs: Long) {
        val clampedPos = positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(0L))
        if (clampedPos <= 1500L) {
            // Restarted cleanly from the beginning
            startedFromBeginning = true
            wasAdvancedForward = false
            lastNaturalPlaybackMs = clampedPos
            accumulatedPlayedMs = 0L
            lastTickWallTimeMs = System.currentTimeMillis()
        } else if (clampedPos > lastNaturalPlaybackMs + 1000L) {
            // Song was played in advance (seeked forward) -> will NOT count
            wasAdvancedForward = true
        } else {
            // Rewound backward within already played audio
            lastNaturalPlaybackMs = clampedPos
            lastTickWallTimeMs = System.currentTimeMillis()
        }

        audioEngine?.seekTo(clampedPos)
        _playbackState.update {
            it.copy(currentPositionMs = clampedPos.coerceIn(0L, it.durationMs))
        }
        if (!suppressRoomBroadcast && isHostRoomActive()) {
            onHostRoomPlaybackEvent?.invoke(
                HostRoomPlaybackEvent.Seek(clampedPos, _playbackState.value.isPlaying)
            )
        }
    }

    fun applySyncedTrackPlayback(
        track: Track,
        positionMs: Long,
        speed: Float,
        isPlaying: Boolean,
        context: Context? = null
    ) {
        val ctx = context ?: appContext
        suppressRoomBroadcast = true
        try {
            setPlaybackSpeed(speed)
            val sameTrack = _playbackState.value.currentTrack?.id == track.id
            if (!sameTrack) {
                resetPlaySession(track.id)
                _playbackState.update {
                    it.copy(
                        currentTrack = track,
                        currentPositionMs = positionMs.coerceAtLeast(0L),
                        durationMs = track.durationSeconds * 1000L,
                        currentArtStyle = track.animatedStyle
                    )
                }
                if (isPlaying) {
                    play(ctx)
                    if (positionMs > 400L) {
                        scope.launch {
                            delay(350L)
                            audioEngine?.seekTo(positionMs)
                        }
                    }
                } else {
                    pause(ctx)
                }
            } else {
                if (isPlaying && !_playbackState.value.isPlaying) {
                    audioEngine?.resume(track)
                    _playbackState.update { it.copy(isPlaying = true) }
                    startProgressTracking()
                } else if (!isPlaying && _playbackState.value.isPlaying) {
                    pause(ctx)
                }
                if (kotlin.math.abs(_playbackState.value.currentPositionMs - positionMs) > 600L) {
                    seekTo(positionMs)
                }
            }
        } finally {
            suppressRoomBroadcast = false
        }
    }

    fun applySyncedPause(positionMs: Long, context: Context? = null) {
        suppressRoomBroadcast = true
        try {
            pause(context)
            if (positionMs >= 0L) {
                seekTo(positionMs)
            }
        } finally {
            suppressRoomBroadcast = false
        }
    }

    fun applySyncedSeek(positionMs: Long, isPlaying: Boolean, context: Context? = null) {
        suppressRoomBroadcast = true
        try {
            seekTo(positionMs)
            if (isPlaying && !_playbackState.value.isPlaying) {
                togglePlayPause(context)
            } else if (!isPlaying && _playbackState.value.isPlaying) {
                pause(context)
            }
        } finally {
            suppressRoomBroadcast = false
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
        updateSoundSettings(_soundSettings.value.copy(playbackSpeed = speed.coerceIn(0.5f, 2.0f)))
    }

    fun toggleEqualizerEnabled() {
        updateSoundSettings(_soundSettings.value.copy(isEnabled = !_soundSettings.value.isEnabled))
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        lastTickWallTimeMs = System.currentTimeMillis()
        progressJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                delay(250)
                val nowWall = System.currentTimeMillis()
                val wallDelta = (nowWall - lastTickWallTimeMs).coerceIn(0L, 2000L)
                lastTickWallTimeMs = nowWall

                val engine = audioEngine
                val realPos = engine?.currentPositionMs ?: 0L
                val realDur = engine?.durationMs ?: 0L

                var updatedPos = 0L
                var updatedDur = 0L
                _playbackState.update { state ->
                    val effectiveDur = if (realDur > 0L) realDur else state.durationMs
                    val effectivePos = if (realPos > 0L) realPos else {
                        val step = (wallDelta * _soundSettings.value.playbackSpeed).toLong().coerceAtLeast(250L)
                        state.currentPositionMs + step
                    }
                    updatedDur = effectiveDur
                    updatedPos = effectivePos.coerceAtMost(effectiveDur)
                    state.copy(
                        currentPositionMs = updatedPos,
                        durationMs = effectiveDur
                    )
                }
                if (realPos > 0L || updatedPos > 0L) {
                    recordPlaybackProgress(updatedPos, updatedDur, wallDelta)
                }
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
