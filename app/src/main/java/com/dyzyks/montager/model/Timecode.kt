package com.dyzyks.montager.model

import java.util.Locale

/**
 * Utility for timecode calculations and formatting.
 * Timestamps in the DYZYKS Montager engine are microsecond-accurate (Long us).
 */
object Timecode {
    const val US_PER_SECOND = 1_000_000L
    const val DEFAULT_FPS = 60

    fun usToFrames(us: Long, fps: Int = DEFAULT_FPS): Long {
        if (us <= 0L) return 0L
        return (us * fps) / US_PER_SECOND
    }

    fun framesToUs(frames: Long, fps: Int = DEFAULT_FPS): Long {
        if (frames <= 0L) return 0L
        return (frames * US_PER_SECOND) / fps
    }

    /**
     * Formats microseconds into DaVinci Resolve style timecode: HH:MM:SS:FF
     */
    fun formatTimecode(us: Long, fps: Int = DEFAULT_FPS): String {
        val totalSeconds = (us.coerceAtLeast(0L)) / US_PER_SECOND
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val remainingUs = us % US_PER_SECOND
        val frame = (remainingUs * fps / US_PER_SECOND).coerceIn(0, (fps - 1).toLong())
        return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frame)
    }

    /**
     * Formats microseconds into short MM:SS.S
     */
    fun formatShort(us: Long): String {
        val totalSeconds = (us.coerceAtLeast(0L)) / US_PER_SECOND
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = ((us % US_PER_SECOND) / 100_000L)
        return String.format(Locale.US, "%02d:%02d.%01d", minutes, seconds, millis)
    }
}
