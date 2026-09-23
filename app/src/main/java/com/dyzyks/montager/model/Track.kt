package com.dyzyks.montager.model

import java.util.UUID

enum class TrackType {
    VIDEO,
    AUDIO
}

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TrackType,
    val clips: List<TimelineClip> = emptyList(),
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val isLocked: Boolean = false
) {
    val durationUs: Long
        get() = clips.maxOfOrNull { it.timelineEndUs } ?: 0L

    fun getClipAt(timelineUs: Long): TimelineClip? {
        return clips.firstOrNull { it.containsTimelineUs(timelineUs) }
    }
}
