package com.dyzyks.montager.model

enum class EasingType {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    fun interpolate(fraction: Float): Float {
        val t = fraction.coerceIn(0.0f, 1.0f)
        return when (this) {
            LINEAR -> t
            EASE_IN -> t * t
            EASE_OUT -> t * (2.0f - t)
            EASE_IN_OUT -> if (t < 0.5f) 2.0f * t * t else -1.0f + (4.0f - 2.0f * t) * t
        }
    }
}

data class Keyframe(
    val timeUs: Long,
    val value: Float,
    val easing: EasingType = EasingType.LINEAR
)

data class KeyframedProperty(
    val propertyName: String,
    val baseValue: Float,
    val keyframes: List<Keyframe> = emptyList()
) {
    fun getValueAt(timeUs: Long): Float {
        if (keyframes.isEmpty()) return baseValue
        if (keyframes.size == 1) return keyframes.first().value

        val sorted = keyframes.sortedBy { it.timeUs }
        if (timeUs <= sorted.first().timeUs) return sorted.first().value
        if (timeUs >= sorted.last().timeUs) return sorted.last().value

        for (i in 0 until sorted.size - 1) {
            val kf1 = sorted[i]
            val kf2 = sorted[i + 1]
            if (timeUs in kf1.timeUs..kf2.timeUs) {
                val duration = (kf2.timeUs - kf1.timeUs).toFloat()
                if (duration <= 0.0f) return kf2.value
                val rawFraction = (timeUs - kf1.timeUs).toFloat() / duration
                val easedFraction = kf1.easing.interpolate(rawFraction)
                return kf1.value + (kf2.value - kf1.value) * easedFraction
            }
        }
        return sorted.last().value
    }
}
