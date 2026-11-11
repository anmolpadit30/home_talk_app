package com.hometalk.app.domain

import com.hometalk.app.data.network.ControlMessage
import com.hometalk.app.data.network.ControlMessageCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlMessageCodecTest {

    @Test
    fun roundTripsPairRequest() {
        val original = ControlMessage.PairRequest(
            deviceId = "device-a",
            deviceName = "Kitchen",
            roomId = "room-1",
            roomName = "Family",
            pin = "123456",
        )
        val decoded = ControlMessageCodec.decode(ControlMessageCodec.encode(original))
        assertTrue(decoded is ControlMessage.PairRequest)
        decoded as ControlMessage.PairRequest
        assertEquals(original.deviceId, decoded.deviceId)
        assertEquals(original.pin, decoded.pin)
        assertEquals(original.roomId, decoded.roomId)
    }

    @Test
    fun roundTripsHelloAck() {
        val original = ControlMessage.HelloAck(
            deviceId = "device-b",
            deviceName = "Living room",
            roomId = "room-1",
            roomName = "Family",
            paired = true,
        )
        val decoded = ControlMessageCodec.decode(ControlMessageCodec.encode(original)) as ControlMessage.HelloAck
        assertEquals(true, decoded.paired)
        assertEquals("Living room", decoded.deviceName)
    }
}
