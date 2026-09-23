package com.dyzyks.montager.model

import kotlin.math.pow

data class AudioSettings(
    val volume: Float = 1.0f,          // Linear volume multiplier (0.0 to 2.0)
    val gainDb: Float = 0.0f,          // Gain in dB (-60.0 dB to +12.0 dB)
    val pan: Float = 0.0f,             // -1.0 (left) to 0.0 (center) to +1.0 (right)
    val fadeInUs: Long = 0L,           // Fade-in duration in microseconds
    val fadeOutUs: Long = 0L,          // Fade-out duration in microseconds
    val isMuted: Boolean = false,
    val isSolo: Boolean = false
) {
    /**
     * Total linear gain factor combining volume multiplier and gainDb.
     * gainDb to linear: 10^(gainDb / 20.0)
     */
    fun computeLinearGain(): Float {
        if (isMuted) return 0.0f
        val dbLinear = 10.0.pow(gainDb / 20.0).toFloat()
        return (volume * dbLinear).coerceAtLeast(0.0f)
    }

    /**
     * Constant power / unity-at-center Pan Law:
     * When pan = 0.0 (center): leftGain = 1.0, rightGain = 1.0 (unity gain at center as required)
     * When pan = -1.0 (full left): leftGain = 1.0, rightGain = 0.0
     * When pan = +1.0 (full right): leftGain = 0.0, rightGain = 1.0
     */
    fun computePanGains(): Pair<Float, Float> {
        val clampedPan = pan.coerceIn(-1.0f, 1.0f)
        val leftGain = if (clampedPan <= 0.0f) 1.0f else (1.0f - clampedPan)
        val rightGain = if (clampedPan >= 0.0f) 1.0f else (1.0f + clampedPan)
        return Pair(leftGain, rightGain)
    }

    /**
     * Computes fade envelope multiplier [0.0..1.0] at [clipTimeUs] given clip total duration.
     */
    fun computeFadeMultiplier(clipTimeUs: Long, clipDurationUs: Long): Float {
        if (isMuted) return 0.0f
        if (clipDurationUs <= 0L) return 1.0f

        var mult = 1.0f
        if (fadeInUs > 0L && clipTimeUs < fadeInUs) {
            mult = (clipTimeUs.toFloat() / fadeInUs.toFloat()).coerceIn(0.0f, 1.0f)
        }
        val remainingUs = clipDurationUs - clipTimeUs
        if (fadeOutUs > 0L && remainingUs < fadeOutUs) {
            val fadeOutMult = (remainingUs.toFloat() / fadeOutUs.toFloat()).coerceIn(0.0f, 1.0f)
            mult = minOf(mult, fadeOutMult)
        }
        return mult
    }
}
