package com.hometalk.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import javax.inject.Inject

class AudioRecorder @Inject constructor() {
    private val frameSize = NetworkDefaults.SAMPLE_RATE_HZ * NetworkDefaults.FRAME_DURATION_MS / 1_000
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        stop()
        val minBuffer = AudioRecord.getMinBufferSize(
            NetworkDefaults.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            HomeTalkLog.e(LogArea.AUDIO, "AudioRecord min buffer invalid: $minBuffer")
            return false
        }
        val bufferSize = maxOf(minBuffer, frameSize * 2 * 4)
        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                NetworkDefaults.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                HomeTalkLog.e(LogArea.AUDIO, "AudioRecord not initialized")
                return false
            }
            record.startRecording()
            audioRecord = record
            HomeTalkLog.i(LogArea.AUDIO, "AudioRecord started buffer=$bufferSize")
            true
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.AUDIO, "AudioRecord start failed", error)
            false
        }
    }

    fun read(frame: ShortArray): Int {
        val record = audioRecord ?: return -1
        return record.read(frame, 0, frame.size)
    }

    fun stop() {
        val record = audioRecord ?: return
        audioRecord = null
        try {
            record.stop()
        } catch (_: Exception) {
        }
        try {
            record.release()
        } catch (_: Exception) {
        }
        HomeTalkLog.i(LogArea.AUDIO, "AudioRecord stopped")
    }
}
