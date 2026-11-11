package com.hometalk.app.domain.model

/**
 * Half-duplex channel snapshot. Arbitration is implemented in Phase 3.
 */
data class ChannelState(
    val isBusy: Boolean = false,
    val currentTransmitterId: String? = null,
    val currentTransmitterName: String? = null,
    val transmissionId: String? = null,
    val leaseExpiresAtEpochMs: Long? = null,
)
