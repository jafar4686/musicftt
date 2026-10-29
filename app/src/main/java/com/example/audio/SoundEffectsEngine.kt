package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin

/**
 * Native, zero-dependency procedural audio synthesizer for AsdMusic.
 * Produces:
 * 1. Crisp, tactile typewriter click sounds per character.
 * 2. High-fidelity harmonic entrance chime (polyphonic resonant chord).
 */
object SoundEffectsEngine {

    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Plays a short, soft, tactile typewriter click sound.
     */
    fun playTypewriterClick() {
        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 18
                val numSamples = (sampleRate * durationMs / 1000)
                val buffer = ShortArray(numSamples)

                val freq1 = 2400.0
                val freq2 = 3600.0

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val decay = exp(-t * 220.0)
                    // Blend two high-frequency bursts with fast exponential decay
                    val sample = ((sin(2.0 * Math.PI * freq1 * t) * 0.6 +
                            sin(2.0 * Math.PI * freq2 * t) * 0.4) * decay * 0.35 * Short.MAX_VALUE).toInt()
                    buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                Log.w("SoundEffectsEngine", "Typewriter click error: ${e.message}")
            }
        }
    }

    /**
     * Plays a rich, professional studio entrance chime (polyphonic harmonic chord: C5, E5, G5, C6).
     */
    fun playEntranceChime() {
        scope.launch {
            try {
                val sampleRate = 44100
                val durationMs = 1600
                val numSamples = (sampleRate * durationMs / 1000)
                val buffer = ShortArray(numSamples)

                // Harmonics for a warm, elegant bell/chime chord (C-E-G-C)
                val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
                val weights = doubleArrayOf(0.35, 0.28, 0.22, 0.15)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate

                    // Attack (25ms) + gentle decay
                    val attack = if (t < 0.025) t / 0.025 else 1.0
                    val decay = exp(-t * 2.8) * attack

                    var sum = 0.0
                    for (f in freqs.indices) {
                        sum += sin(2.0 * Math.PI * freqs[f] * t) * weights[f]
                    }

                    // Add subtle warmth shimmer
                    val shimmer = 1.0 + 0.08 * sin(2.0 * Math.PI * 6.0 * t)
                    val sampleVal = (sum * shimmer * decay * 0.5 * Short.MAX_VALUE).toInt()
                    buffer[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                playBuffer(buffer, sampleRate)
            } catch (e: Exception) {
                Log.w("SoundEffectsEngine", "Entrance chime error: ${e.message}")
            }
        }
    }

    private fun playBuffer(buffer: ShortArray, sampleRate: Int) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()

        // Release after playback completes
        scope.launch {
            kotlinx.coroutines.delay((buffer.size * 1000L / sampleRate) + 100L)
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {}
        }
    }
}
