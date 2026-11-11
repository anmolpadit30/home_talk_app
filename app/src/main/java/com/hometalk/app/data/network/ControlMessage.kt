package com.hometalk.app.data.network

import com.hometalk.app.domain.model.NetworkDefaults
import org.json.JSONObject

/**
 * Newline-delimited JSON control messages over TCP.
 */
sealed class ControlMessage {
    abstract val type: String

    data class Hello(
        val deviceId: String,
        val deviceName: String,
        val roomId: String,
        val roomName: String,
        val protocolVersion: Int = NetworkDefaults.PROTOCOL_VERSION.toInt(),
        val controlPort: Int = NetworkDefaults.TCP_CONTROL_PORT,
        val audioPort: Int = NetworkDefaults.UDP_AUDIO_PORT,
    ) : ControlMessage() {
        override val type: String = TYPE_HELLO
    }

    data class HelloAck(
        val deviceId: String,
        val deviceName: String,
        val roomId: String,
        val roomName: String,
        val protocolVersion: Int = NetworkDefaults.PROTOCOL_VERSION.toInt(),
        val paired: Boolean,
        val controlPort: Int = NetworkDefaults.TCP_CONTROL_PORT,
        val audioPort: Int = NetworkDefaults.UDP_AUDIO_PORT,
    ) : ControlMessage() {
        override val type: String = TYPE_HELLO_ACK
    }

    data class Heartbeat(
        val deviceId: String,
        val timestampEpochMs: Long,
    ) : ControlMessage() {
        override val type: String = TYPE_HEARTBEAT
    }

    data class PairRequest(
        val deviceId: String,
        val deviceName: String,
        val roomId: String,
        val roomName: String,
        val pin: String,
        val protocolVersion: Int = NetworkDefaults.PROTOCOL_VERSION.toInt(),
        val controlPort: Int = NetworkDefaults.TCP_CONTROL_PORT,
    ) : ControlMessage() {
        override val type: String = TYPE_PAIR_REQUEST
    }

    data class PairAccept(
        val deviceId: String,
        val deviceName: String,
        val roomId: String,
        val roomName: String,
        val protocolVersion: Int = NetworkDefaults.PROTOCOL_VERSION.toInt(),
        val controlPort: Int = NetworkDefaults.TCP_CONTROL_PORT,
    ) : ControlMessage() {
        override val type: String = TYPE_PAIR_ACCEPT
    }

    data class PairReject(
        val reason: String,
    ) : ControlMessage() {
        override val type: String = TYPE_PAIR_REJECT
    }

    data class TxRequest(
        val deviceId: String,
        val deviceName: String,
        val roomId: String,
        val transmissionId: String,
    ) : ControlMessage() {
        override val type: String = TYPE_TX_REQUEST
    }

    data class TxGrant(
        val deviceId: String,
        val deviceName: String,
        val roomId: String,
        val transmissionId: String,
        val leaseExpiresAtEpochMs: Long,
    ) : ControlMessage() {
        override val type: String = TYPE_TX_GRANT
    }

    data class TxDeny(
        val reason: String,
        val currentTransmitterId: String? = null,
        val currentTransmitterName: String? = null,
    ) : ControlMessage() {
        override val type: String = TYPE_TX_DENY
    }

    data class TxRelease(
        val deviceId: String,
        val transmissionId: String,
    ) : ControlMessage() {
        override val type: String = TYPE_TX_RELEASE
    }

    data class TxAnnounce(
        val deviceId: String,
        val deviceName: String,
        val roomId: String,
        val transmissionId: String,
        val leaseExpiresAtEpochMs: Long,
    ) : ControlMessage() {
        override val type: String = TYPE_TX_ANNOUNCE
    }

    data class TxClear(
        val transmissionId: String,
    ) : ControlMessage() {
        override val type: String = TYPE_TX_CLEAR
    }

    data class TxAck(
        val ok: Boolean = true,
    ) : ControlMessage() {
        override val type: String = TYPE_TX_ACK
    }

    companion object {
        const val TYPE_HELLO = "hello"
        const val TYPE_HELLO_ACK = "hello_ack"
        const val TYPE_HEARTBEAT = "heartbeat"
        const val TYPE_PAIR_REQUEST = "pair_request"
        const val TYPE_PAIR_ACCEPT = "pair_accept"
        const val TYPE_PAIR_REJECT = "pair_reject"
        const val TYPE_TX_REQUEST = "tx_request"
        const val TYPE_TX_GRANT = "tx_grant"
        const val TYPE_TX_DENY = "tx_deny"
        const val TYPE_TX_RELEASE = "tx_release"
        const val TYPE_TX_ANNOUNCE = "tx_announce"
        const val TYPE_TX_CLEAR = "tx_clear"
        const val TYPE_TX_ACK = "tx_ack"
    }
}

object ControlMessageCodec {
    fun encode(message: ControlMessage): String {
        val json = JSONObject().put("type", message.type)
        when (message) {
            is ControlMessage.Hello -> json
                .put("deviceId", message.deviceId)
                .put("deviceName", message.deviceName)
                .put("roomId", message.roomId)
                .put("roomName", message.roomName)
                .put("protocolVersion", message.protocolVersion)
                .put("controlPort", message.controlPort)
                .put("audioPort", message.audioPort)
            is ControlMessage.HelloAck -> json
                .put("deviceId", message.deviceId)
                .put("deviceName", message.deviceName)
                .put("roomId", message.roomId)
                .put("roomName", message.roomName)
                .put("protocolVersion", message.protocolVersion)
                .put("paired", message.paired)
                .put("controlPort", message.controlPort)
                .put("audioPort", message.audioPort)
            is ControlMessage.Heartbeat -> json
                .put("deviceId", message.deviceId)
                .put("timestampEpochMs", message.timestampEpochMs)
            is ControlMessage.PairRequest -> json
                .put("deviceId", message.deviceId)
                .put("deviceName", message.deviceName)
                .put("roomId", message.roomId)
                .put("roomName", message.roomName)
                .put("pin", message.pin)
                .put("protocolVersion", message.protocolVersion)
                .put("controlPort", message.controlPort)
            is ControlMessage.PairAccept -> json
                .put("deviceId", message.deviceId)
                .put("deviceName", message.deviceName)
                .put("roomId", message.roomId)
                .put("roomName", message.roomName)
                .put("protocolVersion", message.protocolVersion)
                .put("controlPort", message.controlPort)
            is ControlMessage.PairReject -> json.put("reason", message.reason)
            is ControlMessage.TxRequest -> json
                .put("deviceId", message.deviceId)
                .put("deviceName", message.deviceName)
                .put("roomId", message.roomId)
                .put("transmissionId", message.transmissionId)
            is ControlMessage.TxGrant -> json
                .put("deviceId", message.deviceId)
                .put("deviceName", message.deviceName)
                .put("roomId", message.roomId)
                .put("transmissionId", message.transmissionId)
                .put("leaseExpiresAtEpochMs", message.leaseExpiresAtEpochMs)
            is ControlMessage.TxDeny -> json
                .put("reason", message.reason)
                .put("currentTransmitterId", message.currentTransmitterId)
                .put("currentTransmitterName", message.currentTransmitterName)
            is ControlMessage.TxRelease -> json
                .put("deviceId", message.deviceId)
                .put("transmissionId", message.transmissionId)
            is ControlMessage.TxAnnounce -> json
                .put("deviceId", message.deviceId)
                .put("deviceName", message.deviceName)
                .put("roomId", message.roomId)
                .put("transmissionId", message.transmissionId)
                .put("leaseExpiresAtEpochMs", message.leaseExpiresAtEpochMs)
            is ControlMessage.TxClear -> json.put("transmissionId", message.transmissionId)
            is ControlMessage.TxAck -> json.put("ok", message.ok)
        }
        return json.toString()
    }

    fun decode(raw: String): ControlMessage {
        val json = JSONObject(raw)
        return when (val type = json.getString("type")) {
            ControlMessage.TYPE_HELLO -> ControlMessage.Hello(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                roomId = json.getString("roomId"),
                roomName = json.optString("roomName"),
                protocolVersion = json.optInt("protocolVersion", NetworkDefaults.PROTOCOL_VERSION.toInt()),
                controlPort = json.optInt("controlPort", NetworkDefaults.TCP_CONTROL_PORT),
                audioPort = json.optInt("audioPort", NetworkDefaults.UDP_AUDIO_PORT),
            )
            ControlMessage.TYPE_HELLO_ACK -> ControlMessage.HelloAck(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                roomId = json.getString("roomId"),
                roomName = json.optString("roomName"),
                protocolVersion = json.optInt("protocolVersion", NetworkDefaults.PROTOCOL_VERSION.toInt()),
                paired = json.optBoolean("paired", false),
                controlPort = json.optInt("controlPort", NetworkDefaults.TCP_CONTROL_PORT),
                audioPort = json.optInt("audioPort", NetworkDefaults.UDP_AUDIO_PORT),
            )
            ControlMessage.TYPE_HEARTBEAT -> ControlMessage.Heartbeat(
                deviceId = json.getString("deviceId"),
                timestampEpochMs = json.getLong("timestampEpochMs"),
            )
            ControlMessage.TYPE_PAIR_REQUEST -> ControlMessage.PairRequest(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                roomId = json.getString("roomId"),
                roomName = json.optString("roomName"),
                pin = json.getString("pin"),
                protocolVersion = json.optInt("protocolVersion", NetworkDefaults.PROTOCOL_VERSION.toInt()),
                controlPort = json.optInt("controlPort", NetworkDefaults.TCP_CONTROL_PORT),
            )
            ControlMessage.TYPE_PAIR_ACCEPT -> ControlMessage.PairAccept(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                roomId = json.getString("roomId"),
                roomName = json.optString("roomName"),
                protocolVersion = json.optInt("protocolVersion", NetworkDefaults.PROTOCOL_VERSION.toInt()),
                controlPort = json.optInt("controlPort", NetworkDefaults.TCP_CONTROL_PORT),
            )
            ControlMessage.TYPE_PAIR_REJECT -> ControlMessage.PairReject(
                reason = json.optString("reason", "rejected"),
            )
            ControlMessage.TYPE_TX_REQUEST -> ControlMessage.TxRequest(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                roomId = json.getString("roomId"),
                transmissionId = json.getString("transmissionId"),
            )
            ControlMessage.TYPE_TX_GRANT -> ControlMessage.TxGrant(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                roomId = json.getString("roomId"),
                transmissionId = json.getString("transmissionId"),
                leaseExpiresAtEpochMs = json.getLong("leaseExpiresAtEpochMs"),
            )
            ControlMessage.TYPE_TX_DENY -> ControlMessage.TxDeny(
                reason = json.optString("reason", "busy"),
                currentTransmitterId = json.optString("currentTransmitterId").takeIf { it.isNotBlank() },
                currentTransmitterName = json.optString("currentTransmitterName").takeIf { it.isNotBlank() },
            )
            ControlMessage.TYPE_TX_RELEASE -> ControlMessage.TxRelease(
                deviceId = json.getString("deviceId"),
                transmissionId = json.getString("transmissionId"),
            )
            ControlMessage.TYPE_TX_ANNOUNCE -> ControlMessage.TxAnnounce(
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                roomId = json.getString("roomId"),
                transmissionId = json.getString("transmissionId"),
                leaseExpiresAtEpochMs = json.getLong("leaseExpiresAtEpochMs"),
            )
            ControlMessage.TYPE_TX_CLEAR -> ControlMessage.TxClear(
                transmissionId = json.getString("transmissionId"),
            )
            ControlMessage.TYPE_TX_ACK -> ControlMessage.TxAck(
                ok = json.optBoolean("ok", true),
            )
            else -> throw IllegalArgumentException("Unknown control message type: $type")
        }
    }
}
