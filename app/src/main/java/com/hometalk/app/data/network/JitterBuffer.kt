package com.hometalk.app.data.network

import java.util.TreeMap

/**
 * Small sequence-ordered jitter buffer. Drops late/duplicate packets.
 */
class JitterBuffer(
    private val capacity: Int = 8,
) {
    private val packets = TreeMap<Int, PacketProtocol.AudioPacket>()
    private var nextSequence: Int? = null

    @Synchronized
    fun offer(packet: PacketProtocol.AudioPacket): Boolean {
        val next = nextSequence
        if (next != null && packet.sequence < next) {
            return false // late
        }
        if (packets.containsKey(packet.sequence)) {
            return false // duplicate
        }
        packets[packet.sequence] = packet
        while (packets.size > capacity) {
            packets.pollFirstEntry()
        }
        return true
    }

    @Synchronized
    fun poll(): PacketProtocol.AudioPacket? {
        if (packets.isEmpty()) return null
        val first = packets.firstEntry() ?: return null
        val next = nextSequence
        return if (next == null || first.key == next || packets.size >= capacity / 2) {
            packets.pollFirstEntry()
            nextSequence = first.key + 1
            first.value
        } else {
            null
        }
    }

    @Synchronized
    fun clear() {
        packets.clear()
        nextSequence = null
    }

    @Synchronized
    fun size(): Int = packets.size
}
