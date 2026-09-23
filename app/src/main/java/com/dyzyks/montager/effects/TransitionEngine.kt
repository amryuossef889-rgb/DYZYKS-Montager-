package com.dyzyks.montager.effects

import android.graphics.Bitmap
import com.dyzyks.montager.model.TransitionType

object TransitionEngine {

    /**
     * Composites two video frames (fromFrame and toFrame) across a transition with normalized progress [0.0f, 1.0f].
     * Uses direct pixel buffer blending for 100% deterministic cross-platform rendering across hardware and JVM/Robolectric.
     */
    fun compositeTransition(
        fromFrame: Bitmap,
        toFrame: Bitmap,
        type: TransitionType,
        progress: Float
    ): Bitmap {
        val p = progress.coerceIn(0.0f, 1.0f)
        val width = fromFrame.width
        val height = fromFrame.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        when (type) {
            TransitionType.CROSSFADE -> {
                val pixelsFrom = IntArray(width * height)
                val pixelsTo = IntArray(width * height)
                fromFrame.getPixels(pixelsFrom, 0, width, 0, 0, width, height)
                toFrame.getPixels(pixelsTo, 0, width, 0, 0, width, height)
                val outPixels = IntArray(width * height)
                val invP = 1.0f - p

                for (i in outPixels.indices) {
                    val cf = pixelsFrom[i]
                    val ct = pixelsTo[i]
                    val a = (cf ushr 24) and 0xFF
                    val rf = (cf ushr 16) and 0xFF
                    val gf = (cf ushr 8) and 0xFF
                    val bf = cf and 0xFF
                    val rt = (ct ushr 16) and 0xFF
                    val gt = (ct ushr 8) and 0xFF
                    val bt = ct and 0xFF

                    val r = (rf * invP + rt * p).toInt().coerceIn(0, 255)
                    val g = (gf * invP + gt * p).toInt().coerceIn(0, 255)
                    val b = (bf * invP + bt * p).toInt().coerceIn(0, 255)
                    outPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                output.setPixels(outPixels, 0, width, 0, 0, width, height)
            }

            TransitionType.WIPE_LEFT -> {
                val wipeX = (width * p).toInt().coerceIn(0, width)
                val pixelsFrom = IntArray(width * height)
                val pixelsTo = IntArray(width * height)
                fromFrame.getPixels(pixelsFrom, 0, width, 0, 0, width, height)
                toFrame.getPixels(pixelsTo, 0, width, 0, 0, width, height)
                val outPixels = IntArray(width * height)

                for (y in 0 until height) {
                    val row = y * width
                    for (x in 0 until width) {
                        outPixels[row + x] = if (x < wipeX) pixelsTo[row + x] else pixelsFrom[row + x]
                    }
                }
                output.setPixels(outPixels, 0, width, 0, 0, width, height)
            }

            TransitionType.WIPE_RIGHT -> {
                val wipeX = (width * (1.0f - p)).toInt().coerceIn(0, width)
                val pixelsFrom = IntArray(width * height)
                val pixelsTo = IntArray(width * height)
                fromFrame.getPixels(pixelsFrom, 0, width, 0, 0, width, height)
                toFrame.getPixels(pixelsTo, 0, width, 0, 0, width, height)
                val outPixels = IntArray(width * height)

                for (y in 0 until height) {
                    val row = y * width
                    for (x in 0 until width) {
                        outPixels[row + x] = if (x >= wipeX) pixelsTo[row + x] else pixelsFrom[row + x]
                    }
                }
                output.setPixels(outPixels, 0, width, 0, 0, width, height)
            }

            TransitionType.DIP_TO_BLACK -> {
                val frame = if (p <= 0.5f) fromFrame else toFrame
                val factor = if (p <= 0.5f) (1.0f - p * 2.0f) else ((p - 0.5f) * 2.0f)
                val pixels = IntArray(width * height)
                frame.getPixels(pixels, 0, width, 0, 0, width, height)

                for (i in pixels.indices) {
                    val c = pixels[i]
                    val a = (c ushr 24) and 0xFF
                    val r = (((c ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
                    val g = (((c ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
                    val b = ((c and 0xFF) * factor).toInt().coerceIn(0, 255)
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                output.setPixels(pixels, 0, width, 0, 0, width, height)
            }

            TransitionType.DIP_TO_WHITE -> {
                val frame = if (p <= 0.5f) fromFrame else toFrame
                val factor = if (p <= 0.5f) (p * 2.0f) else (1.0f - (p - 0.5f) * 2.0f)
                val pixels = IntArray(width * height)
                frame.getPixels(pixels, 0, width, 0, 0, width, height)

                for (i in pixels.indices) {
                    val c = pixels[i]
                    val a = (c ushr 24) and 0xFF
                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF
                    val nr = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
                    val ng = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
                    val nb = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
                    pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
                output.setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }

        return output
    }
}
