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
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

fun interface AudioPacketListener {
    fun onAudioPacket(packet: PacketProtocol.AudioPacket)
}

@Singleton
class UdpAudioReceiver @Inject constructor() {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: DatagramSocket? = null
    private var job: Job? = null
    private var listener: AudioPacketListener? = null

    fun setListener(listener: AudioPacketListener?) {
        this.listener = listener
    }

    fun start() {
        synchronized(lock) {
            if (job?.isActive == true && socket?.isClosed == false) return
            startLocked()
        }
    }

    fun restart() {
        synchronized(lock) {
            HomeTalkLog.i(LogArea.NETWORK, "UDP audio receiver restart")
            stopLocked()
            startLocked()
        }
    }

    fun stop() {
        synchronized(lock) {
            stopLocked()
            HomeTalkLog.i(LogArea.NETWORK, "UDP audio receiver stopped")
        }
    }

    /** True when a live socket is accepting datagrams. */
    fun isListening(): Boolean = synchronized(lock) {
        job?.isActive == true && socket?.isClosed == false
    }

    private fun stopLocked() {
        val oldSocket = socket
        socket = null
        job?.cancel()
        job = null
        try {
            oldSocket?.close()
        } catch (_: Exception) {
        }
    }

    private fun startLocked() {
        val local = try {
            DatagramSocket(NetworkDefaults.UDP_AUDIO_PORT).apply {
                reuseAddress = true
                // Wake the receive loop so OEM freezes / cancelled jobs exit promptly.
                soTimeout = 5_000
            }
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.NETWORK, "UDP receiver failed to bind", error)
            return
        }
        socket = local
        job = scope.launch {
            HomeTalkLog.i(LogArea.NETWORK, "UDP audio listening on ${NetworkDefaults.UDP_AUDIO_PORT}")
            val buffer = ByteArray(PacketProtocol.HEADER_SIZE + PacketProtocol.MAX_PAYLOAD)
            try {
                while (isActive) {
                    // Bail if a newer start/restart replaced this socket.
                    if (socket !== local || local.isClosed) break
                    try {
                        val datagram = DatagramPacket(buffer, buffer.size)
                        local.receive(datagram)
                        val packet = PacketProtocol.decode(datagram.data, datagram.length) ?: continue
                        listener?.onAudioPacket(packet)
                    } catch (_: SocketTimeoutException) {
                        // Expected — keeps the loop alive under deep sleep.
                    } catch (error: Exception) {
                        if (!isActive || socket !== local || local.isClosed) break
                        HomeTalkLog.w(LogArea.NETWORK, "UDP receive error", error)
                    }
                }
            } finally {
                try {
                    local.close()
                } catch (_: Exception) {
                }
                synchronized(lock) {
                    // Never close/null a newer socket started by a concurrent restart.
                    if (socket === local) {
                        socket = null
                    }
                }
                HomeTalkLog.i(LogArea.NETWORK, "UDP receive loop exited")
            }
        }
    }
}
