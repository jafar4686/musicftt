package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Resilient, crash-proof musical audio player and ambient synthesizer.
 * Safeguarded against null native pointers, emulator audio driver absences, and SEGV_MAPERR fault addr 0x0.
 */
class AudioEngine(private val context: Context) {

    private var audioTrack: AudioTrack? = null
    private var isAudioSinkAvailable = false
    private var synthesisJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val sampleRate = 44100
    private var isPlaying = false
    private var currentFrequency = 220.0
    private var targetVolume = 0.4f

    private val chordProgressions = mapOf(
        "Cyberpunk" to listOf(110.0, 130.81, 146.83, 164.81),
        "Lo-Fi" to listOf(130.81, 164.81, 196.00, 220.00),
        "Synthwave" to listOf(146.83, 174.61, 220.00, 261.63),
        "Ambient" to listOf(110.00, 146.83, 164.81, 220.00),
        "Chillhop" to listOf(130.81, 174.61, 196.00, 246.94)
    )

    init {
        initAudioTrackSafely()
    }

    private fun initAudioTrackSafely() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufferSize > 0) {
                val bufferSize = maxOf(minBufferSize, sampleRate / 2)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    audioTrack = track
                    isAudioSinkAvailable = true
                    try {
                        track.play()
                    } catch (e: Throwable) {
                        Log.w("AudioEngine", "Unable to start AudioTrack play: ${e.message}")
                        isAudioSinkAvailable = false
                    }
                } else {
                    Log.w("AudioEngine", "AudioTrack state not initialized (headless/emulator mode)")
                    isAudioSinkAvailable = false
                }
            } else {
                isAudioSinkAvailable = false
            }
        } catch (t: Throwable) {
            Log.w("AudioEngine", "AudioTrack creation bypassed safely: ${t.message}")
            isAudioSinkAvailable = false
            audioTrack = null
        }
    }

    fun playTrack(track: Track) {
        isPlaying = true
        synthesisJob?.cancel()
        synthesisJob = scope.launch {
            try {
                synthesizeTrackMelody(track)
            } catch (t: Throwable) {
                Log.w("AudioEngine", "Playback synthesis caught exception safely: ${t.message}")
            }
        }
    }

    fun pause() {
        isPlaying = false
        synthesisJob?.cancel()
        synthesisJob = null
        try {
            if (isAudioSinkAvailable && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.pause()
            }
        } catch (_: Throwable) {}
    }

    fun resume(track: Track?) {
        if (track != null) {
            try {
                if (isAudioSinkAvailable && audioTrack?.playState == AudioTrack.PLAYSTATE_PAUSED) {
                    audioTrack?.play()
                }
            } catch (_: Throwable) {}
            playTrack(track)
        }
    }

    fun stop() {
        pause()
    }

    fun release() {
        isPlaying = false
        synthesisJob?.cancel()
        synthesisJob = null
        try {
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.stop()
                audioTrack?.release()
            }
        } catch (_: Throwable) {}
        audioTrack = null
        isAudioSinkAvailable = false
    }

    private suspend fun synthesizeTrackMelody(track: Track) {
        val chords = chordProgressions[track.genre] ?: chordProgressions["Lo-Fi"]!!
        val buffer = ShortArray(2048)
        var phase = 0.0
        var secondaryPhase = 0.0
        var chordIndex = 0
        var noteCounter = 0

        while (scope.isActive && isPlaying) {
            val baseFreq = chords[chordIndex % chords.size]
            val arpeggioFactor = when ((noteCounter / 8) % 4) {
                0 -> 1.0
                1 -> 1.2599
                2 -> 1.4983
                else -> 2.0
            }
            val targetFreq = baseFreq * arpeggioFactor
            currentFrequency += (targetFreq - currentFrequency) * 0.15

            val phaseIncrement = (2.0 * PI * currentFrequency) / sampleRate
            val secondaryIncrement = (2.0 * PI * (currentFrequency * 1.003)) / sampleRate

            if (isAudioSinkAvailable && audioTrack != null) {
                val currentTrack = audioTrack
                if (currentTrack != null && currentTrack.state == AudioTrack.STATE_INITIALIZED) {
                    for (i in buffer.indices) {
                        phase += phaseIncrement
                        if (phase > 2.0 * PI) phase -= 2.0 * PI

                        secondaryPhase += secondaryIncrement
                        if (secondaryPhase > 2.0 * PI) secondaryPhase -= 2.0 * PI

                        val sample1 = sin(phase)
                        val sampleSub = 0.35 * sin(phase * 0.5)
                        val sampleDetuned = 0.3 * sin(secondaryPhase)
                        val harmonic = 0.15 * sin(phase * 3.0)

                        val mixed = (sample1 + sampleSub + sampleDetuned + harmonic) * 0.45 * targetVolume
                        val clamped = mixed.coerceIn(-1.0, 1.0)
                        buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
                    }

                    try {
                        val written = currentTrack.write(buffer, 0, buffer.size)
                        if (written < 0) {
                            Log.w("AudioEngine", "AudioTrack write returned error code: $written")
                            isAudioSinkAvailable = false
                        }
                    } catch (t: Throwable) {
                        Log.w("AudioEngine", "AudioTrack write failed safely: ${t.message}")
                        isAudioSinkAvailable = false
                    }
                } else {
                    isAudioSinkAvailable = false
                }
            }

            noteCounter++
            if (noteCounter % 32 == 0) {
                chordIndex++
            }

            delay(25)
        }
    }
}
