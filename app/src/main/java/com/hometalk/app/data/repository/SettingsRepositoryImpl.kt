package com.hometalk.app.data.repository

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.hometalk.app.data.preferences.PreferenceCodec
import com.hometalk.app.data.preferences.PreferenceKeys
import com.hometalk.app.domain.model.UserSettings
import com.hometalk.app.domain.repository.SettingsRepository
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        .catch { error ->
            HomeTalkLog.e(LogArea.PREFS, "Failed to read preferences", error)
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> prefs.toUserSettings() }

    override suspend fun ensureInitialized() {
        dataStore.edit { prefs ->
            if (prefs[PreferenceKeys.DEVICE_ID].isNullOrBlank()) {
                val id = UUID.randomUUID().toString()
                prefs[PreferenceKeys.DEVICE_ID] = id
                HomeTalkLog.i(LogArea.PREFS, "Generated deviceId=$id")
            }
            if (prefs[PreferenceKeys.ROOM_ID].isNullOrBlank()) {
                val roomId = UUID.randomUUID().toString()
                prefs[PreferenceKeys.ROOM_ID] = roomId
                HomeTalkLog.i(LogArea.PREFS, "Generated roomId=$roomId")
            }
            if (prefs[PreferenceKeys.DEVICE_NAME].isNullOrBlank()) {
                prefs[PreferenceKeys.DEVICE_NAME] = Build.MODEL.ifBlank { "HomeTalk Phone" }
            }
            if (prefs[PreferenceKeys.ROOM_NAME].isNullOrBlank()) {
                prefs[PreferenceKeys.ROOM_NAME] = UserSettings.DEFAULT_ROOM_NAME
            }
            if (prefs[PreferenceKeys.SPEAKER_VOLUME] == null) {
                prefs[PreferenceKeys.SPEAKER_VOLUME] = UserSettings.DEFAULT_SPEAKER_VOLUME
            }
            if (prefs[PreferenceKeys.NOTIFICATIONS_ENABLED] == null) {
                prefs[PreferenceKeys.NOTIFICATIONS_ENABLED] = true
            }
            if (prefs[PreferenceKeys.AUDIO_BITRATE_KBPS] == null) {
                prefs[PreferenceKeys.AUDIO_BITRATE_KBPS] = UserSettings.DEFAULT_AUDIO_BITRATE_KBPS
            }
            if (prefs[PreferenceKeys.KEEP_SERVICE_IN_BACKGROUND] == null) {
                prefs[PreferenceKeys.KEEP_SERVICE_IN_BACKGROUND] = true
            }
            if (prefs[PreferenceKeys.SPEAKER_ENABLED] == null) {
                prefs[PreferenceKeys.SPEAKER_ENABLED] = true
            }
        }
    }

    override suspend fun updateDeviceName(name: String) {
        val trimmed = name.trim().ifBlank { Build.MODEL.ifBlank { "HomeTalk Phone" } }
        edit { this[PreferenceKeys.DEVICE_NAME] = trimmed }
    }

    override suspend fun updateRoomName(name: String) {
        val trimmed = name.trim().ifBlank { UserSettings.DEFAULT_ROOM_NAME }
        edit {
            this[PreferenceKeys.ROOM_NAME] = trimmed
            if (this[PreferenceKeys.ROOM_ID].isNullOrBlank()) {
                this[PreferenceKeys.ROOM_ID] = UUID.randomUUID().toString()
            }
        }
    }

    override suspend fun updateRoomIdentity(roomId: String, roomName: String) {
        val trimmedRoomId = roomId.trim()
        if (trimmedRoomId.isBlank()) return
        val trimmedName = roomName.trim().ifBlank { UserSettings.DEFAULT_ROOM_NAME }
        edit {
            this[PreferenceKeys.ROOM_ID] = trimmedRoomId
            this[PreferenceKeys.ROOM_NAME] = trimmedName
        }
    }

    override suspend fun updateSpeakerVolume(volume: Float) {
        edit { this[PreferenceKeys.SPEAKER_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    override suspend fun updateNotificationsEnabled(enabled: Boolean) {
        edit { this[PreferenceKeys.NOTIFICATIONS_ENABLED] = enabled }
    }

    override suspend fun updateAudioBitrateKbps(bitrateKbps: Int) {
        val clamped = bitrateKbps.coerceIn(
            UserSettings.MIN_AUDIO_BITRATE_KBPS,
            UserSettings.MAX_AUDIO_BITRATE_KBPS,
        )
        edit { this[PreferenceKeys.AUDIO_BITRATE_KBPS] = clamped }
    }

    override suspend fun updateManualPeerAddresses(addresses: List<String>) {
        val cleaned = addresses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        edit { this[PreferenceKeys.MANUAL_PEER_ADDRESSES] = PreferenceCodec.encodeAddresses(cleaned) }
    }

    override suspend fun updateKeepServiceInBackground(enabled: Boolean) {
        edit { this[PreferenceKeys.KEEP_SERVICE_IN_BACKGROUND] = enabled }
    }

    override suspend fun updateSpeakerEnabled(enabled: Boolean) {
        edit { this[PreferenceKeys.SPEAKER_ENABLED] = enabled }
    }

    private suspend fun edit(transform: suspend MutablePreferences.() -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.PREFS, "Failed to write preferences", error)
        }
    }

    private fun Preferences.toUserSettings(): UserSettings = UserSettings(
        deviceId = this[PreferenceKeys.DEVICE_ID].orEmpty(),
        deviceName = this[PreferenceKeys.DEVICE_NAME].orEmpty(),
        roomName = this[PreferenceKeys.ROOM_NAME] ?: UserSettings.DEFAULT_ROOM_NAME,
        roomId = this[PreferenceKeys.ROOM_ID].orEmpty(),
        speakerVolume = this[PreferenceKeys.SPEAKER_VOLUME] ?: UserSettings.DEFAULT_SPEAKER_VOLUME,
        notificationsEnabled = this[PreferenceKeys.NOTIFICATIONS_ENABLED] ?: true,
        audioBitrateKbps = this[PreferenceKeys.AUDIO_BITRATE_KBPS]
            ?: UserSettings.DEFAULT_AUDIO_BITRATE_KBPS,
        manualPeerAddresses = PreferenceCodec.decodeAddresses(this[PreferenceKeys.MANUAL_PEER_ADDRESSES]),
        keepServiceInBackground = this[PreferenceKeys.KEEP_SERVICE_IN_BACKGROUND] ?: true,
        speakerEnabled = this[PreferenceKeys.SPEAKER_ENABLED] ?: true,
    )
}
