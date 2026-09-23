package com.dyzyks.montager.export

import android.media.MediaFormat
import java.io.File

data class ExportConfig(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val videoBitrate: Int = 12_000_000,
    val audioBitrate: Int = 192_000,
    val audioSampleRate: Int = 44100,
    val audioChannelCount: Int = 2,
    val videoMimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    val audioMimeType: String = MediaFormat.MIMETYPE_AUDIO_AAC,
    val outputFile: File
)
