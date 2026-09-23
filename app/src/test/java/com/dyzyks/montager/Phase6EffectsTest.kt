package com.dyzyks.montager

import android.graphics.Bitmap
import android.graphics.Color
import com.dyzyks.montager.effects.EffectsEngine
import com.dyzyks.montager.effects.TransitionEngine
import com.dyzyks.montager.model.ColorGrading
import com.dyzyks.montager.model.EasingType
import com.dyzyks.montager.model.TransitionType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase6EffectsTest {

    private fun createSolidBitmap(color: Int, w: Int = 100, h: Int = 100): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    @Test
    fun testEasingCurves() {
        // Easing interpolation fractions at t = 0, 0.5, 1.0
        assertEquals(0.0f, EasingType.LINEAR.interpolate(0.0f), 0.001f)
        assertEquals(0.5f, EasingType.LINEAR.interpolate(0.5f), 0.001f)
        assertEquals(1.0f, EasingType.LINEAR.interpolate(1.0f), 0.001f)

        // Ease in starts slower: (0.5)^2 = 0.25 < 0.5
        assertEquals(0.25f, EasingType.EASE_IN.interpolate(0.5f), 0.001f)

        // Ease out starts faster: 0.5 * (2 - 0.5) = 0.75 > 0.5
        assertEquals(0.75f, EasingType.EASE_OUT.interpolate(0.5f), 0.001f)

        // Ease in-out is symmetric: at t=0.5 it equals 0.5
        assertEquals(0.5f, EasingType.EASE_IN_OUT.interpolate(0.5f), 0.001f)
    }

    @Test
    fun testEffectsEngineApplication() {
        val testBmp = createSolidBitmap(Color.GRAY, 200, 200)

        // Zoom pulse
        val pulsed = EffectsEngine.applyZoomPulse(testBmp, progress = 0.5f, intensity = 0.8f)
        assertNotNull(pulsed)
        assertEquals(200, pulsed.width)
        assertEquals(200, pulsed.height)

        // Screen shake
        val shaken = EffectsEngine.applyScreenShake(testBmp, timeUs = 100_000L, intensity = 1.0f)
        assertNotNull(shaken)

        // RGB Split
        val split = EffectsEngine.applyRgbSplit(testBmp, intensity = 0.5f)
        assertNotNull(split)

        // Flash
        val flashed = EffectsEngine.applyFlash(testBmp, intensity = 1.0f, progress = 0.2f)
        assertNotNull(flashed)

        // Vignette
        val vignetted = EffectsEngine.applyVignette(testBmp, intensity = 0.7f)
        assertNotNull(vignetted)

        // Color Grading
        val graded = EffectsEngine.applyColorGrading(testBmp, ColorGrading(saturation = 0.0f)) // Grayscale
        assertNotNull(graded)
    }

    @Test
    fun testTransitionEngineCrossfadeAndDips() {
        val redFrame = createSolidBitmap(Color.RED, 100, 100)
        val blueFrame = createSolidBitmap(Color.BLUE, 100, 100)

        // Crossfade at 0.5
        val crossfadeMid = TransitionEngine.compositeTransition(
            redFrame, blueFrame, TransitionType.CROSSFADE, progress = 0.5f
        )
        assertNotNull(crossfadeMid)

        // Wipe Left
        val wipeLeft = TransitionEngine.compositeTransition(
            redFrame, blueFrame, TransitionType.WIPE_LEFT, progress = 0.3f
        )
        assertNotNull(wipeLeft)

        // Dip to Black at midpoint (progress 0.5 should be pure black or very dark)
        val dipBlackMid = TransitionEngine.compositeTransition(
            redFrame, blueFrame, TransitionType.DIP_TO_BLACK, progress = 0.5f
        )
        assertNotNull(dipBlackMid)
        val midPixel = dipBlackMid.getPixel(50, 50)
        val r = Color.red(midPixel)
        val g = Color.green(midPixel)
        val b = Color.blue(midPixel)
        // Check that it's predominantly darkened / black
        assertTrue("Expected dark pixel at dip midpoint but got R=$r G=$g B=$b", r <= 20 && g <= 20 && b <= 20)
    }
}
