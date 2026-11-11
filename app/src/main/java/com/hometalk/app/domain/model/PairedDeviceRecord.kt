package com.hometalk.app.domain.model

data class PairedDeviceRecord(
    val id: String,
    val name: String,
    val hostAddress: String? = null,
    val controlPort: Int? = NetworkDefaults.TCP_CONTROL_PORT,
    val pairedAtEpochMs: Long,
    val lastSeenEpochMs: Long? = null,
)
