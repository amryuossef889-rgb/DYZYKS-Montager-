package com.dyzyks.montager.model

import java.util.UUID

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val name: String = "Untitled Montager Project",
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val tracks: List<Track> = listOf(
        Track(id = "V1", name = "V1", type = TrackType.VIDEO),
        Track(id = "V2", name = "V2", type = TrackType.VIDEO),
        Track(id = "A1", name = "A1", type = TrackType.AUDIO),
        Track(id = "A2", name = "A2", type = TrackType.AUDIO)
    ),
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }

    val durationUs: Long
        get() = tracks.maxOfOrNull { it.durationUs } ?: 0L

    val videoTracks: List<Track>
        get() = tracks.filter { it.type == TrackType.VIDEO }

    val audioTracks: List<Track>
        get() = tracks.filter { it.type == TrackType.AUDIO }

    fun findClip(clipId: String): TimelineClip? {
        for (track in tracks) {
            val clip = track.clips.firstOrNull { it.id == clipId }
            if (clip != null) return clip
        }
        return null
    }

    fun findTrackForClip(clipId: String): Track? {
        return tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }
    }
}
