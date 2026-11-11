package com.hometalk.app.domain.repository

import com.hometalk.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun ensureInitialized()
    suspend fun updateDeviceName(name: String)
    suspend fun updateRoomName(name: String)
    suspend fun updateRoomIdentity(roomId: String, roomName: String)
    suspend fun updateSpeakerVolume(volume: Float)
    suspend fun updateNotificationsEnabled(enabled: Boolean)
    suspend fun updateAudioBitrateKbps(bitrateKbps: Int)
    suspend fun updateManualPeerAddresses(addresses: List<String>)
    suspend fun updateKeepServiceInBackground(enabled: Boolean)
    suspend fun updateSpeakerEnabled(enabled: Boolean)
}
