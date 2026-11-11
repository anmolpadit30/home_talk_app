package com.hometalk.app.domain

import com.hometalk.app.data.network.PairingQrCodec
import com.hometalk.app.domain.model.PairingQrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingQrCodecTest {

    @Test
    fun roundTripsPayload() {
        val original = PairingQrPayload(
            protocolVersion = 1,
            deviceId = "abc-123",
            deviceName = "Mom Phone",
            roomId = "room-9",
            roomName = "Family",
            hostAddress = "192.168.1.20",
            controlPort = 49870,
            pin = "654321",
        )
        val encoded = PairingQrCodec.encode(original)
        val decoded = PairingQrCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(original.deviceId, decoded!!.deviceId)
        assertEquals(original.hostAddress, decoded.hostAddress)
        assertEquals(original.pin, decoded.pin)
        assertEquals(original.controlPort, decoded.controlPort)
    }

    @Test
    fun rejectsUnknownScheme() {
        assertNull(PairingQrCodec.decode("https://example.com"))
    }
}
