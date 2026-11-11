package com.hometalk.app.data.network

import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.domain.model.PairingQrPayload
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object PairingQrCodec {
    private const val PREFIX = "hometalk://pair?"

    fun encode(payload: PairingQrPayload): String {
        val query = listOf(
            "v" to payload.protocolVersion.toString(),
            "did" to payload.deviceId,
            "name" to payload.deviceName,
            "rid" to payload.roomId,
            "rnm" to payload.roomName,
            "host" to payload.hostAddress,
            "port" to payload.controlPort.toString(),
            "pin" to payload.pin,
        ).joinToString("&") { (key, value) ->
            "$key=${urlEncode(value)}"
        }
        return PREFIX + query
    }

    fun decode(raw: String): PairingQrPayload? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(PREFIX) && !trimmed.startsWith("hometalk://pair")) return null
        val query = trimmed.substringAfter('?', missingDelimiterValue = "").ifBlank { return null }
        val values = query.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', missingDelimiterValue = "")
                val value = part.substringAfter('=', missingDelimiterValue = "")
                if (key.isBlank()) null else key to urlDecode(value)
            }
            .toMap()
        val host = values["host"] ?: return null
        val pin = values["pin"] ?: return null
        val deviceId = values["did"] ?: return null
        return PairingQrPayload(
            protocolVersion = values["v"]?.toIntOrNull() ?: NetworkDefaults.PROTOCOL_VERSION.toInt(),
            deviceId = deviceId,
            deviceName = values["name"].orEmpty(),
            roomId = values["rid"].orEmpty(),
            roomName = values["rnm"].orEmpty(),
            hostAddress = host,
            controlPort = values["port"]?.toIntOrNull() ?: NetworkDefaults.TCP_CONTROL_PORT,
            pin = pin,
        )
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun urlDecode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)
}
