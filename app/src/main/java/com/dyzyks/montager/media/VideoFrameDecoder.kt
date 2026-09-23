package com.dyzyks.montager.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.LruCache
import java.io.File
import java.nio.ByteBuffer

/**
 * Sequential per-source frame-accurate video decoder.
 * Adheres strictly to Rule #1:
 * NEVER uses MediaMetadataRetriever.getFrameAtTime for playback or export.
 * Uses MediaExtractor + MediaCodec sequentially advancing frame-by-frame,
 * seeking only on backward or large-forward jumps, with exact frame preroll.
 */
class VideoFrameDecoder(
    private val sourceUri: String,
    private val context: Context? = null
) : AutoCloseable {

    companion object {
        private const val TAG = "VideoFrameDecoder"
        private const val TIMEOUT_US = 15_000L
        private const val MAX_FORWARD_JUMP_WITHOUT_SEEK_US = 1_000_000L // 1.0s
    }

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    var videoWidth: Int = 1920
        private set
    var videoHeight: Int = 1080
        private set
    var rotationDegrees: Int = 0
        private set
    var durationUs: Long = 0L
        private set

    private var currentPositionUs: Long = -1L
    private var isEos: Boolean = false
    private var isInitialized = false

    // Bounded LRU cache of recently decoded bitmaps to avoid repeated decodes during timeline scrub
    private val frameCache = LruCache<Long, Bitmap>(30)

    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized) return true

        try {
            // Read rotation and duration metadata safely
            val retriever = MediaMetadataRetriever()
            try {
                if (sourceUri.startsWith("/") || sourceUri.startsWith("file://")) {
                    val path = if (sourceUri.startsWith("file://")) sourceUri.substring(7) else sourceUri
                    retriever.setDataSource(path)
                } else if (context != null) {
                    retriever.setDataSource(context, Uri.parse(sourceUri))
                } else {
                    retriever.setDataSource(sourceUri)
                }
                val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                rotationDegrees = rotStr?.toIntOrNull() ?: 0
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationUs = (durStr?.toLongOrNull() ?: 0L) * 1000L
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract metadata via retriever for $sourceUri", e)
            } finally {
                retriever.release()
            }

            val ext = MediaExtractor()
            if (sourceUri.startsWith("/") || sourceUri.startsWith("file://")) {
                val path = if (sourceUri.startsWith("file://")) sourceUri.substring(7) else sourceUri
                val f = File(path)
                if (!f.exists()) return false
                ext.setDataSource(f.absolutePath)
            } else if (context != null) {
                ext.setDataSource(context, Uri.parse(sourceUri), null)
            } else {
                ext.setDataSource(sourceUri)
            }

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until ext.trackCount) {
                val format = ext.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                ext.release()
                return false
            }

            ext.selectTrack(videoTrackIndex)
            videoWidth = if (videoFormat.containsKey(MediaFormat.KEY_WIDTH)) videoFormat.getInteger(MediaFormat.KEY_WIDTH) else 1920
            videoHeight = if (videoFormat.containsKey(MediaFormat.KEY_HEIGHT)) videoFormat.getInteger(MediaFormat.KEY_HEIGHT) else 1080
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                rotationDegrees = videoFormat.getInteger(MediaFormat.KEY_ROTATION)
            }

            handlerThread = HandlerThread("VideoDecoder-$sourceUri").apply { start() }
            handler = Handler(handlerThread!!.looper)

            // Setup ImageReader
            imageReader = ImageReader.newInstance(
                videoWidth,
                videoHeight,
                android.graphics.ImageFormat.YUV_420_888,
                3
            )

            val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val dec = MediaCodec.createDecoderByType(mime)
            dec.configure(videoFormat, imageReader!!.surface, null, 0)
            dec.start()

            this.extractor = ext
            this.codec = dec
            this.isInitialized = true
            this.currentPositionUs = 0L
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed for $sourceUri", e)
            close()
            return false
        }
    }

    /**
     * Retrieves the exact video frame at [targetUs] in source time.
     * Advances frame-by-frame sequentially without seeking when possible.
     * Seeks with preroll only when scrubbing backwards or leaping forward.
     */
    @Synchronized
    fun getFrameAtUs(targetUs: Long): Bitmap? {
        val cached = frameCache.get(targetUs)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        if (!initialize()) {
            return createPlaceholderFrame(targetUs)
        }

        val dec = codec ?: return null
        val ext = extractor ?: return null
        val reader = imageReader ?: return null

        val deltaUs = targetUs - currentPositionUs
        if (deltaUs < 0 || deltaUs > MAX_FORWARD_JUMP_WITHOUT_SEEK_US) {
            // Seek to previous keyframe and preroll
            ext.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            dec.flush()
            isEos = false
            currentPositionUs = ext.sampleTime
        }

        val info = MediaCodec.BufferInfo()
        var targetBitmap: Bitmap? = null
        val wallClockTimeoutMs = 800L
        val startTime = System.currentTimeMillis()

        while (targetBitmap == null && (System.currentTimeMillis() - startTime) < wallClockTimeoutMs) {
            // Feed input
            if (!isEos) {
                val inIdx = dec.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val inBuf = dec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        val sampleSize = ext.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            dec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val pts = ext.sampleTime
                            dec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                            ext.advance()
                        }
                    }
                }
            }

            // Drain output
            val outIdx = dec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIdx >= 0 -> {
                    val framePts = info.presentationTimeUs
                    val isPastOrAtTarget = framePts >= (targetUs - 16_666L) // within ~1 frame window
                    dec.releaseOutputBuffer(outIdx, true)

                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        try {
                            if (isPastOrAtTarget || isEos) {
                                val bmp = yuv420ToBitmap(image, videoWidth, videoHeight, rotationDegrees)
                                targetBitmap = bmp
                                currentPositionUs = framePts
                            }
                        } finally {
                            image.close()
                        }
                    }

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFmt = dec.outputFormat
                    if (newFmt.containsKey(MediaFormat.KEY_WIDTH)) videoWidth = newFmt.getInteger(MediaFormat.KEY_WIDTH)
                    if (newFmt.containsKey(MediaFormat.KEY_HEIGHT)) videoHeight = newFmt.getInteger(MediaFormat.KEY_HEIGHT)
                }
            }
        }

        val result = targetBitmap ?: createPlaceholderFrame(targetUs)
        frameCache.put(targetUs, result)
        return result
    }

    private fun yuv420ToBitmap(image: Image, width: Int, height: Int, rotation: Int): Bitmap {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val argbArray = IntArray(width * height)

        for (y in 0 until height) {
            val yOffset = y * yRowStride
            val uvOffset = (y shr 1) * uvRowStride

            for (x in 0 until width) {
                val yVal = (yBuffer.get(yOffset + x).toInt() and 0xFF)
                val uvIndex = uvOffset + (x shr 1) * uvPixelStride
                val uVal = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val vVal = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                // YUV to RGB conversion
                val r = (yVal + 1.370705f * vVal).toInt().coerceIn(0, 255)
                val g = (yVal - 0.337633f * uVal - 0.698001f * vVal).toInt().coerceIn(0, 255)
                val b = (yVal + 1.732446f * uVal).toInt().coerceIn(0, 255)

                argbArray[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        var bmp = Bitmap.createBitmap(argbArray, width, height, Bitmap.Config.ARGB_8888)
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, true)
            if (rotated != bmp) {
                bmp.recycle()
                bmp = rotated
            }
        }
        return bmp
    }

    private fun createPlaceholderFrame(targetUs: Long): Bitmap {
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.rgb(20, 20, 24))
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 20.0f
            isAntiAlias = true
        }
        val sec = targetUs / 1_000_000.0
        canvas.drawText(String.format("FRAME: %.2fs", sec), 30f, 90f, paint)
        return bmp
    }

    override fun close() {
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {}
        try {
            extractor?.release()
        } catch (_: Exception) {}
        try {
            imageReader?.close()
        } catch (_: Exception) {}
        try {
            handlerThread?.quitSafely()
        } catch (_: Exception) {}

        codec = null
        extractor = null
        imageReader = null
        handlerThread = null
        isInitialized = false
        frameCache.evictAll()
    }
}
