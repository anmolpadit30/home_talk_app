package com.hometalk.app.domain

import com.hometalk.app.data.preferences.PairedDevicesCodec
import com.hometalk.app.domain.model.PairedDeviceRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class PairedDevicesCodecTest {

    @Test
    fun roundTripsRecords() {
        val original = listOf(
            PairedDeviceRecord(
                id = "one",
                name = "Dad",
                hostAddress = "192.168.1.8",
                controlPort = 49870,
                pairedAtEpochMs = 100,
                lastSeenEpochMs = 200,
            ),
        )
        val decoded = PairedDevicesCodec.decode(PairedDevicesCodec.encode(original))
        assertEquals(1, decoded.size)
        assertEquals("one", decoded[0].id)
        assertEquals("Dad", decoded[0].name)
        assertEquals("192.168.1.8", decoded[0].hostAddress)
    }

    @Test
    fun emptyAndInvalidInput() {
        assertEquals(emptyList<PairedDeviceRecord>(), PairedDevicesCodec.decode(null))
        assertEquals(emptyList<PairedDeviceRecord>(), PairedDevicesCodec.decode("not-json"))
    }
}
