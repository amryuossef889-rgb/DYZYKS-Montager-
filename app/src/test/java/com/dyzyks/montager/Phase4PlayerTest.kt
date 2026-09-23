package com.dyzyks.montager

import com.dyzyks.montager.media.TimelineRenderPlan
import com.dyzyks.montager.model.*
import com.dyzyks.montager.player.PlaybackClock
import com.dyzyks.montager.player.PreviewPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase4PlayerTest {

    @Test
    fun testPlaybackClockAudioMasterAndDrift() {
        var simulatedAudioUs: Long? = 1_234_567L
        val clock = PlaybackClock(audioPositionProvider = { simulatedAudioUs })

        clock.start(0L)
        // With audio position provided, clock returns audio position
        assertEquals(1_234_567L, clock.getCurrentTimelineUs())

        // Frame timing evaluation
        // Target: 1_234_567L, video frame: 1_234_500L (on time, diff < 33ms)
        assertEquals(0, clock.evaluateFrameTiming(1_234_500L, 1_234_567L))

        // Video frame: 1_100_000L (too late, diff -134ms -> skip)
        assertEquals(-1, clock.evaluateFrameTiming(1_100_000L, 1_234_567L))

        // Video frame: 1_300_000L (too early, diff +65ms -> hold)
        assertEquals(1, clock.evaluateFrameTiming(1_300_000L, 1_234_567L))

        clock.pause()
        assertFalse(clock.isPlaying)
    }

    @Test
    fun testPlaybackClockSpeedFactor() {
        val clock = PlaybackClock()
        clock.playbackSpeed = 2.0f
        clock.start(0L)

        Thread.sleep(50) // 50ms wall clock
        val elapsedUs = clock.getCurrentTimelineUs()
        // At 2x speed, 50ms wall clock should produce approximately 100ms (100,000us)
        assertTrue("Expected elapsed >= 70,000us but got $elapsedUs", elapsedUs >= 70_000L)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testPreviewPlayerScrubbing() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val clip = TimelineClip(
            id = "c1",
            name = "game.mp4",
            sourceUri = "/path/game.mp4",
            mediaType = MediaType.VIDEO,
            timelineStartUs = 0L,
            durationUs = 5_000_000L
        )
        val project = Project(
            tracks = listOf(Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(clip)))
        )
        val plan = TimelineRenderPlan(project)
        val player = PreviewPlayer(plan, scope = testScope)

        testDispatcher.scheduler.advanceUntilIdle()

        // Scrub to 2.5s
        player.scrubTo(2_500_000L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2_500_000L, player.state.value.playheadUs)
        assertNotNull(player.state.value.currentFrame)

        player.close()
    }
}
