package com.hometalk.app.domain.model

data class Device(
    val id: String,
    val name: String,
    val isOnline: Boolean,
    val isPaired: Boolean,
    val lastSeenEpochMs: Long?,
    val hostAddress: String? = null,
    val controlPort: Int? = null,
    val signalQuality: ConnectionQuality = ConnectionQuality.Unknown,
)

enum class ConnectionQuality {
    Unknown,
    Poor,
    Fair,
    Good,
    Excellent,
}
