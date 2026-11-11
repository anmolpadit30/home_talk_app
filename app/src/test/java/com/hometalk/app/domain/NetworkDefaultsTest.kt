package com.hometalk.app.domain

import com.hometalk.app.data.network.PacketProtocol
import com.hometalk.app.domain.model.NetworkDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteOrder

class NetworkDefaultsTest {

    @Test
    fun controlAndAudioPortsAreInUserRange() {
        assertTrue(NetworkDefaults.TCP_CONTROL_PORT in 1024..65535)
        assertTrue(NetworkDefaults.UDP_AUDIO_PORT in 1024..65535)
        assertTrue(NetworkDefaults.TCP_CONTROL_PORT != NetworkDefaults.UDP_AUDIO_PORT)
    }

    @Test
    fun audioFrameMatchesRecommendedMvpConfig() {
        assertEquals(16_000, NetworkDefaults.SAMPLE_RATE_HZ)
        assertEquals(1, NetworkDefaults.CHANNELS)
        assertEquals(20, NetworkDefaults.FRAME_DURATION_MS)
    }

    @Test
    fun packetProtocolUsesNetworkByteOrderAndSharedVersion() {
        assertEquals(ByteOrder.BIG_ENDIAN, PacketProtocol.BYTE_ORDER)
        assertEquals(NetworkDefaults.PROTOCOL_VERSION, PacketProtocol.VERSION)
    }

    @Test
    fun wifiSleepIsTenMinutes() {
        assertEquals(10 * 60_000L, NetworkDefaults.WIFI_SLEEP_AFTER_MS)
    }
}
