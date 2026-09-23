package com.dyzyks.montager.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.dyzyks.montager.model.*
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dedicated visual effects processing engine.
 * Applies zoom pulse, screen shake, RGB split, cyber glitch, impact flash, vignette, and color grading.
 */
object EffectsEngine {

    fun applyColorGrading(input: Bitmap, grading: ColorGrading): Bitmap {
        if (grading.isNeutral()) return input

        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val matrix = ColorMatrix()
        matrix.setSaturation(grading.saturation)

        val contrast = grading.contrast
        val brightness = grading.brightness * 255.0f
        val scale = contrast
        val translate = (-0.5f * contrast + 0.5f) * 255.0f + brightness

        val cbMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(cbMatrix)

        if (grading.temperature != 0.0f) {
            val rShift = grading.temperature * 30.0f
            val bShift = -grading.temperature * 30.0f
            val tempMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, rShift,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, bShift,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(tempMatrix)
        }

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(input, 0f, 0f, paint)
        return output
    }

    fun applyZoomPulse(input: Bitmap, progress: Float, intensity: Float): Bitmap {
        val pulse = 1.0f + (intensity * 0.35f * sin(progress * Math.PI.toFloat()))
        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val matrix = Matrix().apply {
            postScale(pulse, pulse, input.width / 2.0f, input.height / 2.0f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(input, matrix, paint)
        return output
    }

    fun applyScreenShake(input: Bitmap, timeUs: Long, intensity: Float): Bitmap {
        val shakeAmp = intensity * 25.0f
        val rng = Random(timeUs / 25_000L)
        val dx = (rng.nextFloat() * 2.0f - 1.0f) * shakeAmp
        val dy = (rng.nextFloat() * 2.0f - 1.0f) * shakeAmp

        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val matrix = Matrix().apply { postTranslate(dx, dy) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(input, matrix, paint)
        return output
    }

    fun applyRgbSplit(input: Bitmap, intensity: Float): Bitmap {
        val offset = intensity * 20.0f
        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Red pass shifted left
        val rPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val rMatrix = Matrix().apply { postTranslate(-offset, 0f) }
        canvas.drawBitmap(input, rMatrix, rPaint)

        // Green & Blue pass shifted right
        val gbPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val gbMatrix = Matrix().apply { postTranslate(offset, 0f) }
        canvas.drawBitmap(input, gbMatrix, gbPaint)

        return output
    }

    fun applyVignette(input: Bitmap, intensity: Float): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val radius = maxOf(input.width, input.height) * 0.75f
        val vigShader = RadialGradient(
            input.width / 2.0f, input.height / 2.0f, radius,
            intArrayOf(Color.TRANSPARENT, Color.argb((intensity * 230).toInt(), 0, 0, 0)),
            floatArrayOf(0.4f, 1.0f),
            Shader.TileMode.CLAMP
        )
        val vigPaint = Paint().apply { shader = vigShader }
        canvas.drawRect(0f, 0f, input.width.toFloat(), input.height.toFloat(), vigPaint)
        return output
    }

    fun applyFlash(input: Bitmap, intensity: Float, progress: Float): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val alpha = (intensity * (1.0f - progress).coerceIn(0f, 1f) * 255.0f).toInt()
        if (alpha > 0) {
            val paint = Paint().apply {
                color = Color.WHITE
                this.alpha = alpha
            }
            canvas.drawRect(0f, 0f, input.width.toFloat(), input.height.toFloat(), paint)
        }
        return output
    }
}
