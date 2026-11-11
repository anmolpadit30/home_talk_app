package com.hometalk.app.data.discovery

/**
 * NSD / mDNS advertisement and discovery. Implemented in Phase 2.
 */
interface DeviceDiscovery {
    fun start()
    fun stop()
}
