package com.hometalk.app.domain.model

data class PairingSession(
    val pin: String,
    val expiresAtEpochMs: Long,
    val localHostAddress: String?,
    val controlPort: Int = NetworkDefaults.TCP_CONTROL_PORT,
) {
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        nowEpochMs >= expiresAtEpochMs
}

data class PairingQrPayload(
    val protocolVersion: Int,
    val deviceId: String,
    val deviceName: String,
    val roomId: String,
    val roomName: String,
    val hostAddress: String,
    val controlPort: Int,
    val pin: String,
)
