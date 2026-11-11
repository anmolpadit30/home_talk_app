package com.hometalk.app.data.network

import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

fun interface AudioPacketListener {
    fun onAudioPacket(packet: PacketProtocol.AudioPacket)
}

@Singleton
class UdpAudioReceiver @Inject constructor() {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var job: Job? = null
    private var listener: AudioPacketListener? = null

    fun setListener(listener: AudioPacketListener?) {
        this.listener = listener
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        job = scope.launch {
            try {
                val local = DatagramSocket(NetworkDefaults.UDP_AUDIO_PORT)
                socket = local
                HomeTalkLog.i(LogArea.NETWORK, "UDP audio listening on ${NetworkDefaults.UDP_AUDIO_PORT}")
                val buffer = ByteArray(PacketProtocol.HEADER_SIZE + PacketProtocol.MAX_PAYLOAD)
                while (isActive && running.get()) {
                    try {
                        val datagram = DatagramPacket(buffer, buffer.size)
                        local.receive(datagram)
                        val packet = PacketProtocol.decode(datagram.data, datagram.length) ?: continue
                        listener?.onAudioPacket(packet)
                    } catch (_: SocketException) {
                        break
                    } catch (error: Exception) {
                        HomeTalkLog.w(LogArea.NETWORK, "UDP receive error", error)
                    }
                }
            } catch (error: Exception) {
                HomeTalkLog.e(LogArea.NETWORK, "UDP receiver failed to bind", error)
                running.set(false)
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        job?.cancel()
        job = null
        HomeTalkLog.i(LogArea.NETWORK, "UDP audio receiver stopped")
    }
}
