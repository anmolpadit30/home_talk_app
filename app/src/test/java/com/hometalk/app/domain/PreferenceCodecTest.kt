package com.hometalk.app.domain

import com.hometalk.app.data.preferences.PreferenceCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceCodecTest {

    @Test
    fun roundTripsPeerAddresses() {
        val original = listOf("192.168.1.20", "10.0.0.8")
        val encoded = PreferenceCodec.encodeAddresses(original)
        assertEquals(original, PreferenceCodec.decodeAddresses(encoded))
    }

    @Test
    fun ignoresBlankEntries() {
        assertEquals(
            listOf("192.168.0.5"),
            PreferenceCodec.decodeAddresses("  \n192.168.0.5\n"),
        )
    }

    @Test
    fun emptyInputYieldsEmptyList() {
        assertEquals(emptyList<String>(), PreferenceCodec.decodeAddresses(null))
        assertEquals(emptyList<String>(), PreferenceCodec.decodeAddresses(""))
    }
}
