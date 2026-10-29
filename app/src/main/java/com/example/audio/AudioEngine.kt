package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.R
import com.example.model.LarkSoundSettings
import com.example.model.Track
import java.io.File

/**
 * High-fidelity, real MediaPlayer engine for AsdMusic+.
 * Plays actual media files (MP3, M4A, WAV), content URIs, and fallback acoustic audio.
 * Integrates Lark Player-style Equalizer, Bass Boost, 3D Surround Virtualizer, and Speed.
 */
class AudioEngine(
    private val context: Context,
    private val onTrackCompleted: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {

    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var currentSoundSettings: LarkSoundSettings = LarkSoundSettings()

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
                    attachAudioEffects(mp.audioSessionId)
                    applySoundSettings(currentSoundSettings)
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

    private fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId == 0) return
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = currentSoundSettings.isEnabled
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Equalizer attach error: ${e.message}")
        }
        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = currentSoundSettings.isEnabled
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "BassBoost attach error: ${e.message}")
        }
        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = currentSoundSettings.isEnabled
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Virtualizer attach error: ${e.message}")
        }
    }

    fun applySoundSettings(settings: LarkSoundSettings) {
        currentSoundSettings = settings
        try {
            equalizer?.let { eq ->
                eq.enabled = settings.isEnabled
                if (settings.isEnabled) {
                    val numBands = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange // e.g. [-1500, 1500] in millibels
                    val minMb = range[0]
                    val maxMb = range[1]

                    for (i in 0 until minOf(numBands, settings.bandLevels.size)) {
                        val levelDb = settings.bandLevels[i] // -10 to +10 dB
                        val millibels = (levelDb * 100).coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
                        eq.setBandLevel(i.toShort(), millibels)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Apply EQ error: ${e.message}")
        }

        try {
            bassBoost?.let { bb ->
                bb.enabled = settings.isEnabled
                if (settings.isEnabled) {
                    val strength = ((settings.bassBoostPercent / 100f) * 1000).toInt().coerceIn(0, 1000).toShort()
                    bb.setStrength(strength)
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Apply BassBoost error: ${e.message}")
        }

        try {
            virtualizer?.let { virt ->
                virt.enabled = settings.isEnabled
                if (settings.isEnabled) {
                    val strength = ((settings.virtualizer3DPercent / 100f) * 1000).toInt().coerceIn(0, 1000).toShort()
                    virt.setStrength(strength)
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Apply Virtualizer error: ${e.message}")
        }

        // Apply playback speed
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null && isPrepared) {
                mediaPlayer?.let { mp ->
                    val params = mp.playbackParams
                    params.speed = settings.playbackSpeed.coerceIn(0.25f, 3.0f)
                    mp.playbackParams = params
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Apply playback speed error: ${e.message}")
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
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null

        try {
            bassBoost?.release()
        } catch (_: Exception) {}
        bassBoost = null

        try {
            virtualizer?.release()
        } catch (_: Exception) {}
        virtualizer = null

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
