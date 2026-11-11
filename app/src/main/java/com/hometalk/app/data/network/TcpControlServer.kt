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
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

fun interface ControlMessageHandler {
    suspend fun handle(
        remoteHost: String,
        message: ControlMessage,
    ): ControlMessage?
}

@Singleton
class TcpControlServer @Inject constructor() {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var scope: CoroutineScope? = null

    fun start(handler: ControlMessageHandler) {
        if (!running.compareAndSet(false, true)) return
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(NetworkDefaults.TCP_CONTROL_PORT))
        serverSocket = server
        val localScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = localScope
        HomeTalkLog.i(LogArea.NETWORK, "TCP control server listening on ${NetworkDefaults.TCP_CONTROL_PORT}")
        acceptJob = localScope.launch {
            while (isActive && running.get()) {
                try {
                    val client = server.accept()
                    launch { handleClient(client, handler) }
                } catch (_: SocketException) {
                    break
                } catch (error: Exception) {
                    HomeTalkLog.w(LogArea.NETWORK, "TCP accept failed", error)
                }
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        scope = null
        HomeTalkLog.i(LogArea.NETWORK, "TCP control server stopped")
    }

    private suspend fun handleClient(socket: Socket, handler: ControlMessageHandler) {
        socket.use { client ->
            client.soTimeout = NetworkDefaults.TCP_READ_TIMEOUT_MS
            client.tcpNoDelay = true
            val remoteHost = client.inetAddress?.hostAddress.orEmpty()
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8))
            val line = reader.readLine() ?: return
            val request = try {
                ControlMessageCodec.decode(line)
            } catch (error: Exception) {
                HomeTalkLog.w(LogArea.NETWORK, "Invalid control message from $remoteHost", error)
                val reject = ControlMessage.PairReject(reason = "invalid_message")
                writer.write(ControlMessageCodec.encode(reject))
                writer.newLine()
                writer.flush()
                return
            }
            val response = handler.handle(remoteHost, request)
            if (response != null) {
                writer.write(ControlMessageCodec.encode(response))
                writer.newLine()
                writer.flush()
            }
        }
    }
}
