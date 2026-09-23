package com.dyzyks.montager.media

import com.dyzyks.montager.model.AudioSettings
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Pure Kotlin 44.1kHz Stereo PCM Audio Mixer.
 * Implements:
 * - Constant-power/unity-at-center Pan Law
 * - Decibel & linear gain scaling
 * - Fade in / fade out envelopes
 * - Absolute sample position resampling (eliminates drift across chunk boundaries)
 * - Transparent soft-knee limiter to prevent clipping
 */
object PcmAudioMixer {

    const val DEFAULT_SAMPLE_RATE = 44100
    const val CHANNEL_COUNT = 2 // Stereo

    /**
     * Resamples a source 16-bit PCM array (mono or stereo) at [sourceSampleRate]
     * to target 44.1kHz stereo frames starting at absolute target frame [startTargetFrameIndex].
     *
     * Resampling uses absolute position calculations:
     * exactSourcePos = (startTargetFrameIndex + i) * (sourceSampleRate / targetSampleRate)
     * This guarantees that whether audio is decoded in 100-frame chunks or 10,000-frame chunks,
     * every output sample lands on the exact same mathematical position without fractional drift.
     */
    fun resampleByAbsolutePosition(
        sourcePcm: ShortArray,
        sourceChannels: Int,
        sourceSampleRate: Int,
        targetSampleRate: Int,
        startTargetFrameIndex: Long,
        targetFrameCount: Int
    ): FloatArray {
        // Output is interleaved stereo floats in range [-1.0f, +1.0f]: [L0, R0, L1, R1, ...]
        val output = FloatArray(targetFrameCount * 2)
        if (sourcePcm.isEmpty()) return output

        val ratio = sourceSampleRate.toDouble() / targetSampleRate.toDouble()
        val totalSourceFrames = sourcePcm.size / sourceChannels

        for (i in 0 until targetFrameCount) {
            val absoluteTargetFrame = startTargetFrameIndex + i
            val exactSourceFrame = absoluteTargetFrame * ratio

            val frame0 = floor(exactSourceFrame).toLong()
            val fraction = (exactSourceFrame - frame0).toFloat()
            val frame1 = frame0 + 1

            val left: Float
            val right: Float

            if (frame0 >= totalSourceFrames) {
                left = 0.0f
                right = 0.0f
            } else {
                val f0Index = (frame0.toInt()).coerceIn(0, totalSourceFrames - 1)
                val f1Index = (frame1.toInt()).coerceIn(0, totalSourceFrames - 1)

                if (sourceChannels == 1) {
                    val s0 = sourcePcm[f0Index] / 32768.0f
                    val s1 = sourcePcm[f1Index] / 32768.0f
                    val mono = s0 + (s1 - s0) * fraction
                    left = mono
                    right = mono
                } else {
                    val s0L = sourcePcm[f0Index * 2] / 32768.0f
                    val s0R = sourcePcm[f0Index * 2 + 1] / 32768.0f
                    val s1L = sourcePcm[f1Index * 2] / 32768.0f
                    val s1R = sourcePcm[f1Index * 2 + 1] / 32768.0f

                    left = s0L + (s1L - s0L) * fraction
                    right = s0R + (s1R - s0R) * fraction
                }
            }

            output[i * 2] = left
            output[i * 2 + 1] = right
        }

        return output
    }

    /**
     * Applies gain, pan law, and fade envelopes to an interleaved stereo float buffer.
     */
    fun processChannelStrip(
        buffer: FloatArray,
        frameCount: Int,
        startTimelineUs: Long,
        clipDurationUs: Long,
        settings: AudioSettings
    ) {
        if (settings.isMuted) {
            buffer.fill(0.0f)
            return
        }

        val baseGain = settings.computeLinearGain()
        val (panLeft, panRight) = settings.computePanGains()
        val usPerFrame = 1_000_000.0 / DEFAULT_SAMPLE_RATE

        for (i in 0 until frameCount) {
            val frameUs = startTimelineUs + (i * usPerFrame).toLong()
            val fadeMult = settings.computeFadeMultiplier(frameUs, clipDurationUs)
            val leftGain = baseGain * panLeft * fadeMult
            val rightGain = baseGain * panRight * fadeMult

            buffer[i * 2] *= leftGain
            buffer[i * 2 + 1] *= rightGain
        }
    }

    /**
     * Soft-knee limiter curve to transparently prevent digital 0 dBFS clipping.
     * Linear below knee threshold (0.85), smooth hyperbolic tangent compression above.
     */
    fun softKneeLimit(sample: Float, threshold: Float = 0.85f): Float {
        val absVal = abs(sample)
        if (absVal <= threshold) return sample
        val margin = 1.0f - threshold
        val compressed = threshold + margin * tanh(((absVal - threshold) / margin).toDouble()).toFloat()
        return sign(sample) * compressed.coerceIn(0.0f, 1.0f)
    }

    /**
     * Mixes multiple stereo float tracks together, applies soft-knee limiter,
     * and converts into standard 16-bit signed PCM short array.
     */
    fun mixAndQuantize(
        trackBuffers: List<FloatArray>,
        frameCount: Int
    ): ShortArray {
        val totalSamples = frameCount * 2
        val mixedShorts = ShortArray(totalSamples)

        for (s in 0 until totalSamples) {
            var sum = 0.0f
            for (t in trackBuffers.indices) {
                val buf = trackBuffers[t]
                if (s < buf.size) {
                    sum += buf[s]
                }
            }
            val limited = softKneeLimit(sum)
            val quantized = (limited * 32767.0f).toInt().coerceIn(-32768, 32767)
            mixedShorts[s] = quantized.toShort()
        }

        return mixedShorts
    }
}
