package com.hometalk.app.data.network

import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UdpAudioSender @Inject constructor() {
    private var socket: DatagramSocket? = null

    @Synchronized
    fun ensureSocket() {
        if (socket == null || socket?.isClosed == true) {
            socket = DatagramSocket().apply { soTimeout = 0 }
        }
    }

    @Synchronized
    fun close() {
        socket?.close()
        socket = null
    }

    suspend fun send(host: String, port: Int, packetBytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            ensureSocket()
            val address = InetAddress.getByName(host)
            val datagram = DatagramPacket(packetBytes, packetBytes.size, address, port)
            socket?.send(datagram)
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.NETWORK, "UDP send failed host=$host port=$port", error)
        }
    }
}
