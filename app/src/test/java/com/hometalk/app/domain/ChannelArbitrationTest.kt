package com.hometalk.app.domain

import com.hometalk.app.domain.model.ChannelArbitration
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelArbitrationTest {

    @Test
    fun electsLowestDeviceId() {
        val coordinator = ChannelArbitration.electCoordinator(
            localDeviceId = "b-device",
            onlinePairedDeviceIds = listOf("c-device", "a-device"),
        )
        assertEquals("a-device", coordinator)
    }

    @Test
    fun electsLocalWhenAlone() {
        val coordinator = ChannelArbitration.electCoordinator(
            localDeviceId = "only-me",
            onlinePairedDeviceIds = emptyList(),
        )
        assertEquals("only-me", coordinator)
    }
}
