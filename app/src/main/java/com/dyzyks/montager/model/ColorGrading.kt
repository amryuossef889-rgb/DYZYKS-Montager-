package com.dyzyks.montager.model

data class ColorGrading(
    val brightness: Float = 0.0f,     // -1.0 to 1.0 (0.0 = neutral)
    val contrast: Float = 1.0f,       // 0.0 to 2.0 (1.0 = neutral)
    val saturation: Float = 1.0f,     // 0.0 to 2.0 (1.0 = neutral, 0.0 = B&W)
    val temperature: Float = 0.0f,    // -1.0 to 1.0 (cool to warm)
    val tint: Float = 0.0f,           // -1.0 to 1.0 (green to magenta)
    val exposure: Float = 0.0f,       // -2.0 to 2.0 stops (0.0 = neutral)
    val gamma: Float = 1.0f           // 0.5 to 1.5 (1.0 = neutral)
) {
    fun isNeutral(): Boolean {
        return brightness == 0.0f &&
                contrast == 1.0f &&
                saturation == 1.0f &&
                temperature == 0.0f &&
                tint == 0.0f &&
                exposure == 0.0f &&
                gamma == 1.0f
    }
}
