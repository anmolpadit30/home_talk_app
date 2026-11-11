package com.hometalk.app.data.network

import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.util.UuidBytes
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary UDP audio packet (network byte order).
 *
 * Header (64 bytes):
 * - version (1)
 * - flags (1)
 * - room ID (16)
 * - sender device ID (16)
 * - transmission ID (16)
 * - sequence (4)
 * - timestamp ms (8)
 * - payload length (2)
 * Payload: Opus frame bytes
 */
object PacketProtocol {
    val BYTE_ORDER: ByteOrder = ByteOrder.BIG_ENDIAN
    const val VERSION: Byte = NetworkDefaults.PROTOCOL_VERSION
    const val HEADER_SIZE = 64
    const val MAX_PAYLOAD = 1_280

    data class AudioPacket(
        val version: Byte,
        val roomId: String,
        val senderDeviceId: String,
        val transmissionId: String,
        val sequence: Int,
        val timestampEpochMs: Long,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioPacket) return false
            return version == other.version &&
                roomId == other.roomId &&
                senderDeviceId == other.senderDeviceId &&
                transmissionId == other.transmissionId &&
                sequence == other.sequence &&
                timestampEpochMs == other.timestampEpochMs &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = version.toInt()
            result = 31 * result + roomId.hashCode()
            result = 31 * result + senderDeviceId.hashCode()
            result = 31 * result + transmissionId.hashCode()
            result = 31 * result + sequence
            result = 31 * result + timestampEpochMs.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    fun encode(packet: AudioPacket): ByteArray {
        require(packet.payload.size <= MAX_PAYLOAD) { "Payload too large" }
        val buffer = ByteBuffer.allocate(HEADER_SIZE + packet.payload.size).order(BYTE_ORDER)
        buffer.put(packet.version)
        buffer.put(0) // flags
        buffer.put(UuidBytes.toBytes(packet.roomId))
        buffer.put(UuidBytes.toBytes(packet.senderDeviceId))
        buffer.put(UuidBytes.toBytes(packet.transmissionId))
        buffer.putInt(packet.sequence)
        buffer.putLong(packet.timestampEpochMs)
        buffer.putShort(packet.payload.size.toShort())
        // pad to HEADER_SIZE
        while (buffer.position() < HEADER_SIZE) {
            buffer.put(0)
        }
        buffer.put(packet.payload)
        return buffer.array()
    }

    fun decode(bytes: ByteArray, length: Int = bytes.size): AudioPacket? {
        if (length < HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes, 0, length).order(BYTE_ORDER)
        val version = buffer.get()
        if (version != VERSION) return null
        buffer.get() // flags
        val roomId = UuidBytes.fromBytes(bytes, buffer.position()).also { buffer.position(buffer.position() + UuidBytes.SIZE) }
        val senderId = UuidBytes.fromBytes(bytes, buffer.position()).also { buffer.position(buffer.position() + UuidBytes.SIZE) }
        val transmissionId = UuidBytes.fromBytes(bytes, buffer.position()).also { buffer.position(buffer.position() + UuidBytes.SIZE) }
        val sequence = buffer.int
        val timestamp = buffer.long
        val payloadLength = buffer.short.toInt() and 0xFFFF
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD) return null
        // Skip padding to HEADER_SIZE
        buffer.position(HEADER_SIZE)
        if (HEADER_SIZE + payloadLength > length) return null
        val payload = ByteArray(payloadLength)
        buffer.get(payload)
        return AudioPacket(
            version = version,
            roomId = roomId,
            senderDeviceId = senderId,
            transmissionId = transmissionId,
            sequence = sequence,
            timestampEpochMs = timestamp,
            payload = payload,
        )
    }
}
