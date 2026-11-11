package com.hometalk.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.homeTalkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hometalk_preferences",
)

object PreferenceKeys {
    val DEVICE_ID = stringPreferencesKey("device_id")
    val DEVICE_NAME = stringPreferencesKey("device_name")
    val ROOM_NAME = stringPreferencesKey("room_name")
    val ROOM_ID = stringPreferencesKey("room_id")
    val SPEAKER_VOLUME = floatPreferencesKey("speaker_volume")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val AUDIO_BITRATE_KBPS = intPreferencesKey("audio_bitrate_kbps")
    val MANUAL_PEER_ADDRESSES = stringPreferencesKey("manual_peer_addresses")
    val KEEP_SERVICE_IN_BACKGROUND = booleanPreferencesKey("keep_service_in_background")
    val SPEAKER_ENABLED = booleanPreferencesKey("speaker_enabled")
    val PAIRED_DEVICES_JSON = stringPreferencesKey("paired_devices_json")
}

object PreferenceCodec {
    private const val ADDRESS_SEPARATOR = "\n"

    fun encodeAddresses(addresses: List<String>): String =
        addresses.joinToString(ADDRESS_SEPARATOR)

    fun decodeAddresses(raw: String?): List<String> =
        raw.orEmpty()
            .split(ADDRESS_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
