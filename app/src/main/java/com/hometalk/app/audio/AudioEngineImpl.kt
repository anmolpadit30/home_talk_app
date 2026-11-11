package com.hometalk.app.audio

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

fun interface EncodedFrameListener {
    fun onEncodedFrame(encoded: ByteArray, length: Int, sequence: Int, timestampEpochMs: Long)
}

@Singleton
class AudioEngineImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val recorder: AudioRecorder,
    private val player: AudioPlayer,
    private val encoder: ConcentusOpusEncoder,
    private val decoder: ConcentusOpusDecoder,
) : AudioEngine {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val frameSize = NetworkDefaults.SAMPLE_RATE_HZ * NetworkDefaults.FRAME_DURATION_MS / 1_000
    private val capturing = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)

    private var captureJob: Job? = null
    private var scope: CoroutineScope? = null
    private var encodedFrameListener: EncodedFrameListener? = null
    private var focusRequest: AudioFocusRequest? = null
    private var previousMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphone: Boolean = false
    private var sequence: Int = 0

    fun setEncodedFrameListener(listener: EncodedFrameListener?) {
        encodedFrameListener = listener
    }

    fun setPlaybackVolume(volume: Float) {
        player.setVolume(volume)
    }

    fun ensureLoudspeaker() {
        forceLoudspeaker()
        player.ensureLoudspeaker()
    }

    override suspend fun startPlayback() {
        if (!playing.compareAndSet(false, true)) return
        configureCommunicationAudio()
        decoder.open()
        if (!player.start()) {
            playing.set(false)
            abandonAudioFocus()
            HomeTalkLog.e(LogArea.AUDIO, "Playback failed to start")
            return
        }
        forceLoudspeaker()
        player.ensureLoudspeaker()
    }

    override suspend fun stopPlayback() {
        if (!playing.compareAndSet(true, false)) return
        player.stop()
        decoder.release()
        if (!capturing.get()) {
            restoreAudioRouting()
            abandonAudioFocus()
        }
    }

    override suspend fun startCapture() {
        if (!capturing.compareAndSet(false, true)) return
        configureCommunicationAudio()
        forceLoudspeaker()
        val bitrate = 28
        encoder.open(bitrate)
        if (!recorder.start()) {
            capturing.set(false)
            encoder.release()
            HomeTalkLog.e(LogArea.AUDIO, "Capture failed to start")
            return
        }
        sequence = 0
        val localScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = localScope
        val pcm = ShortArray(frameSize)
        val encoded = ByteArray(400)
        captureJob = localScope.launch {
            HomeTalkLog.i(LogArea.AUDIO, "Capture loop running")
            while (isActive && capturing.get()) {
                val read = recorder.read(pcm)
                if (read < frameSize) continue
                val length = encoder.encode(pcm, encoded)
                if (length <= 0) continue
                val payload = encoded.copyOf(length)
                val seq = sequence++
                encodedFrameListener?.onEncodedFrame(
                    encoded = payload,
                    length = length,
                    sequence = seq,
                    timestampEpochMs = System.currentTimeMillis(),
                )
            }
            HomeTalkLog.i(LogArea.AUDIO, "Capture loop stopped")
        }
    }

    override suspend fun stopCapture() {
        if (!capturing.compareAndSet(true, false)) return
        captureJob?.cancel()
        captureJob = null
        recorder.stop()
        encoder.release()
        scope = null
        if (!playing.get()) {
            restoreAudioRouting()
            abandonAudioFocus()
        } else {
            // Keep loudspeaker for ongoing receive.
            forceLoudspeaker()
            player.ensureLoudspeaker()
        }
    }

    fun decodeAndPlay(encoded: ByteArray, encodedLength: Int) {
        if (!playing.get()) return
        val pcm = ShortArray(frameSize)
        val samples = decoder.decode(encoded, encodedLength, pcm)
        if (samples > 0) {
            player.write(pcm, samples)
        }
    }

    private fun configureCommunicationAudio() {
        previousMode = audioManager.mode
        @Suppress("DEPRECATION")
        previousSpeakerphone = audioManager.isSpeakerphoneOn
        requestAudioFocus()
        // MODE_NORMAL + MEDIA usage is more reliable for loudspeaker walkie playback.
        // Keep communication mode only while capturing to help echo control.
        if (capturing.get()) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } else {
            audioManager.mode = AudioManager.MODE_NORMAL
        }
        forceLoudspeaker()
    }

    private fun forceLoudspeaker() {
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Clear any communication device override that may pin earpiece.
                runCatching { audioManager.clearCommunicationDevice() }
            }
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.AUDIO, "Failed to force loudspeaker", error)
        }
    }

    private fun restoreAudioRouting() {
        audioManager.mode = previousMode
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = previousSpeakerphone
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
