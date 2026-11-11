package com.hometalk.app.util

import java.nio.ByteBuffer
import java.util.UUID

object UuidBytes {
    const val SIZE = 16

    fun toBytes(uuidString: String): ByteArray {
        val uuid = runCatching { UUID.fromString(uuidString) }.getOrElse {
            UUID.nameUUIDFromBytes(uuidString.toByteArray(Charsets.UTF_8))
        }
        val buffer = ByteBuffer.allocate(SIZE)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray, offset: Int = 0): String {
        val buffer = ByteBuffer.wrap(bytes, offset, SIZE)
        return UUID(buffer.long, buffer.long).toString()
    }
}
