package com.hometalk.app.domain.repository

import com.hometalk.app.domain.model.PairedDeviceRecord
import kotlinx.coroutines.flow.Flow

interface PairedDevicesRepository {
    val pairedDevices: Flow<List<PairedDeviceRecord>>
    suspend fun getSnapshot(): List<PairedDeviceRecord>
    suspend fun upsert(record: PairedDeviceRecord)
    suspend fun remove(deviceId: String)
    suspend fun isPaired(deviceId: String): Boolean
}
