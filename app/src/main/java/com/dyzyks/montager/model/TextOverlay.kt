package com.dyzyks.montager.model

import java.util.UUID

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "SAMPLE TITLE",
    val fontSizeSp: Float = 36.0f,
    val textColorArgb: Long = 0xFFFFFFFFL,
    val backgroundColorArgb: Long = 0x88000000L,
    val positionX: Float = 0.0f, // -1.0 to 1.0 (0.0 = center)
    val positionY: Float = 0.0f, // -1.0 to 1.0 (0.0 = center)
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val isBold: Boolean = true
)
