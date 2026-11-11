package com.hometalk.app.domain.repository

import com.hometalk.app.domain.model.PairingSession
import kotlinx.coroutines.flow.StateFlow

interface DiscoveryController {
    val pairingSession: StateFlow<PairingSession?>
    val statusMessage: StateFlow<String>
    fun start()
    fun stop()
    fun refresh()
    fun republishIdentity()
    suspend fun beginPairingSession(): PairingSession
    fun endPairingSession()
    suspend fun pairWithDiscoveredDevice(deviceId: String, pin: String): Result<Unit>
    suspend fun pairWithHost(hostAddress: String, controlPort: Int, pin: String): Result<Unit>
    suspend fun unpair(deviceId: String)
    suspend fun probeManualPeer(hostAddress: String)
}
