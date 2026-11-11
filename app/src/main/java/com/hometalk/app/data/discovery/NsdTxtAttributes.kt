package com.hometalk.app.data.discovery

import android.net.nsd.NsdServiceInfo
import com.hometalk.app.domain.model.NetworkDefaults

data class DiscoveredServiceInfo(
    val deviceId: String,
    val deviceName: String,
    val roomId: String,
    val roomName: String,
    val hostAddress: String,
    val controlPort: Int,
    val serviceName: String,
)

object NsdTxtAttributes {
    const val DEVICE_ID = "did"
    const val DEVICE_NAME = "name"
    const val ROOM_ID = "rid"
    const val ROOM_NAME = "rnm"
    const val PROTOCOL_VERSION = "ver"

    fun apply(
        serviceInfo: NsdServiceInfo,
        deviceId: String,
        deviceName: String,
        roomId: String,
        roomName: String,
    ) {
        serviceInfo.setAttribute(DEVICE_ID, deviceId.take(48))
        serviceInfo.setAttribute(DEVICE_NAME, deviceName.take(32))
        serviceInfo.setAttribute(ROOM_ID, roomId.take(48))
        serviceInfo.setAttribute(ROOM_NAME, roomName.take(24))
        serviceInfo.setAttribute(PROTOCOL_VERSION, NetworkDefaults.PROTOCOL_VERSION.toString())
    }

    fun parse(
        serviceInfo: NsdServiceInfo,
        hostAddress: String,
        controlPort: Int,
    ): DiscoveredServiceInfo? {
        val attrs = serviceInfo.attributes.orEmpty().mapKeys { it.key }
        fun attr(key: String): String? {
            val bytes = attrs[key] ?: return null
            return runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() }
        }
        val deviceId = attr(DEVICE_ID) ?: return null
        return DiscoveredServiceInfo(
            deviceId = deviceId,
            deviceName = attr(DEVICE_NAME) ?: serviceInfo.serviceName,
            roomId = attr(ROOM_ID).orEmpty(),
            roomName = attr(ROOM_NAME).orEmpty(),
            hostAddress = hostAddress,
            controlPort = if (controlPort > 0) controlPort else NetworkDefaults.TCP_CONTROL_PORT,
            serviceName = serviceInfo.serviceName,
        )
    }
}
