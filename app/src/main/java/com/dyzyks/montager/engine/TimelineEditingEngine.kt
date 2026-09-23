package com.dyzyks.montager.engine

import com.dyzyks.montager.model.*
import java.util.UUID
import kotlin.math.abs

/**
 * Pure Kotlin Timeline Editing Engine.
 * Operates on immutable Project and returns updated Project instances.
 * Implements: Split, Trim, Ripple Delete, Lift Delete, Move, Snap, Group/Link, Speed Change, Selection.
 */
object TimelineEditingEngine {

    /**
     * Splits a clip at [splitTimelineUs].
     * If the clip is linked to a group, splits linked clips on other tracks at the same timeline time.
     */
    fun splitClip(project: Project, clipId: String, splitTimelineUs: Long): Project {
        val targetClip = project.findClip(clipId) ?: return project
        if (splitTimelineUs <= targetClip.timelineStartUs || splitTimelineUs >= targetClip.timelineEndUs) {
            return project
        }

        val clipsToSplit = if (targetClip.groupId != null) {
            project.tracks.flatMap { it.clips }.filter { it.groupId == targetClip.groupId && it.containsTimelineUs(splitTimelineUs) }
        } else {
            listOf(targetClip)
        }

        val splitMap = clipsToSplit.associateBy { it.id }

        val newTracks = project.tracks.map { track ->
            val newClips = mutableListOf<TimelineClip>()
            for (clip in track.clips) {
                val toSplit = splitMap[clip.id]
                if (toSplit != null) {
                    val splitSourceUs = clip.mapTimelineToSourceUs(splitTimelineUs)

                    val leftDuration = splitTimelineUs - clip.timelineStartUs
                    val rightDuration = clip.timelineEndUs - splitTimelineUs

                    val leftGroup = if (clip.groupId != null) UUID.randomUUID().toString() else null
                    val rightGroup = if (clip.groupId != null) UUID.randomUUID().toString() else null

                    val leftClip = clip.copy(
                        id = UUID.randomUUID().toString(),
                        durationUs = leftDuration,
                        sourceOutUs = splitSourceUs,
                        transitionOut = null,
                        groupId = leftGroup
                    )

                    val rightClip = clip.copy(
                        id = UUID.randomUUID().toString(),
                        timelineStartUs = splitTimelineUs,
                        durationUs = rightDuration,
                        sourceInUs = splitSourceUs,
                        transitionIn = null,
                        groupId = rightGroup
                    )

                    newClips.add(leftClip)
                    newClips.add(rightClip)
                } else {
                    newClips.add(clip)
                }
            }
            track.copy(clips = newClips)
        }

        return project.copy(tracks = newTracks, modifiedAt = System.currentTimeMillis())
    }

    /**
     * Trims a clip's in-point or out-point.
     */
    fun trimClip(
        project: Project,
        clipId: String,
        newSourceInUs: Long? = null,
        newSourceOutUs: Long? = null
    ): Project {
        val clip = project.findClip(clipId) ?: return project
        val track = project.findTrackForClip(clipId) ?: return project

        val sourceIn = (newSourceInUs ?: clip.sourceInUs).coerceAtLeast(0L)
        val sourceOut = (newSourceOutUs ?: clip.sourceOutUs).coerceAtLeast(sourceIn + 100_000L) // Min 100ms

        val sourceDeltaIn = sourceIn - clip.sourceInUs
        val newTimelineStart = (clip.timelineStartUs + (sourceDeltaIn / clip.speed).toLong()).coerceAtLeast(0L)
        val newDuration = ((sourceOut - sourceIn) / clip.speed).toLong()

        val updatedClip = clip.copy(
            timelineStartUs = newTimelineStart,
            durationUs = newDuration,
            sourceInUs = sourceIn,
            sourceOutUs = sourceOut
        )

        val updatedTracks = project.tracks.map { t ->
            if (t.id == track.id) {
                t.copy(clips = t.clips.map { if (it.id == clipId) updatedClip else it })
            } else t
        }

        return project.copy(tracks = updatedTracks, modifiedAt = System.currentTimeMillis())
    }

    /**
     * Deletes clips and ripples (shifts) subsequent clips left to close gaps.
     */
    fun rippleDelete(project: Project, clipIds: Set<String>): Project {
        if (clipIds.isEmpty()) return project

        val updatedTracks = project.tracks.map { track ->
            val remainingClips = mutableListOf<TimelineClip>()
            // Sort clips by timeline start
            val sortedClips = track.clips.sortedBy { it.timelineStartUs }

            var cumulativeShiftUs = 0L

            for (clip in sortedClips) {
                if (clipIds.contains(clip.id)) {
                    cumulativeShiftUs += clip.durationUs
                } else {
                    val shiftedClip = clip.copy(
                        timelineStartUs = (clip.timelineStartUs - cumulativeShiftUs).coerceAtLeast(0L)
                    )
                    remainingClips.add(shiftedClip)
                }
            }

            track.copy(clips = remainingClips)
        }

        return project.copy(tracks = updatedTracks, modifiedAt = System.currentTimeMillis())
    }

    /**
     * Lift deletes clips, leaving gap in place.
     */
    fun liftDelete(project: Project, clipIds: Set<String>): Project {
        if (clipIds.isEmpty()) return project

        val updatedTracks = project.tracks.map { track ->
            track.copy(clips = track.clips.filterNot { clipIds.contains(it.id) })
        }

        return project.copy(tracks = updatedTracks, modifiedAt = System.currentTimeMillis())
    }

    /**
     * Moves a clip to a new start time and optionally target track.
     * Moves grouped clips in lockstep.
     */
    fun moveClip(
        project: Project,
        clipId: String,
        targetTrackId: String,
        newTimelineStartUs: Long,
        enableSnapping: Boolean = true,
        snapThresholdUs: Long = 100_000L
    ): Project {
        val clip = project.findClip(clipId) ?: return project
        val currentTrack = project.findTrackForClip(clipId) ?: return project

        var finalStartUs = newTimelineStartUs.coerceAtLeast(0L)
        if (enableSnapping) {
            finalStartUs = snapToGridOrEdges(project, finalStartUs, snapThresholdUs, excludeClipId = clipId)
        }

        val deltaUs = finalStartUs - clip.timelineStartUs

        val linkedGroupIds = if (clip.groupId != null) setOf(clip.groupId) else emptySet()

        val updatedTracks = project.tracks.map { track ->
            val newClips = mutableListOf<TimelineClip>()
            for (c in track.clips) {
                if (c.id == clipId) {
                    if (track.id == targetTrackId) {
                        newClips.add(c.copy(timelineStartUs = finalStartUs))
                    }
                } else if (c.groupId != null && linkedGroupIds.contains(c.groupId)) {
                    val shiftedStart = (c.timelineStartUs + deltaUs).coerceAtLeast(0L)
                    newClips.add(c.copy(timelineStartUs = shiftedStart))
                } else {
                    newClips.add(c)
                }
            }
            // If moved to a different track and this is the target track
            if (track.id == targetTrackId && currentTrack.id != targetTrackId) {
                newClips.add(clip.copy(timelineStartUs = finalStartUs))
            }
            track.copy(clips = newClips.sortedBy { it.timelineStartUs })
        }

        return project.copy(tracks = updatedTracks, modifiedAt = System.currentTimeMillis())
    }

    /**
     * Magnetic snapping: Snaps targetUs to any existing clip start, clip end, or 0.
     */
    fun snapToGridOrEdges(
        project: Project,
        targetUs: Long,
        thresholdUs: Long = 100_000L,
        excludeClipId: String? = null
    ): Long {
        val snapPoints = mutableSetOf(0L)
        for (track in project.tracks) {
            for (clip in track.clips) {
                if (clip.id == excludeClipId) continue
                snapPoints.add(clip.timelineStartUs)
                snapPoints.add(clip.timelineEndUs)
            }
        }

        var closestPoint = targetUs
        var minDiff = thresholdUs + 1

        for (point in snapPoints) {
            val diff = abs(targetUs - point)
            if (diff < minDiff && diff <= thresholdUs) {
                minDiff = diff
                closestPoint = point
            }
        }

        return closestPoint
    }

    /**
     * Links multiple clips into a synchronized group.
     */
    fun linkClips(project: Project, clipIds: Set<String>, groupId: String? = null): Project {
        val gid = groupId ?: UUID.randomUUID().toString()
        val updatedTracks = project.tracks.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clipIds.contains(clip.id)) clip.copy(groupId = gid) else clip
            })
        }
        return project.copy(tracks = updatedTracks, modifiedAt = System.currentTimeMillis())
    }

    /**
     * Unlinks clips.
     */
    fun unlinkClips(project: Project, clipIds: Set<String>): Project {
        val updatedTracks = project.tracks.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clipIds.contains(clip.id)) clip.copy(groupId = null) else clip
            })
        }
        return project.copy(tracks = updatedTracks, modifiedAt = System.currentTimeMillis())
    }

    /**
     * Changes clip playback speed (0.25x to 4.0x) and re-computes timeline duration.
     * Optionally ripples subsequent clips on the track.
     */
    fun changeClipSpeed(
        project: Project,
        clipId: String,
        newSpeed: Float,
        ripple: Boolean = false
    ): Project {
        val speed = newSpeed.coerceIn(0.25f, 4.0f)
        val clip = project.findClip(clipId) ?: return project
        val track = project.findTrackForClip(clipId) ?: return project

        val sourceDuration = clip.sourceOutUs - clip.sourceInUs
        val newDurationUs = (sourceDuration / speed).toLong()
        val durationDeltaUs = newDurationUs - clip.durationUs

        val updatedTracks = project.tracks.map { t ->
            if (t.id == track.id) {
                var found = false
                val newClips = t.clips.map { c ->
                    if (c.id == clipId) {
                        found = true
                        c.copy(speed = speed, durationUs = newDurationUs)
                    } else if (found && ripple) {
                        c.copy(timelineStartUs = (c.timelineStartUs + durationDeltaUs).coerceAtLeast(0L))
                    } else {
                        c
                    }
                }
                t.copy(clips = newClips)
            } else t
        }

        return project.copy(tracks = updatedTracks, modifiedAt = System.currentTimeMillis())
    }

    /**
     * Sets selection status on clips.
     */
    fun setSelection(project: Project, selectedClipIds: Set<String>): Project {
        val updatedTracks = project.tracks.map { track ->
            track.copy(clips = track.clips.map { clip ->
                val shouldSelect = selectedClipIds.contains(clip.id)
                if (clip.isSelected != shouldSelect) clip.copy(isSelected = shouldSelect) else clip
            })
        }
        return project.copy(tracks = updatedTracks)
    }
}
