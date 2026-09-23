package com.dyzyks.montager.media

interface AudioFrameReader : AutoCloseable {
    /**
     * Reads exactly [frameCount] stereo 16-bit PCM frames at 44.1kHz starting at [startFrame44k].
     * Returns a ShortArray of size (frameCount * 2).
     */
    fun readFrames(uri: String, startFrame44k: Long, frameCount: Int): ShortArray
}
