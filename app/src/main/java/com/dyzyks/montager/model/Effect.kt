package com.dyzyks.montager.model

import java.util.UUID

enum class EffectType {
    ZOOM_PULSE,
    SHAKE,
    RGB_SPLIT,
    GLITCH,
    FLASH,
    VIGNETTE,
    COLOR_GRADING;

    val displayName: String
        get() = when (this) {
            ZOOM_PULSE -> "Zoom Pulse"
            SHAKE -> "Screen Shake"
            RGB_SPLIT -> "RGB Split (Chromatic)"
            GLITCH -> "Cyber Glitch"
            FLASH -> "Impact Flash"
            VIGNETTE -> "Vignette"
            COLOR_GRADING -> "Color Grading"
        }
}

data class Effect(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType,
    val startTimeUs: Long = 0L,
    val durationUs: Long = 1_000_000L,
    val intensity: Float = 0.5f, // 0.0f to 1.0f
    val parameters: Map<String, Float> = emptyMap()
) {
    fun isActiveAt(timeUs: Long): Boolean {
        return timeUs in startTimeUs until (startTimeUs + durationUs)
    }

    fun getProgressAt(timeUs: Long): Float {
        if (!isActiveAt(timeUs)) return 0.0f
        if (durationUs <= 0L) return 1.0f
        return ((timeUs - startTimeUs).toFloat() / durationUs.toFloat()).coerceIn(0.0f, 1.0f)
    }
}
