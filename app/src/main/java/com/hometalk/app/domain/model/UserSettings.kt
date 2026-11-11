package com.hometalk.app.domain.model

data class UserSettings(
    val deviceId: String,
    val deviceName: String,
    val roomName: String,
    val roomId: String,
    val speakerVolume: Float,
    val notificationsEnabled: Boolean,
    val audioBitrateKbps: Int,
    val manualPeerAddresses: List<String>,
    val keepServiceInBackground: Boolean,
    val speakerEnabled: Boolean,
) {
    companion object {
        const val DEFAULT_ROOM_NAME = "Family"
        const val DEFAULT_SPEAKER_VOLUME = 0.85f
        const val DEFAULT_AUDIO_BITRATE_KBPS = 24
        const val MIN_AUDIO_BITRATE_KBPS = 24
        const val MAX_AUDIO_BITRATE_KBPS = 32

        fun uninitialized(): UserSettings = UserSettings(
            deviceId = "",
            deviceName = "",
            roomName = DEFAULT_ROOM_NAME,
            roomId = "",
            speakerVolume = DEFAULT_SPEAKER_VOLUME,
            notificationsEnabled = true,
            audioBitrateKbps = DEFAULT_AUDIO_BITRATE_KBPS,
            manualPeerAddresses = emptyList(),
            keepServiceInBackground = true,
            speakerEnabled = true,
        )
    }
}
