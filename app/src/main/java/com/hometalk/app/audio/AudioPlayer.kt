package com.hometalk.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AudioPlayer @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val frameSize = NetworkDefaults.SAMPLE_RATE_HZ * NetworkDefaults.FRAME_DURATION_MS / 1_000
    private var audioTrack: AudioTrack? = null

    fun start(): Boolean {
        stop()
        val minBuffer = AudioTrack.getMinBufferSize(
            NetworkDefaults.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            HomeTalkLog.e(LogArea.AUDIO, "AudioTrack min buffer invalid: $minBuffer")
            return false
        }
        val bufferSize = maxOf(minBuffer, frameSize * 2 * 8)
        return try {
            // USAGE_MEDIA plays on the loudspeaker by default. VOICE_COMMUNICATION
            // tends to route to the earpiece on many OEMs.
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(NetworkDefaults.SAMPLE_RATE_HZ)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            routeToBuiltinSpeaker(track)
            track.play()
            audioTrack = track
            HomeTalkLog.i(LogArea.AUDIO, "AudioTrack started on loudspeaker buffer=$bufferSize")
            true
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.AUDIO, "AudioTrack start failed", error)
            false
        }
    }

    fun ensureLoudspeaker() {
        val track = audioTrack ?: return
        routeToBuiltinSpeaker(track)
    }

    fun write(pcm: ShortArray, length: Int) {
        val track = audioTrack ?: return
        if (length <= 0) return
        track.write(pcm, 0, length)
    }

    fun setVolume(volume: Float) {
        audioTrack?.setVolume(volume.coerceIn(0f, 1f))
    }

    fun stop() {
        val track = audioTrack ?: return
        audioTrack = null
        try {
            track.pause()
            track.flush()
            track.stop()
        } catch (_: Exception) {
        }
        try {
            track.release()
        } catch (_: Exception) {
        }
        HomeTalkLog.i(LogArea.AUDIO, "AudioTrack stopped")
    }

    private fun routeToBuiltinSpeaker(track: AudioTrack) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val speaker = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
        if (speaker != null) {
            val ok = track.setPreferredDevice(speaker)
            HomeTalkLog.d(LogArea.AUDIO, "Preferred loudspeaker set=$ok id=${speaker.id}")
        } else {
            HomeTalkLog.w(LogArea.AUDIO, "Built-in loudspeaker device not found")
        }
    }
}
