package com.hometalk.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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
            // USAGE_MEDIA + MODE_NORMAL loudspeaker path. Do not pin preferredDevice —
            // that silenced playback on several OEM builds.
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
            track.play()
            audioTrack = track
            HomeTalkLog.i(LogArea.AUDIO, "AudioTrack started buffer=$bufferSize")
            true
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.AUDIO, "AudioTrack start failed", error)
            false
        }
    }

    fun ensureLoudspeaker() {
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.AUDIO, "Speakerphone toggle failed", error)
        }
    }

    /** @return false if the track is missing or cannot be resumed. */
    fun ensurePlaying(): Boolean {
        val track = audioTrack ?: return false
        return try {
            if (track.state != AudioTrack.STATE_INITIALIZED) return false
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            track.playState == AudioTrack.PLAYSTATE_PLAYING
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.AUDIO, "AudioTrack resume failed", error)
            false
        }
    }

    /** OEMs often pause a silent MEDIA track after long screen-off — rebuild. */
    fun forceRebuild(): Boolean {
        HomeTalkLog.i(LogArea.AUDIO, "AudioTrack force rebuild")
        return start()
    }

    fun write(pcm: ShortArray, length: Int) {
        if (length <= 0) return
        val track = audioTrack ?: return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            runCatching { track.play() }
        }
        val written = track.write(pcm, 0, length)
        if (written < 0) {
            HomeTalkLog.w(LogArea.AUDIO, "AudioTrack write error=$written — restarting")
            start()
        }
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
}
