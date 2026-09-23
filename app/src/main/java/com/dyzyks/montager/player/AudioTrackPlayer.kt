package com.dyzyks.montager.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log

/**
 * Low-latency streaming AudioTrack player for timeline preview audio.
 * Operates at 44.1kHz stereo 16-bit PCM.
 */
class AudioTrackPlayer(
    val sampleRate: Int = 44100
) : AutoCloseable {

    companion object {
        private const val TAG = "AudioTrackPlayer"
    }

    private var audioTrack: AudioTrack? = null
    private var baseTimelineUs: Long = 0L
    private var totalFramesWritten: Long = 0L

    @Synchronized
    fun initialize() {
        if (audioTrack != null) return

        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize * 2, sampleRate / 5) // At least 200ms buffer

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val track = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        this.audioTrack = track
    }

    @Synchronized
    fun play(fromTimelineUs: Long) {
        initialize()
        val track = audioTrack ?: return
        baseTimelineUs = fromTimelineUs
        totalFramesWritten = 0L
        try {
            track.play()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack play failed", e)
        }
    }

    @Synchronized
    fun pause() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack pause error", e)
        }
    }

    @Synchronized
    fun writePcm(pcm: ShortArray): Int {
        val track = audioTrack ?: return 0
        val written = track.write(pcm, 0, pcm.size)
        if (written > 0) {
            totalFramesWritten += written / 2 // stereo
        }
        return written
    }

    /**
     * Estimates the current audio playback timeline time in microseconds.
     */
    @Synchronized
    fun getPlaybackTimelineUs(): Long? {
        val track = audioTrack ?: return null
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return null

        val headPos = track.playbackHeadPosition.toLong()
        val playedUs = (headPos * 1_000_000.0 / sampleRate).toLong()
        return baseTimelineUs + playedUs
    }

    override fun close() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
