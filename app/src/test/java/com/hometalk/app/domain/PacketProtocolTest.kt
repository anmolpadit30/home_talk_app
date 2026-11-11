package com.hometalk.app.domain

import com.hometalk.app.data.network.PacketProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class PacketProtocolTest {

    @Test
    fun roundTripsAudioPacket() {
        val original = PacketProtocol.AudioPacket(
            version = PacketProtocol.VERSION,
            roomId = UUID.randomUUID().toString(),
            senderDeviceId = UUID.randomUUID().toString(),
            transmissionId = UUID.randomUUID().toString(),
            sequence = 42,
            timestampEpochMs = 1_700_000_000_000L,
            payload = byteArrayOf(1, 2, 3, 4, 5),
        )
        val encoded = PacketProtocol.encode(original)
        val decoded = PacketProtocol.decode(encoded)
        assertNotNull(decoded)
        assertEquals(original.sequence, decoded!!.sequence)
        assertEquals(original.roomId, decoded.roomId)
        assertEquals(original.senderDeviceId, decoded.senderDeviceId)
        assertEquals(original.transmissionId, decoded.transmissionId)
        assertEquals(original.timestampEpochMs, decoded.timestampEpochMs)
        assertEquals(original.payload.toList(), decoded.payload.toList())
    }

    @Test
    fun rejectsTruncatedPacket() {
        assertNull(PacketProtocol.decode(ByteArray(10)))
    }
}
