package com.hometalk.app.data.network

import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TcpControlClient @Inject constructor() {
    suspend fun exchange(
        host: String,
        port: Int,
        message: ControlMessage,
    ): Result<ControlMessage> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = NetworkDefaults.TCP_READ_TIMEOUT_MS
                socket.connect(
                    InetSocketAddress(host, port),
                    NetworkDefaults.TCP_CONNECT_TIMEOUT_MS,
                )
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                writer.write(ControlMessageCodec.encode(message))
                writer.newLine()
                writer.flush()
                val line = reader.readLine()
                    ?: throw IllegalStateException("Empty response from $host:$port")
                ControlMessageCodec.decode(line)
            }
        }.onFailure { error ->
            HomeTalkLog.w(LogArea.NETWORK, "TCP exchange failed host=$host port=$port", error)
        }
    }
}
