package com.dyzyks.montager.model

data class Transform(
    val positionX: Float = 0.0f,
    val positionY: Float = 0.0f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDeg: Float = 0.0f,
    val opacity: Float = 1.0f,
    val keyframes: List<Keyframe> = emptyList() // Keyframes for opacity/scale modulation
) {
    fun getResolvedOpacity(clipTimeUs: Long): Float {
        if (keyframes.isEmpty()) return opacity.coerceIn(0.0f, 1.0f)
        val prop = KeyframedProperty("opacity", opacity, keyframes)
        return prop.getValueAt(clipTimeUs).coerceIn(0.0f, 1.0f)
    }
}
