package com.hometalk.app.domain.model

/**
 * Who is currently speaking for UI / presence.
 * Full duplex: local TX does not block remote TX (or the other way around).
 */
data class ChannelState(
    /** True when at least one remote (or local announce) talker is active. */
    val isBusy: Boolean = false,
    val currentTransmitterId: String? = null,
    val currentTransmitterName: String? = null,
    val transmissionId: String? = null,
    val leaseExpiresAtEpochMs: Long? = null,
)
