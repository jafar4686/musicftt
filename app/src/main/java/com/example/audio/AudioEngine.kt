package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.example.R
import com.example.model.Track
import java.io.File

/**
 * High-fidelity, real MediaPlayer engine for Android.
 * Plays actual media files (MP3, M4A, WAV), content URIs, and fallback acoustic audio.
 * Uses PowerManager.PARTIAL_WAKE_LOCK and AudioAttributes.USAGE_MEDIA to ensure
 * smooth, seamless, crystal-clear background playback when exiting the app.
 */
class AudioEngine(
    private val context: Context,
    private val onTrackCompleted: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isPlayingState = false
    private var currentTrackId: String? = null

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            isPlayingState
        }

    val currentPositionMs: Long
        get() = try {
            if (isPrepared && mediaPlayer != null) {
                mediaPlayer!!.currentPosition.toLong()
            } else 0L
        } catch (_: Exception) {
            0L
        }

    val durationMs: Long
        get() = try {
            if (isPrepared && mediaPlayer != null && mediaPlayer!!.duration > 0) {
                mediaPlayer!!.duration.toLong()
            } else 0L
        } catch (_: Exception) {
            0L
        }

    fun playTrack(track: Track) {
        currentTrackId = track.id
        releasePlayerSafely()

        try {
            val player = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
            }

            var dataSourceSet = false

            // 1. Try local file path if present and valid
            val filePath = track.localFilePath
            if (!filePath.isNullOrBlank()) {
                if (filePath.startsWith("content://")) {
                    try {
                        val uri = Uri.parse(filePath)
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            player.setDataSource(pfd.fileDescriptor)
                            dataSourceSet = true
                        }
                    } catch (e: Exception) {
                        Log.w("AudioEngine", "Unable to open content URI: ${e.message}")
                    }
                } else {
                    val file = File(filePath)
                    if (file.exists() && file.length() > 512) {
                        try {
                            player.setDataSource(file.absolutePath)
                            dataSourceSet = true
                        } catch (e: Exception) {
                            Log.w("AudioEngine", "Failed to setDataSource for file: ${e.message}")
                        }
                    }
                }
            }

            // 2. Try source URL if it's a valid remote stream
            if (!dataSourceSet && !track.sourceUrl.isNullOrBlank() &&
                (track.sourceUrl.startsWith("http://") || track.sourceUrl.startsWith("https://")) &&
                (track.sourceUrl.endsWith(".mp3") || track.sourceUrl.endsWith(".m4a") || track.sourceUrl.endsWith(".wav") || track.sourceUrl.endsWith(".ogg"))
            ) {
                try {
                    player.setDataSource(track.sourceUrl)
                    dataSourceSet = true
                } catch (e: Exception) {
                    Log.w("AudioEngine", "Failed to setDataSource for remote URL: ${e.message}")
                }
            }

            // 3. Fallback to real bundled acoustic track (res/raw/aura_music.wav)
            if (!dataSourceSet) {
                try {
                    val afd = context.resources.openRawResourceFd(R.raw.aura_music)
                    if (afd != null) {
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        dataSourceSet = true
                    }
                } catch (e: Exception) {
                    Log.w("AudioEngine", "Failed to load bundled fallback track: ${e.message}")
                }
            }

            if (!dataSourceSet) {
                Log.e("AudioEngine", "No valid data source found for track ${track.title}")
                return
            }

            player.setOnPreparedListener { mp ->
                isPrepared = true
                try {
                    mp.start()
                    isPlayingState = true
                } catch (e: Exception) {
                    Log.e("AudioEngine", "Error starting playback on prepared: ${e.message}")
                }
            }

            player.setOnCompletionListener {
                isPlayingState = false
                onTrackCompleted()
            }

            player.setOnErrorListener { _, what, extra ->
                Log.w("AudioEngine", "MediaPlayer error: what=$what, extra=$extra")
                isPrepared = false
                isPlayingState = false
                onError("Playback error ($what, $extra)")
                true // Handled
            }

            mediaPlayer = player
            player.prepareAsync()

        } catch (t: Throwable) {
            Log.e("AudioEngine", "Fatal error creating MediaPlayer: ${t.message}")
            releasePlayerSafely()
        }
    }

    fun pause() {
        isPlayingState = false
        try {
            if (isPrepared && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Pause error: ${e.message}")
        }
    }

    fun resume(track: Track?) {
        if (mediaPlayer != null && isPrepared) {
            try {
                mediaPlayer?.start()
                isPlayingState = true
                return
            } catch (e: Exception) {
                Log.w("AudioEngine", "Resume error: ${e.message}")
            }
        }
        if (track != null) {
            playTrack(track)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            if (isPrepared && mediaPlayer != null) {
                val targetMs = positionMs.coerceIn(0L, durationMs).toInt()
                mediaPlayer?.seekTo(targetMs)
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Seek error: ${e.message}")
        }
    }

    fun stop() {
        pause()
    }

    fun release() {
        releasePlayerSafely()
    }

    private fun releasePlayerSafely() {
        isPrepared = false
        isPlayingState = false
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }
}
