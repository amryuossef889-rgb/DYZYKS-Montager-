package com.dyzyks.montager.model

import java.util.UUID

enum class TransitionType {
    CROSSFADE,
    WIPE_LEFT,
    WIPE_RIGHT,
    DIP_TO_BLACK,
    DIP_TO_WHITE;

    val displayName: String
        get() = when (this) {
            CROSSFADE -> "Cross Dissolve"
            WIPE_LEFT -> "Wipe Left"
            WIPE_RIGHT -> "Wipe Right"
            DIP_TO_BLACK -> "Dip to Black"
            DIP_TO_WHITE -> "Dip to White"
        }
}

enum class TransitionAlignment {
    CENTER_ON_CUT,
    START_ON_CUT,
    END_ON_CUT
}

data class Transition(
    val id: String = UUID.randomUUID().toString(),
    val type: TransitionType,
    val durationUs: Long = 500_000L, // 0.5s default
    val alignment: TransitionAlignment = TransitionAlignment.CENTER_ON_CUT
)
