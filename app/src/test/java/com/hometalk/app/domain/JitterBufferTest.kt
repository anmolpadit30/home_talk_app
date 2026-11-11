package com.hometalk.app.domain

import com.hometalk.app.data.network.JitterBuffer
import com.hometalk.app.data.network.PacketProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class JitterBufferTest {

    @Test
    fun dropsDuplicatesAndOrdersPackets() {
        val buffer = JitterBuffer(capacity = 8)
        buffer.offer(packet(2))
        buffer.offer(packet(1))
        buffer.offer(packet(1))
        assertEquals(1, buffer.poll()!!.sequence)
        assertEquals(2, buffer.poll()!!.sequence)
        assertNull(buffer.poll())
    }

    private fun packet(sequence: Int) = PacketProtocol.AudioPacket(
        version = PacketProtocol.VERSION,
        roomId = UUID.randomUUID().toString(),
        senderDeviceId = UUID.randomUUID().toString(),
        transmissionId = UUID.randomUUID().toString(),
        sequence = sequence,
        timestampEpochMs = sequence.toLong(),
        payload = byteArrayOf(sequence.toByte()),
    )
}
