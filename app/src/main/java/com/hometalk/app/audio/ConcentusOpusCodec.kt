package com.hometalk.app.audio

import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder as ConcentusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder as ConcentusEncoder
import io.github.jaredmdobson.concentus.OpusSignal
import javax.inject.Inject

class ConcentusOpusEncoder @Inject constructor() : OpusEncoder {
    private val frameSize = NetworkDefaults.SAMPLE_RATE_HZ * NetworkDefaults.FRAME_DURATION_MS / 1_000
    private var encoder: ConcentusEncoder? = null

    fun open(bitrateKbps: Int) {
        release()
        val opus = ConcentusEncoder(
            NetworkDefaults.SAMPLE_RATE_HZ,
            NetworkDefaults.CHANNELS,
            OpusApplication.OPUS_APPLICATION_VOIP,
        )
        opus.bitrate = bitrateKbps.coerceIn(24, 32) * 1_000
        opus.signalType = OpusSignal.OPUS_SIGNAL_VOICE
        opus.complexity = 5
        encoder = opus
        HomeTalkLog.i(LogArea.AUDIO, "Opus encoder opened bitrate=${bitrateKbps}kbps frame=$frameSize")
    }

    override fun encode(pcm: ShortArray, encoded: ByteArray): Int {
        val opus = encoder ?: return 0
        return try {
            opus.encode(pcm, 0, frameSize, encoded, 0, encoded.size)
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.AUDIO, "Opus encode failed", error)
            0
        }
    }

    override fun release() {
        encoder = null
    }
}

class ConcentusOpusDecoder @Inject constructor() : OpusDecoder {
    private val frameSize = NetworkDefaults.SAMPLE_RATE_HZ * NetworkDefaults.FRAME_DURATION_MS / 1_000
    private var decoder: ConcentusDecoder? = null

    fun open() {
        release()
        decoder = ConcentusDecoder(NetworkDefaults.SAMPLE_RATE_HZ, NetworkDefaults.CHANNELS)
        HomeTalkLog.i(LogArea.AUDIO, "Opus decoder opened frame=$frameSize")
    }

    override fun decode(encoded: ByteArray, encodedLength: Int, pcm: ShortArray): Int {
        val opus = decoder ?: return 0
        return try {
            opus.decode(encoded, 0, encodedLength, pcm, 0, frameSize, false)
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.AUDIO, "Opus decode failed", error)
            0
        }
    }

    override fun release() {
        decoder = null
    }
}
