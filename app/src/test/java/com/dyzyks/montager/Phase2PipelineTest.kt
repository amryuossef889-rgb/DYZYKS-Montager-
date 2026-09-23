package com.dyzyks.montager

import android.graphics.Bitmap
import com.dyzyks.montager.media.AudioFrameReader
import com.dyzyks.montager.media.PcmAudioMixer
import com.dyzyks.montager.media.TimelineRenderPlan
import com.dyzyks.montager.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2PipelineTest {

    @Test
    fun testAbsolutePositionResamplingEliminatesDrift() {
        val sourceSampleRate = 48000
        val targetSampleRate = 44100
        val totalTargetFrames = 1000

        // Generate known 48kHz stereo sine wave
        val totalSourceFrames = (totalTargetFrames * 48000.0 / 44100.0).toInt() + 10
        val sourcePcm = ShortArray(totalSourceFrames * 2)
        for (i in 0 until totalSourceFrames) {
            val s = (sin(i * 0.05) * 20000.0).toInt().toShort()
            sourcePcm[i * 2] = s
            sourcePcm[i * 2 + 1] = s
        }

        // 1. One-shot continuous resample of 1000 frames
        val oneShot = PcmAudioMixer.resampleByAbsolutePosition(
            sourcePcm = sourcePcm,
            sourceChannels = 2,
            sourceSampleRate = sourceSampleRate,
            targetSampleRate = targetSampleRate,
            startTargetFrameIndex = 0L,
            targetFrameCount = totalTargetFrames
        )

        // 2. Chunked resample: first 400 frames, then 600 frames
        val chunk1 = PcmAudioMixer.resampleByAbsolutePosition(
            sourcePcm = sourcePcm,
            sourceChannels = 2,
            sourceSampleRate = sourceSampleRate,
            targetSampleRate = targetSampleRate,
            startTargetFrameIndex = 0L,
            targetFrameCount = 400
        )
        val chunk2 = PcmAudioMixer.resampleByAbsolutePosition(
            sourcePcm = sourcePcm,
            sourceChannels = 2,
            sourceSampleRate = sourceSampleRate,
            targetSampleRate = targetSampleRate,
            startTargetFrameIndex = 400L,
            targetFrameCount = 600
        )

        // Compare sample-by-sample: chunk1 + chunk2 MUST EXACTLY MATCH oneShot
        for (i in 0 until 400 * 2) {
            assertEquals("Chunk 1 sample $i mismatch", oneShot[i], chunk1[i], 0.00001f)
        }
        for (i in 0 until 600 * 2) {
            assertEquals("Chunk 2 sample $i mismatch", oneShot[400 * 2 + i], chunk2[i], 0.00001f)
        }
    }

    @Test
    fun testSoftKneeLimiterPreventsClipping() {
        // Test values below threshold (0.85) are untouched
        assertEquals(0.5f, PcmAudioMixer.softKneeLimit(0.5f), 0.0001f)
        assertEquals(-0.7f, PcmAudioMixer.softKneeLimit(-0.7f), 0.0001f)

        // Test extreme overshoot (+3.0f, -5.0f) is smoothly compressed within [-1.0f, +1.0f]
        val limitedHigh = PcmAudioMixer.softKneeLimit(3.0f)
        assertTrue("Limiter exceeded 1.0f: $limitedHigh", limitedHigh <= 1.0f)
        assertTrue("Limiter below threshold on high input", limitedHigh > 0.85f)

        val limitedLow = PcmAudioMixer.softKneeLimit(-5.0f)
        assertTrue("Limiter below -1.0f: $limitedLow", limitedLow >= -1.0f)
        assertTrue("Limiter above -threshold on low input", limitedLow < -0.85f)
    }

    @Test
    fun testMultiTrackAudioMixingAndPlacement() {
        val testReader = object : AudioFrameReader {
            override fun readFrames(uri: String, startFrame44k: Long, frameCount: Int): ShortArray {
                val pcm = ShortArray(frameCount * 2)
                val amp = if (uri.contains("track1")) 10000 else 15000
                for (i in 0 until frameCount) {
                    pcm[i * 2] = amp.toShort()
                    pcm[i * 2 + 1] = amp.toShort()
                }
                return pcm
            }
            override fun close() {}
        }

        val clip1 = TimelineClip(
            id = "c1",
            name = "voice.wav",
            sourceUri = "/path/track1.wav",
            mediaType = MediaType.AUDIO,
            timelineStartUs = 0L,
            durationUs = 1_000_000L,
            audioSettings = AudioSettings(volume = 1.0f, pan = 0.0f)
        )
        val clip2 = TimelineClip(
            id = "c2",
            name = "sfx.wav",
            sourceUri = "/path/track2.wav",
            mediaType = MediaType.AUDIO,
            timelineStartUs = 500_000L,
            durationUs = 1_000_000L,
            audioSettings = AudioSettings(volume = 1.0f, pan = 0.0f)
        )

        val project = Project(
            tracks = listOf(
                Track(id = "A1", name = "A1", type = TrackType.AUDIO, clips = listOf(clip1)),
                Track(id = "A2", name = "A2", type = TrackType.AUDIO, clips = listOf(clip2))
            )
        )

        val renderPlan = TimelineRenderPlan(project, audioReader = testReader)

        // 1. Render first 0.5s: only clip1 is active
        val pcmFirstHalf = renderPlan.renderAudioChunk(0L, 500_000L)
        assertTrue(pcmFirstHalf.isNotEmpty())
        assertEquals(22050 * 2, pcmFirstHalf.size)
        // First sample should be approx clip1 amp (10000)
        assertTrue(abs(pcmFirstHalf[0].toInt() - 10000) < 500)

        // 2. Render overlap region 0.5s..1.0s: both clips active, summed and limited
        val pcmOverlap = renderPlan.renderAudioChunk(500_000L, 500_000L)
        assertTrue(pcmOverlap.isNotEmpty())
        // Sum of 10000 + 15000 = 25000
        assertTrue("Expected mix ~25000 but got ${pcmOverlap[0]}", abs(pcmOverlap[0].toInt() - 25000) < 1500)
    }

    @Test
    fun testTimelineRenderPlanCompositesVideoFrame() {
        val clip = TimelineClip(
            id = "vclip",
            name = "clip.mp4",
            sourceUri = "/path/clip.mp4",
            mediaType = MediaType.VIDEO,
            timelineStartUs = 0L,
            durationUs = 3_000_000L,
            transform = Transform(scaleX = 1.0f, scaleY = 1.0f, opacity = 1.0f),
            colorGrading = ColorGrading(saturation = 1.5f),
            effects = listOf(
                Effect(type = EffectType.VIGNETTE, startTimeUs = 0L, durationUs = 2_000_000L, intensity = 0.8f)
            ),
            textOverlay = TextOverlay(text = "KILL CAM", fontSizeSp = 32f)
        )

        val project = Project(
            tracks = listOf(
                Track(id = "V1", name = "V1", type = TrackType.VIDEO, clips = listOf(clip))
            )
        )

        val plan = TimelineRenderPlan(project)
        val frame = plan.renderVideoFrame(500_000L, 1920, 1080)

        assertNotNull(frame)
        assertEquals(1920, frame.width)
        assertEquals(1080, frame.height)
        assertEquals(Bitmap.Config.ARGB_8888, frame.config)
    }
}
