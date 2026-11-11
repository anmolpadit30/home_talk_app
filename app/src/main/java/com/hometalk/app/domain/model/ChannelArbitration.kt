package com.hometalk.app.domain.model

/**
 * Deterministic coordinator election: lowest device ID among self + online paired peers.
 */
object ChannelArbitration {
    fun electCoordinator(
        localDeviceId: String,
        onlinePairedDeviceIds: Collection<String>,
    ): String {
        val candidates = buildSet {
            add(localDeviceId)
            addAll(onlinePairedDeviceIds.filter { it.isNotBlank() })
        }
        return candidates.minOrNull() ?: localDeviceId
    }
}
