package com.hometalk.app.audio

/**
 * Microphone capture, Opus encode/decode, and speaker playback.
 * Implemented in Phase 3. Encoder and decoder stay behind these interfaces
 * so the native implementation can be replaced later.
 */
interface AudioEngine {
    suspend fun startPlayback()
    suspend fun stopPlayback()
    suspend fun startCapture()
    suspend fun stopCapture()
}

interface OpusEncoder {
    fun encode(pcm: ShortArray, encoded: ByteArray): Int
    fun release()
}

interface OpusDecoder {
    fun decode(encoded: ByteArray, encodedLength: Int, pcm: ShortArray): Int
    fun release()
}
