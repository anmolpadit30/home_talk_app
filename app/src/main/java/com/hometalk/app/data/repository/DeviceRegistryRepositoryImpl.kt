package com.hometalk.app.data.repository

import com.hometalk.app.domain.model.Device
import com.hometalk.app.domain.repository.DeviceRegistryRepository
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory registry. Phase 2 will persist paired devices and update this
 * from NSD plus the TCP control channel.
 */
@Singleton
class DeviceRegistryRepositoryImpl @Inject constructor() : DeviceRegistryRepository {
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    override val devices: Flow<List<Device>> = _devices.asStateFlow()

    override suspend fun upsert(device: Device) {
        _devices.update { current ->
            val without = current.filterNot { it.id == device.id }
            HomeTalkLog.d(LogArea.DISCOVERY, "Upsert device id=${device.id} name=${device.name} online=${device.isOnline}")
            without + device
        }
    }

    override suspend fun remove(deviceId: String) {
        _devices.update { current -> current.filterNot { it.id == deviceId } }
        HomeTalkLog.d(LogArea.DISCOVERY, "Removed device id=$deviceId")
    }

    override suspend fun markOffline(deviceId: String, lastSeenEpochMs: Long) {
        _devices.update { current ->
            current.map { device ->
                if (device.id == deviceId) {
                    device.copy(isOnline = false, lastSeenEpochMs = lastSeenEpochMs)
                } else {
                    device
                }
            }
        }
    }

    override suspend fun clear() {
        _devices.value = emptyList()
    }
}
