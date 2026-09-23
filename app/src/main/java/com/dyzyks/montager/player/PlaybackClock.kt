package com.dyzyks.montager.player

import kotlin.math.abs

/**
 * High-precision A/V synchronized clock for timeline preview playback.
 * - Master clock: Audio playhead when audio is playing, monotonic nanoTime when no audio.
 * - Supports play/pause, scrub/seek, speed modulation, and drift compensation.
 */
class PlaybackClock(
    private val audioPositionProvider: (() -> Long?)? = null
) {
    var isPlaying: Boolean = false
        private set

    var playbackSpeed: Float = 1.0f

    private var anchorTimelineUs: Long = 0L
    private var anchorNanoTime: Long = 0L

    fun start(fromTimelineUs: Long) {
        anchorTimelineUs = fromTimelineUs
        anchorNanoTime = System.nanoTime()
        isPlaying = true
    }

    fun pause() {
        if (!isPlaying) return
        anchorTimelineUs = getCurrentTimelineUs()
        isPlaying = false
    }

    fun seekTo(timelineUs: Long) {
        anchorTimelineUs = timelineUs.coerceAtLeast(0L)
        anchorNanoTime = System.nanoTime()
    }

    /**
     * Returns the exact current timeline position in microseconds.
     */
    fun getCurrentTimelineUs(maxDurationUs: Long = Long.MAX_VALUE): Long {
        if (!isPlaying) {
            return anchorTimelineUs.coerceIn(0L, maxDurationUs)
        }

        // Check if audio provides the master timestamp
        val audioPos = audioPositionProvider?.invoke()
        val calculatedUs = if (audioPos != null && audioPos >= 0L) {
            audioPos
        } else {
            val elapsedNanos = System.nanoTime() - anchorNanoTime
            val elapsedUs = (elapsedNanos / 1000.0 * playbackSpeed).toLong()
            anchorTimelineUs + elapsedUs
        }

        return calculatedUs.coerceIn(0L, maxDurationUs)
    }

    /**
     * Determines whether the video frame renderer should render, skip, or hold.
     * @param framePtsUs presentation timestamp of the current video frame
     * @param targetTimelineUs current clock time
     * @return 0 to render, -1 to skip/drop (frame is too late), +1 to hold/wait (frame is too early)
     */
    fun evaluateFrameTiming(framePtsUs: Long, targetTimelineUs: Long, toleranceUs: Long = 33_333L): Int {
        val diff = framePtsUs - targetTimelineUs
        return when {
            diff < -toleranceUs -> -1 // Late -> drop frame to catch up with audio
            diff > toleranceUs -> 1   // Early -> wait
            else -> 0                // On time -> display
        }
    }
}
