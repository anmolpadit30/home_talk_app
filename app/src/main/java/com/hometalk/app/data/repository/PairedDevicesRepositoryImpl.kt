package com.hometalk.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.hometalk.app.data.preferences.PairedDevicesCodec
import com.hometalk.app.data.preferences.PreferenceKeys
import com.hometalk.app.domain.model.PairedDeviceRecord
import com.hometalk.app.domain.repository.PairedDevicesRepository
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairedDevicesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PairedDevicesRepository {

    override val pairedDevices: Flow<List<PairedDeviceRecord>> = dataStore.data
        .catch { error ->
            HomeTalkLog.e(LogArea.PREFS, "Failed to read paired devices", error)
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs -> PairedDevicesCodec.decode(prefs[PreferenceKeys.PAIRED_DEVICES_JSON]) }

    override suspend fun getSnapshot(): List<PairedDeviceRecord> = pairedDevices.first()

    override suspend fun upsert(record: PairedDeviceRecord) {
        mutate { current ->
            current.filterNot { it.id == record.id } + record
        }
        HomeTalkLog.i(LogArea.DISCOVERY, "Paired device saved id=${record.id} name=${record.name}")
    }

    override suspend fun remove(deviceId: String) {
        mutate { current -> current.filterNot { it.id == deviceId } }
        HomeTalkLog.i(LogArea.DISCOVERY, "Unpaired device id=$deviceId")
    }

    override suspend fun isPaired(deviceId: String): Boolean =
        getSnapshot().any { it.id == deviceId }

    private suspend fun mutate(transform: (List<PairedDeviceRecord>) -> List<PairedDeviceRecord>) {
        try {
            dataStore.edit { prefs ->
                val current = PairedDevicesCodec.decode(prefs[PreferenceKeys.PAIRED_DEVICES_JSON])
                prefs[PreferenceKeys.PAIRED_DEVICES_JSON] = PairedDevicesCodec.encode(transform(current))
            }
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.PREFS, "Failed to update paired devices", error)
        }
    }
}
