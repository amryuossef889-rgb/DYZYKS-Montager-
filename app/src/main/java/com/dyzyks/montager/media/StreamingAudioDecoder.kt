package com.dyzyks.montager.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance streaming audio decoder powered by MediaExtractor and MediaCodec.
 * - Decodes bounded chunks into a localized sliding-window frame buffer.
 * - Handles INFO_OUTPUT_FORMAT_CHANGED dynamically.
 * - Supports seek + preroll for backward jumps or random access.
 * - Converts any source audio (mono, 48kHz, AAC, Opus, MP3) to 44.1kHz stereo with absolute sample position.
 */
class StreamingAudioDecoder(
    private val context: Context? = null
) : AudioFrameReader {

    companion object {
        private const val TAG = "StreamingAudioDecoder"
        private const val TIMEOUT_US = 10_000L
        private const val BUFFER_WINDOW_FRAMES = 88_200 // 2 seconds of 44.1k stereo in memory max
    }

    private data class DecoderSession(
        val uri: String,
        val extractor: MediaExtractor,
        val codec: MediaCodec,
        var sampleRate: Int = 44100,
        var channelCount: Int = 2,
        var currentSourceFrame: Long = 0L,
        var isEos: Boolean = false,
        // Sliding window of decoded 16-bit PCM shorts at native source rate/channels
        var windowStartFrame: Long = 0L,
        val windowSamples: MutableList<Short> = mutableListOf()
    )

    private val sessions = mutableMapOf<String, DecoderSession>()

    override fun readFrames(uri: String, startFrame44k: Long, frameCount: Int): ShortArray {
        if (frameCount <= 0) return ShortArray(0)

        val session = getOrCreateSession(uri) ?: return ShortArray(frameCount * 2)

        synchronized(session) {
            val ratio = session.sampleRate.toDouble() / 44100.0
            val requestedSourceStartFrame = (startFrame44k * ratio).toLong()
            val requestedSourceEndFrame = ((startFrame44k + frameCount) * ratio).toLong()

            // If requested start is before our window or too far ahead (> 2 seconds), seek and preroll
            val windowEndFrame = session.windowStartFrame + (session.windowSamples.size / session.channelCount)
            if (requestedSourceStartFrame < session.windowStartFrame || requestedSourceStartFrame > windowEndFrame + session.sampleRate) {
                seekAndPreroll(session, requestedSourceStartFrame)
            }

            // Decode more frames until we cover requestedSourceEndFrame or reach EOS
            while (!session.isEos && (session.windowStartFrame + (session.windowSamples.size / session.channelCount)) < requestedSourceEndFrame) {
                decodeNextChunk(session)
            }

            // Extract the relevant slice from window
            val windowOffset = (requestedSourceStartFrame - session.windowStartFrame).toInt().coerceAtLeast(0)
            val availableFrames = ((session.windowSamples.size / session.channelCount) - windowOffset).coerceAtLeast(0)
            val framesToTake = minOf(availableFrames, (requestedSourceEndFrame - requestedSourceStartFrame).toInt() + 1)

            val rawSlice = if (framesToTake > 0) {
                val startIdx = windowOffset * session.channelCount
                val endIdx = minOf(session.windowSamples.size, (windowOffset + framesToTake) * session.channelCount)
                session.windowSamples.subList(startIdx, endIdx).toShortArray()
            } else {
                ShortArray(0)
            }

            // Resample to exact 44.1kHz stereo using absolute position
            val floatResampled = PcmAudioMixer.resampleByAbsolutePosition(
                sourcePcm = rawSlice,
                sourceChannels = session.channelCount,
                sourceSampleRate = session.sampleRate,
                targetSampleRate = 44100,
                startTargetFrameIndex = startFrame44k,
                targetFrameCount = frameCount
            )

            // Convert to 16-bit PCM ShortArray
            val output = ShortArray(frameCount * 2)
            for (i in output.indices) {
                output[i] = (floatResampled[i] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            }

            // Trim window if it exceeds maximum size to prevent unbounded memory growth
            val maxSamples = BUFFER_WINDOW_FRAMES * session.channelCount
            if (session.windowSamples.size > maxSamples) {
                val dropSamples = session.windowSamples.size - (maxSamples / 2)
                val dropFrames = dropSamples / session.channelCount
                session.windowSamples.subList(0, dropFrames * session.channelCount).clear()
                session.windowStartFrame += dropFrames
            }

            return output
        }
    }

    private fun getOrCreateSession(uri: String): DecoderSession? {
        sessions[uri]?.let { return it }

        return try {
            val extractor = MediaExtractor()
            if (uri.startsWith("/") || uri.startsWith("file://")) {
                val filePath = if (uri.startsWith("file://")) uri.substring(7) else uri
                val f = File(filePath)
                if (!f.exists()) return null
                extractor.setDataSource(f.absolutePath)
            } else if (context != null) {
                extractor.setDataSource(context, Uri.parse(uri), null)
            } else {
                extractor.setDataSource(uri)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val session = DecoderSession(
                uri = uri,
                extractor = extractor,
                codec = codec,
                sampleRate = sampleRate,
                channelCount = channelCount
            )
            sessions[uri] = session
            session
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize decoder for $uri", e)
            null
        }
    }

    private fun seekAndPreroll(session: DecoderSession, targetSourceFrame: Long) {
        val targetUs = (targetSourceFrame * 1_000_000.0 / session.sampleRate).toLong()
        session.extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        session.codec.flush()
        session.windowSamples.clear()
        session.isEos = false

        val actualSeekUs = session.extractor.sampleTime
        val actualStartFrame = if (actualSeekUs >= 0) {
            (actualSeekUs * session.sampleRate / 1_000_000L)
        } else {
            targetSourceFrame
        }
        session.windowStartFrame = actualStartFrame
        session.currentSourceFrame = actualStartFrame
    }

    private fun decodeNextChunk(session: DecoderSession) {
        val bufferInfo = MediaCodec.BufferInfo()

        // Feed input to decoder
        if (!session.isEos) {
            val inIndex = session.codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                val inBuffer = session.codec.getInputBuffer(inIndex)
                if (inBuffer != null) {
                    val sampleSize = session.extractor.readSampleData(inBuffer, 0)
                    if (sampleSize < 0) {
                        session.codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val sampleTime = session.extractor.sampleTime
                        session.codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                        session.extractor.advance()
                    }
                }
            }
        }

        // Drain output from decoder
        val outIndex = session.codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
        when {
            outIndex >= 0 -> {
                val outBuffer = session.codec.getOutputBuffer(outIndex)
                if (outBuffer != null && bufferInfo.size > 0) {
                    outBuffer.position(bufferInfo.offset)
                    outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val shortBuf = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val count = shortBuf.remaining()
                    val pcmArray = ShortArray(count)
                    shortBuf.get(pcmArray)

                    for (s in pcmArray) {
                        session.windowSamples.add(s)
                    }
                }
                session.codec.releaseOutputBuffer(outIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    session.isEos = true
                }
            }
            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val newFormat = session.codec.outputFormat
                if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    session.sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    session.channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }
    }

    override fun close() {
        for (session in sessions.values) {
            try {
                session.codec.stop()
                session.codec.release()
                session.extractor.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing audio decoder session", e)
            }
        }
        sessions.clear()
    }
}
