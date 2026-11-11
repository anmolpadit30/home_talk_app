package com.hometalk.app.domain.repository

import com.hometalk.app.domain.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRegistryRepository {
    val devices: Flow<List<Device>>
    suspend fun upsert(device: Device)
    suspend fun remove(deviceId: String)
    suspend fun markOffline(deviceId: String, lastSeenEpochMs: Long)
    suspend fun clear()
}
