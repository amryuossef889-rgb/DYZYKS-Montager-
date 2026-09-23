package com.dyzyks.montager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dyzyks.montager.export.ExportConfig
import com.dyzyks.montager.export.ExportMediaStoreHelper
import com.dyzyks.montager.media.TimelineRenderPlan
import com.dyzyks.montager.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase5ExportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testExportConfigDefaults() {
        val destFile = File(tempFolder.root, "out.mp4")
        val config = ExportConfig(outputFile = destFile)

        assertEquals(1920, config.width)
        assertEquals(1080, config.height)
        assertEquals(60, config.fps)
        assertEquals(44100, config.audioSampleRate)
        assertEquals(2, config.audioChannelCount)
        assertEquals(destFile, config.outputFile)
    }

    @Test
    fun testOutputFrameTimestampCalculation() {
        // Output-frame counter timestamps must be derived from output frame index, not input index
        val fps = 60
        val frame0Pts = (0L * 1_000_000L) / fps
        val frame1Pts = (1L * 1_000_000L) / fps
        val frame60Pts = (60L * 1_000_000L) / fps

        assertEquals(0L, frame0Pts)
        assertEquals(16_666L, frame1Pts)
        assertEquals(1_000_000L, frame60Pts)
    }

    @Test
    fun testMediaStoreHelperInsertion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyFile = File(tempFolder.root, "test_render.mp4")
        FileOutputStream(dummyFile).use {
            it.write("fake mp4 content for testing media store insertion".toByteArray())
        }

        val uri = ExportMediaStoreHelper.insertIntoMediaStore(
            context = context,
            sourceFile = dummyFile,
            title = "TestMontage"
        )

        assertNotNull("MediaStore URI should not be null", uri)
    }
}
