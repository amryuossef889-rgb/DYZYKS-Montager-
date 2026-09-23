package com.dyzyks.montager

import com.dyzyks.montager.engine.TimelineEditingEngine
import com.dyzyks.montager.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase3EditingEngineTest {

    @Test
    fun testSplitClipSingleAndGrouped() {
        val groupId = "sync-group-1"
        val vClip = TimelineClip(
            id = "v1-clip",
            name = "game.mp4",
            sourceUri = "/path/game.mp4",
            mediaType = MediaType.VIDEO,
            timelineStartUs = 0L,
            durationUs = 10_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            groupId = groupId
        )
        val aClip = TimelineClip(
            id = "a1-clip",
            name = "game_audio.mp4",
            sourceUri = "/path/game.mp4",
            mediaType = MediaType.AUDIO,
            timelineStartUs = 0L,
            durationUs = 10_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            groupId = groupId
        )

        val project = Project(
            tracks = listOf(
                Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(vClip)),
                Track(id = "A1", name = "A1", type = TrackType.AUDIO, clips = listOf(aClip))
            )
        )

        // Split at 4.0s (4_000_000L)
        val splitProject = TimelineEditingEngine.splitClip(project, "v1-clip", 4_000_000L)

        // Both V1 and A1 should be split into 2 clips because they are grouped
        val vClips = splitProject.tracks[0].clips
        val aClips = splitProject.tracks[1].clips
        assertEquals(2, vClips.size)
        assertEquals(2, aClips.size)

        // Verify left clip
        assertEquals(0L, vClips[0].timelineStartUs)
        assertEquals(4_000_000L, vClips[0].durationUs)
        assertEquals(0L, vClips[0].sourceInUs)
        assertEquals(4_000_000L, vClips[0].sourceOutUs)

        // Verify right clip
        assertEquals(4_000_000L, vClips[1].timelineStartUs)
        assertEquals(6_000_000L, vClips[1].durationUs)
        assertEquals(4_000_000L, vClips[1].sourceInUs)
        assertEquals(10_000_000L, vClips[1].sourceOutUs)
    }

    @Test
    fun testTrimClipInAndOut() {
        val clip = TimelineClip(
            id = "c1",
            name = "clip.mp4",
            sourceUri = "/path/c.mp4",
            timelineStartUs = 1_000_000L,
            durationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L
        )
        val project = Project(tracks = listOf(Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(clip))))

        // Trim head: sourceIn 0 -> 2s
        val headTrimmed = TimelineEditingEngine.trimClip(project, "c1", newSourceInUs = 2_000_000L)
        val hClip = headTrimmed.tracks[0].clips[0]
        assertEquals(3_000_000L, hClip.timelineStartUs)
        assertEquals(3_000_000L, hClip.durationUs)
        assertEquals(2_000_000L, hClip.sourceInUs)
        assertEquals(5_000_000L, hClip.sourceOutUs)

        // Trim tail: sourceOut 5s -> 4s
        val tailTrimmed = TimelineEditingEngine.trimClip(headTrimmed, "c1", newSourceOutUs = 4_000_000L)
        val tClip = tailTrimmed.tracks[0].clips[0]
        assertEquals(3_000_000L, tClip.timelineStartUs)
        assertEquals(2_000_000L, tClip.durationUs)
        assertEquals(2_000_000L, tClip.sourceInUs)
        assertEquals(4_000_000L, tClip.sourceOutUs)
    }

    @Test
    fun testRippleDeleteClosesGap() {
        val c1 = TimelineClip(id = "c1", name = "1", sourceUri = "", timelineStartUs = 0L, durationUs = 2_000_000L)
        val c2 = TimelineClip(id = "c2", name = "2", sourceUri = "", timelineStartUs = 2_000_000L, durationUs = 3_000_000L)
        val c3 = TimelineClip(id = "c3", name = "3", sourceUri = "", timelineStartUs = 5_000_000L, durationUs = 4_000_000L)

        val project = Project(tracks = listOf(Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(c1, c2, c3))))

        // Ripple delete c2 (3.0s duration)
        val rippled = TimelineEditingEngine.rippleDelete(project, setOf("c2"))
        val clips = rippled.tracks[0].clips
        assertEquals(2, clips.size)
        assertEquals("c1", clips[0].id)
        assertEquals(0L, clips[0].timelineStartUs)
        assertEquals("c3", clips[1].id)
        assertEquals(2_000_000L, clips[1].timelineStartUs) // Shifted left by 3.0s!
    }

    @Test
    fun testLiftDeleteLeavesGap() {
        val c1 = TimelineClip(id = "c1", name = "1", sourceUri = "", timelineStartUs = 0L, durationUs = 2_000_000L)
        val c2 = TimelineClip(id = "c2", name = "2", sourceUri = "", timelineStartUs = 2_000_000L, durationUs = 3_000_000L)
        val c3 = TimelineClip(id = "c3", name = "3", sourceUri = "", timelineStartUs = 5_000_000L, durationUs = 4_000_000L)

        val project = Project(tracks = listOf(Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(c1, c2, c3))))

        val lifted = TimelineEditingEngine.liftDelete(project, setOf("c2"))
        val clips = lifted.tracks[0].clips
        assertEquals(2, clips.size)
        assertEquals("c1", clips[0].id)
        assertEquals(0L, clips[0].timelineStartUs)
        assertEquals("c3", clips[1].id)
        assertEquals(5_000_000L, clips[1].timelineStartUs) // Kept original position!
    }

    @Test
    fun testMagneticSnapping() {
        val c1 = TimelineClip(id = "c1", name = "1", sourceUri = "", timelineStartUs = 0L, durationUs = 3_000_000L)
        val project = Project(tracks = listOf(Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(c1))))

        // Snap near 3.0s (e.g. 2_950_000L is within 100ms threshold)
        val snapped = TimelineEditingEngine.snapToGridOrEdges(project, 2_950_000L, thresholdUs = 100_000L)
        assertEquals(3_000_000L, snapped)

        // Outside threshold (e.g. 2_800_000L) should not snap
        val notSnapped = TimelineEditingEngine.snapToGridOrEdges(project, 2_800_000L, thresholdUs = 100_000L)
        assertEquals(2_800_000L, notSnapped)
    }

    @Test
    fun testChangeClipSpeed() {
        val c1 = TimelineClip(
            id = "c1",
            name = "1",
            sourceUri = "",
            timelineStartUs = 0L,
            durationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L,
            speed = 1.0f
        )
        val c2 = TimelineClip(
            id = "c2",
            name = "2",
            sourceUri = "",
            timelineStartUs = 4_000_000L,
            durationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L
        )

        val project = Project(tracks = listOf(Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(c1, c2))))

        // Double speed (2.0x) with ripple: duration halves from 4s to 2s, c2 shifts from 4s to 2s
        val fastForward = TimelineEditingEngine.changeClipSpeed(project, "c1", 2.0f, ripple = true)
        val clips = fastForward.tracks[0].clips
        assertEquals(2.0f, clips[0].speed, 0.01f)
        assertEquals(2_000_000L, clips[0].durationUs)
        assertEquals(2_000_000L, clips[1].timelineStartUs)
    }
}
