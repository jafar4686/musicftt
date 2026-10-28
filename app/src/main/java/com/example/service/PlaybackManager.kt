package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
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

    private var audioEngine: AudioEngine? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentPlaylist: List<Track> = emptyList()
    private var currentIndex: Int = 0

    fun initialize(context: Context) {
        if (audioEngine == null) {
            audioEngine = AudioEngine(context.applicationContext)
        }
    }

    fun setPlaylist(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true, context: Context? = null) {
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
                play(context)
            }
        }
    }

    fun playTrack(track: Track, context: Context? = null) {
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
        play(context)
    }

    fun play(context: Context? = null) {
        val track = _playbackState.value.currentTrack ?: return
        _playbackState.update { it.copy(isPlaying = true) }
        audioEngine?.playTrack(track)
        startProgressTracking()

        // Trigger Foreground Service for notification bar & background playback
        context?.let { ctx ->
            val serviceIntent = Intent(ctx, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_UPDATE_NOTIFICATION
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(serviceIntent)
                } else {
                    ctx.startService(serviceIntent)
                }
            } catch (e: Exception) {
                // Ignore service start exception in sandbox or background restriction
            }
        }
    }

    fun pause(context: Context? = null) {
        _playbackState.update { it.copy(isPlaying = false) }
        audioEngine?.pause()
        stopProgressTracking()

        context?.let { ctx ->
            val serviceIntent = Intent(ctx, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_UPDATE_NOTIFICATION
            }
            try {
                ctx.startService(serviceIntent)
            } catch (_: Exception) {}
        }
    }

    fun togglePlayPause(context: Context? = null) {
        if (_playbackState.value.isPlaying) {
            pause(context)
        } else {
            play(context)
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
        // If > 3 seconds in, restart track
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

    fun setDynamicIslandVisible(visible: Boolean) {
        _playbackState.update { it.copy(isDynamicIslandVisible = visible) }
    }

    fun setFullPlayerVisible(visible: Boolean) {
        _playbackState.update { it.copy(isFullPlayerVisible = visible) }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                delay(250)
                _playbackState.update { state ->
                    val nextPos = state.currentPositionMs + 250
                    if (nextPos >= state.durationMs) {
                        // Song ended
                        when (state.repeatMode) {
                            RepeatMode.ONE -> state.copy(currentPositionMs = 0L)
                            RepeatMode.ALL -> {
                                scope.launch { next() }
                                state.copy(currentPositionMs = 0L)
                            }
                            RepeatMode.OFF -> {
                                if (currentIndex < currentPlaylist.size - 1) {
                                    scope.launch { next() }
                                    state.copy(currentPositionMs = 0L)
                                } else {
                                    audioEngine?.pause()
                                    state.copy(isPlaying = false, currentPositionMs = 0L)
                                }
                            }
                        }
                    } else {
                        state.copy(currentPositionMs = nextPos)
                    }
                }
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
