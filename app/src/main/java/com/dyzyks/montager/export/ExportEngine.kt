package com.dyzyks.montager.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import com.dyzyks.montager.media.TimelineRenderPlan
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExportEngine(
    private val renderPlan: TimelineRenderPlan,
    private val config: ExportConfig,
    private val onProgress: ((progress: Float) -> Unit)? = null,
    private val isCancelled: (() -> Boolean)? = null
) {

    companion object {
        private const val TAG = "ExportEngine"
        private const val TIMEOUT_US = 10_000L
        private const val DRAIN_WALL_CLOCK_TIMEOUT_MS = 45_000L
    }

    private data class QueuedSample(
        val isAudio: Boolean,
        val data: ByteArray,
        val offset: Int,
        val size: Int,
        val presentationTimeUs: Long,
        val flags: Int
    )

    fun export(): Boolean {
        val totalDurationUs = renderPlan.project.durationUs
        if (totalDurationUs <= 0L) {
            Log.e(TAG, "Cannot export empty project (duration <= 0)")
            return false
        }

        val totalFrames = ((totalDurationUs * config.fps) / 1_000_000L).coerceAtLeast(1L)
        val parentDir = config.outputFile.parentFile
        if (parentDir != null && !parentDir.exists()) parentDir.mkdirs()

        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        val pendingSamples = mutableListOf<QueuedSample>()

        try {
            // 1. Configure Video Encoder (H.264 AVC)
            val videoFormat = MediaFormat.createVideoFormat(config.videoMimeType, config.width, config.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval
            }
            videoEncoder = MediaCodec.createEncoderByType(config.videoMimeType)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            // 2. Configure Audio Encoder (AAC)
            val audioFormat = MediaFormat.createAudioFormat(config.audioMimeType, config.audioSampleRate, config.audioChannelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrate)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            audioEncoder = MediaCodec.createEncoderByType(config.audioMimeType)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            // 3. Configure MediaMuxer
            muxer = MediaMuxer(config.outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Presentation timestamp trackers
            var videoOutputFrameCount = 0L
            var audioOutputFrameCount = 0L

            fun writeOrBufferSample(
                isAudio: Boolean,
                buffer: ByteBuffer,
                info: MediaCodec.BufferInfo
            ) {
                if (muxerStarted) {
                    val trackIdx = if (isAudio) audioTrackIndex else videoTrackIndex
                    if (trackIdx >= 0) {
                        muxer?.writeSampleData(trackIdx, buffer, info)
                    }
                } else {
                    // Buffer pending samples (MISTAKE #3 prevention)
                    val bytes = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(bytes)
                    pendingSamples.add(
                        QueuedSample(
                            isAudio = isAudio,
                            data = bytes,
                            offset = 0,
                            size = info.size,
                            presentationTimeUs = info.presentationTimeUs,
                            flags = info.flags
                        )
                    )
                }
            }

            fun checkStartMuxer() {
                if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                    val m = muxer ?: return
                    m.start()
                    muxerStarted = true
                    Log.i(TAG, "Both tracks configured. Muxer started! Flushing ${pendingSamples.size} buffered samples.")

                    for (s in pendingSamples) {
                        val trackIdx = if (s.isAudio) audioTrackIndex else videoTrackIndex
                        val b = ByteBuffer.wrap(s.data, s.offset, s.size)
                        val info = MediaCodec.BufferInfo().apply {
                            set(s.offset, s.size, s.presentationTimeUs, s.flags)
                        }
                        m.writeSampleData(trackIdx, b, info)
                    }
                    pendingSamples.clear()
                }
            }

            fun drainEncoders(drainAllToEos: Boolean) {
                val vBufferInfo = MediaCodec.BufferInfo()
                val aBufferInfo = MediaCodec.BufferInfo()
                var videoEos = false
                var audioEos = false
                val drainStartMs = System.currentTimeMillis()

                while (true) {
                    if (isCancelled?.invoke() == true) return

                    var activity = false

                    // Drain Video Encoder
                    if (!videoEos) {
                        val vOutIdx = videoEncoder.dequeueOutputBuffer(vBufferInfo, TIMEOUT_US)
                        when {
                            vOutIdx >= 0 -> {
                                activity = true
                                val vOutBuf = videoEncoder.getOutputBuffer(vOutIdx)
                                if (vOutBuf != null && (vBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && vBufferInfo.size > 0) {
                                    // Derive output timestamps from output-frame counter (MISTAKE #4 prevention)
                                    val ptsUs = (videoOutputFrameCount * 1_000_000L) / config.fps
                                    vBufferInfo.presentationTimeUs = ptsUs
                                    videoOutputFrameCount++

                                    writeOrBufferSample(false, vOutBuf, vBufferInfo)
                                }
                                videoEncoder.releaseOutputBuffer(vOutIdx, false)
                                if ((vBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    videoEos = true
                                }
                            }
                            vOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                activity = true
                                videoTrackIndex = muxer!!.addTrack(videoEncoder.outputFormat)
                                checkStartMuxer()
                            }
                        }
                    }

                    // Drain Audio Encoder
                    if (!audioEos) {
                        val aOutIdx = audioEncoder.dequeueOutputBuffer(aBufferInfo, TIMEOUT_US)
                        when {
                            aOutIdx >= 0 -> {
                                activity = true
                                val aOutBuf = audioEncoder.getOutputBuffer(aOutIdx)
                                if (aOutBuf != null && (aBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && aBufferInfo.size > 0) {
                                    writeOrBufferSample(true, aOutBuf, aBufferInfo)
                                }
                                audioEncoder.releaseOutputBuffer(aOutIdx, false)
                                if ((aBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    audioEos = true
                                }
                            }
                            aOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                activity = true
                                audioTrackIndex = muxer!!.addTrack(audioEncoder.outputFormat)
                                checkStartMuxer()
                            }
                        }
                    }

                    if (!drainAllToEos) {
                        if (!activity) break
                    } else {
                        // Drain loop until true EOS with wall-clock timeout (MISTAKE #5 prevention)
                        if (videoEos && audioEos) break
                        if (System.currentTimeMillis() - drainStartMs > DRAIN_WALL_CLOCK_TIMEOUT_MS) {
                            Log.w(TAG, "Drain reached wall-clock timeout ($DRAIN_WALL_CLOCK_TIMEOUT_MS ms)")
                            break
                        }
                    }
                }
            }

            // 4. Encode Audio in bounded chunks
            var audioTimelinePosUs = 0L
            val audioChunkDurationUs = 100_000L // 100ms
            var audioDone = false

            while (!audioDone) {
                if (isCancelled?.invoke() == true) return false
                val isLastChunk = (audioTimelinePosUs + audioChunkDurationUs >= totalDurationUs)
                val chunkDur = if (isLastChunk) totalDurationUs - audioTimelinePosUs else audioChunkDurationUs

                val pcm = renderPlan.renderAudioChunk(audioTimelinePosUs, chunkDur, config.audioSampleRate)

                val inIdx = audioEncoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val inBuf = audioEncoder.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        inBuf.clear()
                        inBuf.order(ByteOrder.LITTLE_ENDIAN)
                        val shortBuf = inBuf.asShortBuffer()
                        shortBuf.put(pcm)
                        val bytesSize = pcm.size * 2
                        val ptsUs = audioTimelinePosUs
                        val flags = if (isLastChunk) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        audioEncoder.queueInputBuffer(inIdx, 0, bytesSize, ptsUs, flags)
                    }
                    audioTimelinePosUs += chunkDur
                    if (isLastChunk) audioDone = true
                }

                drainEncoders(drainAllToEos = false)
            }

            // 5. Encode Video frame-by-frame
            for (f in 0 until totalFrames) {
                if (isCancelled?.invoke() == true) return false

                val frameTimelineUs = (f * 1_000_000L) / config.fps
                val frameBitmap = renderPlan.renderVideoFrame(frameTimelineUs, config.width, config.height)

                // Render onto inputSurface
                val canvas: Canvas = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    inputSurface.lockHardwareCanvas()
                } else {
                    inputSurface.lockCanvas(null)
                }

                canvas.drawBitmap(frameBitmap, 0f, 0f, null)
                inputSurface.unlockCanvasAndPost(canvas)
                frameBitmap.recycle()

                onProgress?.invoke(f.toFloat() / totalFrames.toFloat())
                drainEncoders(drainAllToEos = false)
            }

            // Signal video EOS
            videoEncoder.signalEndOfInputStream()

            // Final drain to EOS with wall-clock timeout
            drainEncoders(drainAllToEos = true)

            onProgress?.invoke(1.0f)
            Log.i(TAG, "Export completed successfully to ${config.outputFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed with exception", e)
            return false
        } finally {
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping muxer", e)
            }
            try {
                videoEncoder?.stop()
                videoEncoder?.release()
            } catch (_: Exception) {}
            try {
                audioEncoder?.stop()
                audioEncoder?.release()
            } catch (_: Exception) {}
            inputSurface?.release()
        }
    }
}
