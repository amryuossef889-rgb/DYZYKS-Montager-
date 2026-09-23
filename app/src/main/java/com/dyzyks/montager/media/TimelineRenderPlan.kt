package com.dyzyks.montager.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import com.dyzyks.montager.model.*
import kotlin.math.sin
import kotlin.random.Random

/**
 * TimelineRenderPlan is the SINGLE source of truth for both live preview and final export.
 * Given the project model, it evaluates active clips, trims, transforms, keyframes,
 * effects, transitions, and audio settings, producing:
 * (a) The composited video Bitmap at any instant in time.
 * (b) The mixed stereo PCM ShortArray for any audio time range.
 */
class TimelineRenderPlan(
    val project: Project,
    private val videoDecoderProvider: ((sourceUri: String) -> VideoFrameDecoder?)? = null,
    private val audioReader: AudioFrameReader? = null
) {

    /**
     * Composites all active video and text tracks at [timelineUs] into a single Bitmap of size [targetWidth]x[targetHeight].
     */
    fun renderVideoFrame(timelineUs: Long, targetWidth: Int, targetHeight: Int): Bitmap {
        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.rgb(20, 20, 22)) // DaVinci Resolve near-black background

        val videoTracks = project.videoTracks
        val anySolo = videoTracks.any { it.isSolo }

        for (track in videoTracks) {
            if (track.isMuted) continue
            if (anySolo && !track.isSolo) continue

            val clip = track.getClipAt(timelineUs) ?: continue
            renderClipOnCanvas(canvas, clip, timelineUs, targetWidth, targetHeight)
        }

        return outputBitmap
    }

    private fun renderClipOnCanvas(
        canvas: Canvas,
        clip: TimelineClip,
        timelineUs: Long,
        width: Int,
        height: Int
    ) {
        val clipTimeUs = timelineUs - clip.timelineStartUs

        if (clip.mediaType == MediaType.VIDEO) {
            val sourceUs = clip.mapTimelineToSourceUs(timelineUs)
            val sourceFrame = videoDecoderProvider?.invoke(clip.sourceUri)?.getFrameAtUs(sourceUs)
                ?: createTestPatternBitmap(clip.name, width, height, sourceUs)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // 1. Color Grading
            if (!clip.colorGrading.isNeutral()) {
                val cm = buildColorMatrix(clip.colorGrading)
                paint.colorFilter = ColorMatrixColorFilter(cm)
            }

            // 2. Opacity from Transform / Keyframes
            val resolvedOpacity = clip.transform.getResolvedOpacity(clipTimeUs)
            paint.alpha = (resolvedOpacity * 255.0f).toInt().coerceIn(0, 255)

            // 3. Transform Matrix (Position, Scale, Rotation)
            var scaleX = clip.transform.scaleX
            var scaleY = clip.transform.scaleY
            var posX = clip.transform.positionX * (width / 2.0f)
            var posY = clip.transform.positionY * (height / 2.0f)
            var rot = clip.transform.rotationDeg

            // 4. Effects modulation
            var isFlashActive = false
            var flashIntensity = 0.0f
            var isVignetteActive = false
            var vignetteIntensity = 0.0f
            var rgbSplitOffset = 0.0f
            var isGlitchActive = false

            for (eff in clip.effects) {
                if (eff.isActiveAt(clipTimeUs)) {
                    val progress = eff.getProgressAt(clipTimeUs)
                    when (eff.type) {
                        EffectType.ZOOM_PULSE -> {
                            val pulse = 1.0f + (eff.intensity * 0.4f * sin(progress * Math.PI.toFloat()))
                            scaleX *= pulse
                            scaleY *= pulse
                        }
                        EffectType.SHAKE -> {
                            val shakeAmp = eff.intensity * 30.0f
                            val seed = (clipTimeUs / 20_000L) // 50Hz jitter
                            val rng = Random(seed)
                            posX += (rng.nextFloat() * 2.0f - 1.0f) * shakeAmp
                            posY += (rng.nextFloat() * 2.0f - 1.0f) * shakeAmp
                        }
                        EffectType.RGB_SPLIT -> {
                            rgbSplitOffset = eff.intensity * 25.0f
                        }
                        EffectType.GLITCH -> {
                            isGlitchActive = true
                        }
                        EffectType.FLASH -> {
                            isFlashActive = true
                            flashIntensity = eff.intensity * (1.0f - progress)
                        }
                        EffectType.VIGNETTE -> {
                            isVignetteActive = true
                            vignetteIntensity = eff.intensity
                        }
                        EffectType.COLOR_GRADING -> {}
                    }
                }
            }

            // Transitions (e.g. Crossfade / Wipe / Dip)
            var transitionAlphaMult = 1.0f
            var wipeFraction: Float? = null
            var dipColor: Int? = null

            clip.transitionIn?.let { tr ->
                if (clipTimeUs < tr.durationUs) {
                    val progress = (clipTimeUs.toFloat() / tr.durationUs.toFloat()).coerceIn(0.0f, 1.0f)
                    when (tr.type) {
                        TransitionType.CROSSFADE -> transitionAlphaMult *= progress
                        TransitionType.WIPE_LEFT -> wipeFraction = progress
                        TransitionType.WIPE_RIGHT -> wipeFraction = 1.0f - progress
                        TransitionType.DIP_TO_BLACK -> {
                            dipColor = Color.BLACK
                            transitionAlphaMult *= progress
                        }
                        TransitionType.DIP_TO_WHITE -> {
                            dipColor = Color.WHITE
                            transitionAlphaMult *= progress
                        }
                    }
                }
            }

            clip.transitionOut?.let { tr ->
                val remainingUs = clip.durationUs - clipTimeUs
                if (remainingUs < tr.durationUs) {
                    val progress = (remainingUs.toFloat() / tr.durationUs.toFloat()).coerceIn(0.0f, 1.0f)
                    when (tr.type) {
                        TransitionType.CROSSFADE -> transitionAlphaMult *= progress
                        TransitionType.WIPE_LEFT -> wipeFraction = 1.0f - progress
                        TransitionType.WIPE_RIGHT -> wipeFraction = progress
                        TransitionType.DIP_TO_BLACK -> {
                            dipColor = Color.BLACK
                            transitionAlphaMult *= progress
                        }
                        TransitionType.DIP_TO_WHITE -> {
                            dipColor = Color.WHITE
                            transitionAlphaMult *= progress
                        }
                    }
                }
            }

            paint.alpha = (paint.alpha * transitionAlphaMult).toInt().coerceIn(0, 255)

            // Draw bitmap with transform
            val matrix = Matrix()
            // Center scaling & rotation
            val srcW = sourceFrame.width.toFloat()
            val srcH = sourceFrame.height.toFloat()
            val baseScale = minOf(width / srcW, height / srcH)

            matrix.postScale(baseScale * scaleX, baseScale * scaleY, srcW / 2.0f, srcH / 2.0f)
            matrix.postRotate(rot, srcW / 2.0f, srcH / 2.0f)
            val dx = (width - srcW) / 2.0f + posX
            val dy = (height - srcH) / 2.0f + posY
            matrix.postTranslate(dx, dy)

            canvas.save()
            wipeFraction?.let { wf ->
                val clipRight = width * wf
                canvas.clipRect(0f, 0f, clipRight, height.toFloat())
            }

            if (rgbSplitOffset > 0.0f) {
                // Draw RGB Split with slight horizontal displacement
                val rPaint = Paint(paint).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                        set(floatArrayOf(
                            1f, 0f, 0f, 0f, 0f,
                            0f, 0f, 0f, 0f, 0f,
                            0f, 0f, 0f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    })
                }
                val rMatrix = Matrix(matrix).apply { postTranslate(-rgbSplitOffset, 0f) }
                canvas.drawBitmap(sourceFrame, rMatrix, rPaint)

                val bPaint = Paint(paint).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                        set(floatArrayOf(
                            0f, 0f, 0f, 0f, 0f,
                            0f, 1f, 0f, 0f, 0f,
                            0f, 0f, 1f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    })
                }
                val bMatrix = Matrix(matrix).apply { postTranslate(rgbSplitOffset, 0f) }
                canvas.drawBitmap(sourceFrame, bMatrix, bPaint)
            } else {
                canvas.drawBitmap(sourceFrame, matrix, paint)
            }

            // Glitch horizontal slices
            if (isGlitchActive) {
                val glitchPaint = Paint(paint)
                val sliceHeight = height / 10
                for (s in 0 until 10) {
                    if (s % 3 == 0) {
                        val sliceY = s * sliceHeight
                        val sliceJitter = ((s * 37 + (clipTimeUs / 50_000L)) % 40) - 20
                        canvas.save()
                        canvas.clipRect(0, sliceY, width, sliceY + sliceHeight)
                        val gMatrix = Matrix(matrix).apply { postTranslate(sliceJitter.toFloat(), 0f) }
                        canvas.drawBitmap(sourceFrame, gMatrix, glitchPaint)
                        canvas.restore()
                    }
                }
            }

            // Flash overlay
            if (isFlashActive && flashIntensity > 0.0f) {
                val flashPaint = Paint().apply {
                    color = Color.WHITE
                    alpha = (flashIntensity.coerceIn(0.0f, 1.0f) * 255.0f).toInt()
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
            }

            // Vignette overlay
            if (isVignetteActive && vignetteIntensity > 0.0f) {
                val radius = maxOf(width, height) * 0.75f
                val vigShader = RadialGradient(
                    width / 2.0f, height / 2.0f, radius,
                    intArrayOf(Color.TRANSPARENT, Color.argb((vignetteIntensity * 220).toInt(), 0, 0, 0)),
                    floatArrayOf(0.4f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                val vigPaint = Paint().apply { shader = vigShader }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vigPaint)
            }

            dipColor?.let { dc ->
                val dipPaint = Paint().apply {
                    color = dc
                    alpha = ((1.0f - transitionAlphaMult) * 255.0f).toInt()
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dipPaint)
            }

            canvas.restore()
        }

        // 5. Text Overlay rendering
        clip.textOverlay?.let { txt ->
            renderTextOverlay(canvas, txt, width, height)
        }
    }

    private fun renderTextOverlay(canvas: Canvas, txt: TextOverlay, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = txt.fontSizeSp * (width / 1080.0f) * txt.scale
            color = txt.textColorArgb.toInt()
            alpha = (txt.opacity * 255.0f).toInt().coerceIn(0, 255)
            typeface = if (txt.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        val textBounds = android.graphics.Rect()
        paint.getTextBounds(txt.text, 0, txt.text.length, textBounds)

        val cx = width / 2.0f + txt.positionX * (width / 2.0f)
        val cy = height / 2.0f + txt.positionY * (height / 2.0f)

        // Draw background pill
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = txt.backgroundColorArgb.toInt()
            alpha = (txt.opacity * 200.0f).toInt().coerceIn(0, 255)
        }
        val padX = 24f * (width / 1080.0f)
        val padY = 16f * (height / 1920.0f)
        val rect = android.graphics.RectF(
            cx - (textBounds.width() / 2.0f) - padX,
            cy - textBounds.height() - padY,
            cx + (textBounds.width() / 2.0f) + padX,
            cy + padY
        )
        canvas.drawRoundRect(rect, 16f, 16f, bgPaint)
        canvas.drawText(txt.text, cx, cy, paint)
    }

    /**
     * Builds a ColorMatrix for brightness, contrast, saturation, and temperature.
     */
    private fun buildColorMatrix(cg: ColorGrading): ColorMatrix {
        val matrix = ColorMatrix()

        // Saturation
        matrix.setSaturation(cg.saturation)

        // Contrast and Brightness
        val contrast = cg.contrast
        val brightness = cg.brightness * 255.0f
        val scale = contrast
        val translate = (-0.5f * contrast + 0.5f) * 255.0f + brightness

        val cbMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(cbMatrix)

        // Temperature (cool to warm: boost red, lower blue or vice versa)
        if (cg.temperature != 0.0f) {
            val rShift = cg.temperature * 30.0f
            val bShift = -cg.temperature * 30.0f
            val tempMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, rShift,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, bShift,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(tempMatrix)
        }

        return matrix
    }

    private fun createTestPatternBitmap(name: String, width: Int, height: Int, sourceUs: Long): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(64), height.coerceAtLeast(64), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(25, 30, 45))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(74, 158, 255)
            textSize = 28.0f
            textAlign = Paint.Align.CENTER
        }
        val sec = sourceUs / 1_000_000.0
        c.drawText(name, width / 2.0f, height / 2.0f - 20f, p)
        p.textSize = 20.0f
        p.color = Color.LTGRAY
        c.drawText(String.format("TIME: %.2fs", sec), width / 2.0f, height / 2.0f + 25f, p)
        return bmp
    }

    /**
     * Renders mixed 16-bit stereo PCM audio for the timeline interval [timelineStartUs, timelineStartUs + durationUs].
     */
    fun renderAudioChunk(
        timelineStartUs: Long,
        durationUs: Long,
        targetSampleRate: Int = 44100
    ): ShortArray {
        val frameCount = ((durationUs * targetSampleRate) / 1_000_000L).toInt()
        if (frameCount <= 0) return ShortArray(0)

        val timelineEndUs = timelineStartUs + durationUs
        val startFrame44k = (timelineStartUs * targetSampleRate) / 1_000_000L

        val tracks = project.tracks
        val anySolo = tracks.any { it.isSolo }

        val activeBuffers = mutableListOf<FloatArray>()

        for (track in tracks) {
            if (track.isMuted) continue
            if (anySolo && !track.isSolo) continue

            for (clip in track.clips) {
                // Check overlap with requested timeline window
                if (clip.timelineStartUs >= timelineEndUs || clip.timelineEndUs <= timelineStartUs) {
                    continue
                }

                val clipBuffer = FloatArray(frameCount * 2)

                // Window overlap calculations
                val overlapStartUs = maxOf(timelineStartUs, clip.timelineStartUs)
                val overlapEndUs = minOf(timelineEndUs, clip.timelineEndUs)
                val overlapDurationUs = overlapEndUs - overlapStartUs

                val startFrameOffset = (((overlapStartUs - timelineStartUs) * targetSampleRate) / 1_000_000L).toInt()
                val overlapFrames = (((overlapDurationUs * targetSampleRate) / 1_000_000L)).toInt().coerceAtMost(frameCount - startFrameOffset)

                if (overlapFrames > 0 && audioReader != null) {
                    val clipSourceStartUs = clip.mapTimelineToSourceUs(overlapStartUs)
                    val sourceStartFrame = (clipSourceStartUs * targetSampleRate) / 1_000_000L

                    val rawPcm = audioReader.readFrames(clip.sourceUri, sourceStartFrame, overlapFrames)
                    for (f in 0 until overlapFrames) {
                        val outIdx = (startFrameOffset + f) * 2
                        val inIdx = f * 2
                        if (inIdx + 1 < rawPcm.size && outIdx + 1 < clipBuffer.size) {
                            clipBuffer[outIdx] = rawPcm[inIdx] / 32768.0f
                            clipBuffer[outIdx + 1] = rawPcm[inIdx + 1] / 32768.0f
                        }
                    }
                }

                // Apply channel strip (gain, pan, fades)
                val clipStartUsInWindow = overlapStartUs - clip.timelineStartUs
                PcmAudioMixer.processChannelStrip(
                    buffer = clipBuffer,
                    frameCount = frameCount,
                    startTimelineUs = clipStartUsInWindow,
                    clipDurationUs = clip.durationUs,
                    settings = clip.audioSettings
                )

                activeBuffers.add(clipBuffer)
            }
        }

        return PcmAudioMixer.mixAndQuantize(activeBuffers, frameCount)
    }
}
