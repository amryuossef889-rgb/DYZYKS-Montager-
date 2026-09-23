package com.dyzyks.montager.model

import java.util.UUID

enum class MediaType {
    VIDEO,
    AUDIO,
    TEXT
}

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceUri: String,
    val mediaType: MediaType = MediaType.VIDEO,
    val timelineStartUs: Long = 0L,
    val durationUs: Long = 5_000_000L,        // Duration placed on the timeline
    val sourceInUs: Long = 0L,                 // Trim in point in source media
    val sourceOutUs: Long = 5_000_000L,       // Trim out point in source media
    val speed: Float = 1.0f,                   // 0.25f to 4.0f
    val transform: Transform = Transform(),
    val colorGrading: ColorGrading = ColorGrading(),
    val audioSettings: AudioSettings = AudioSettings(),
    val effects: List<Effect> = emptyList(),
    val transitionIn: Transition? = null,
    val transitionOut: Transition? = null,
    val textOverlay: TextOverlay? = null,
    val isSelected: Boolean = false,
    val groupId: String? = null                // For linked/grouped clips (e.g. video + audio sync)
) {
    val timelineEndUs: Long
        get() = timelineStartUs + durationUs

    /**
     * Maps timeline time in microseconds into source media time in microseconds,
     * taking into account timeline offset, trim in, and speed factor.
     */
    fun mapTimelineToSourceUs(timelineUs: Long): Long {
        val offsetUs = timelineUs - timelineStartUs
        if (offsetUs < 0L) return sourceInUs
        val scaledSourceOffset = (offsetUs.toDouble() * speed.toDouble()).toLong()
        return (sourceInUs + scaledSourceOffset).coerceAtMost(sourceOutUs)
    }

    /**
     * Checks if this clip occupies the timeline at the given timestamp.
     */
    fun containsTimelineUs(timelineUs: Long): Boolean {
        return timelineUs in timelineStartUs until timelineEndUs
    }
}
