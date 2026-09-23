package com.dyzyks.montager.player

import android.graphics.Bitmap
import com.dyzyks.montager.media.TimelineRenderPlan
import com.dyzyks.montager.model.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val playheadUs: Long = 0L,
    val isPlaying: Boolean = false,
    val currentFrame: Bitmap? = null,
    val durationUs: Long = 0L
)

class PreviewPlayer(
    private var renderPlan: TimelineRenderPlan,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : AutoCloseable {

    private val audioTrackPlayer = AudioTrackPlayer()
    private val clock = PlaybackClock(
        audioPositionProvider = { audioTrackPlayer.getPlaybackTimelineUs() }
    )

    private val _state = MutableStateFlow(
        PlayerState(
            playheadUs = 0L,
            isPlaying = false,
            currentFrame = null,
            durationUs = renderPlan.project.durationUs
        )
    )
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var playbackJob: Job? = null
    private var audioStreamingJob: Job? = null

    init {
        // Render initial frame at 0L
        renderImmediateFrame(0L)
    }

    fun updateRenderPlan(newPlan: TimelineRenderPlan) {
        val wasPlaying = clock.isPlaying
        if (wasPlaying) pause()
        this.renderPlan = newPlan
        _state.value = _state.value.copy(durationUs = newPlan.project.durationUs)
        renderImmediateFrame(_state.value.playheadUs)
        if (wasPlaying) play()
    }

    /**
     * Frame-accurate, instantaneous scrub to [timelineUs].
     * Pauses playback, seeks clock, immediately composites and dispatches the exact frame.
     */
    fun scrubTo(timelineUs: Long) {
        val clampedUs = timelineUs.coerceIn(0L, renderPlan.project.durationUs)
        if (clock.isPlaying) {
            pause()
        }
        clock.seekTo(clampedUs)
        renderImmediateFrame(clampedUs)
    }

    fun play() {
        if (clock.isPlaying) return
        val startUs = _state.value.playheadUs
        val durationUs = renderPlan.project.durationUs
        if (startUs >= durationUs) {
            // Loop or restart from 0
            clock.seekTo(0L)
        }

        val currentStart = clock.getCurrentTimelineUs(durationUs)
        clock.start(currentStart)
        audioTrackPlayer.play(currentStart)
        _state.value = _state.value.copy(isPlaying = true)

        startAudioStreamingLoop(currentStart)
        startVideoPlaybackLoop()
    }

    fun pause() {
        if (!clock.isPlaying) return
        clock.pause()
        audioTrackPlayer.pause()
        playbackJob?.cancel()
        audioStreamingJob?.cancel()
        playbackJob = null
        audioStreamingJob = null
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun togglePlayPause() {
        if (clock.isPlaying) pause() else play()
    }

    private fun renderImmediateFrame(timelineUs: Long) {
        scope.launch {
            val frame = renderPlan.renderVideoFrame(timelineUs, 960, 540)
            _state.value = _state.value.copy(
                playheadUs = timelineUs,
                currentFrame = frame
            )
        }
    }

    private fun startAudioStreamingLoop(startUs: Long) {
        audioStreamingJob?.cancel()
        audioStreamingJob = scope.launch(Dispatchers.IO) {
            var streamingPosUs = startUs
            val chunkDurationUs = 100_000L // 100ms chunks

            while (isActive && clock.isPlaying) {
                val pcm = renderPlan.renderAudioChunk(streamingPosUs, chunkDurationUs)
                if (pcm.isNotEmpty()) {
                    audioTrackPlayer.writePcm(pcm)
                }
                streamingPosUs += chunkDurationUs
                delay(80L) // Pace streaming
            }
        }
    }

    private fun startVideoPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.Default) {
            val targetFps = renderPlan.project.fps.coerceIn(24, 60)
            val frameIntervalMs = (1000L / targetFps)

            while (isActive && clock.isPlaying) {
                val currentTimelineUs = clock.getCurrentTimelineUs(renderPlan.project.durationUs)

                if (currentTimelineUs >= renderPlan.project.durationUs && renderPlan.project.durationUs > 0) {
                    withContext(Dispatchers.Main) {
                        pause()
                        scrubTo(0L)
                    }
                    break
                }

                val frame = renderPlan.renderVideoFrame(currentTimelineUs, 960, 540)
                _state.value = _state.value.copy(
                    playheadUs = currentTimelineUs,
                    currentFrame = frame
                )

                delay(frameIntervalMs)
            }
        }
    }

    override fun close() {
        pause()
        audioTrackPlayer.close()
        scope.cancel()
    }
}
