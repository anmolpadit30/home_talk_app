package com.hometalk.app.audio

import com.hometalk.app.data.network.ControlMessage
import com.hometalk.app.data.network.JitterBuffer
import com.hometalk.app.data.network.PacketProtocol
import com.hometalk.app.data.network.TcpControlClient
import com.hometalk.app.data.network.UdpAudioReceiver
import com.hometalk.app.data.network.UdpAudioSender
import com.hometalk.app.di.ApplicationScope
import com.hometalk.app.di.IoDispatcher
import com.hometalk.app.domain.model.ChannelArbitration
import com.hometalk.app.domain.model.ChannelState
import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.domain.model.PhaseConfig
import com.hometalk.app.domain.repository.DeviceRegistryRepository
import com.hometalk.app.domain.repository.PairedDevicesRepository
import com.hometalk.app.domain.repository.SettingsRepository
import com.hometalk.app.domain.repository.TalkController
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TalkSessionManager @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val settingsRepository: SettingsRepository,
    private val deviceRegistryRepository: DeviceRegistryRepository,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val tcpControlClient: TcpControlClient,
    private val udpAudioSender: UdpAudioSender,
    private val udpAudioReceiver: UdpAudioReceiver,
    private val audioEngine: AudioEngineImpl,
) : TalkController {

    private val talkMutex = Mutex()
    private val jitterBuffer = JitterBuffer()
    private val lastRemoteAudioAtMs = AtomicLong(0L)

    private val _channelState = MutableStateFlow(ChannelState())
    override val channelState: StateFlow<ChannelState> = _channelState.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    override val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private var active = false
    private var localTransmissionId: String? = null
    private var playbackJob: Job? = null
    private var leaseWatchJob: Job? = null

    override fun start() {
        if (!PhaseConfig.AUDIO_ENABLED) return
        if (active) return
        active = true
        udpAudioSender.ensureSocket()
        udpAudioReceiver.setListener { packet -> onIncomingAudioPacket(packet) }
        udpAudioReceiver.start()
        audioEngine.setEncodedFrameListener { encoded, _, sequence, timestamp ->
            appScope.launch(ioDispatcher) {
                sendEncodedFrame(encoded, sequence, timestamp)
            }
        }
        appScope.launch(ioDispatcher) {
            audioEngine.startPlayback()
            val settings = settingsRepository.settings.first()
            audioEngine.setPlaybackVolume(settings.speakerVolume)
        }
        playbackJob = appScope.launch(ioDispatcher) { playbackLoop() }
        leaseWatchJob = appScope.launch(ioDispatcher) { leaseWatchLoop() }
        HomeTalkLog.i(LogArea.AUDIO, "TalkSessionManager started")
    }

    override fun stop() {
        if (!active) return
        active = false
        appScope.launch(ioDispatcher) {
            releaseTalk()
            audioEngine.stopPlayback()
            audioEngine.stopCapture()
            udpAudioReceiver.stop()
            udpAudioSender.close()
            jitterBuffer.clear()
            _channelState.value = ChannelState()
            _isTransmitting.value = false
        }
        playbackJob?.cancel()
        playbackJob = null
        leaseWatchJob?.cancel()
        leaseWatchJob = null
        HomeTalkLog.i(LogArea.AUDIO, "TalkSessionManager stopped")
    }

    override suspend fun requestTalk(): Boolean = talkMutex.withLock {
        if (!PhaseConfig.AUDIO_ENABLED) return false
        if (_isTransmitting.value) return true
        val settings = settingsRepository.settings.first()
        val onlinePaired = deviceRegistryRepository.devices.first()
            .filter { it.isOnline && it.isPaired && it.id != settings.deviceId }
        if (onlinePaired.isEmpty()) {
            HomeTalkLog.w(LogArea.AUDIO, "No online paired peers for talk")
            return false
        }

        clearStaleBusyIfNeeded(settings.deviceId)

        val channel = _channelState.value
        if (channel.isBusy &&
            channel.currentTransmitterId != null &&
            channel.currentTransmitterId != settings.deviceId
        ) {
            HomeTalkLog.i(
                LogArea.AUDIO,
                "Channel busy held by ${channel.currentTransmitterName}",
            )
            return false
        }

        val transmissionId = UUID.randomUUID().toString()
        val coordinatorId = ChannelArbitration.electCoordinator(
            localDeviceId = settings.deviceId,
            onlinePairedDeviceIds = onlinePaired.map { it.id },
        )
        HomeTalkLog.i(LogArea.AUDIO, "PTT request coordinator=$coordinatorId tx=$transmissionId")

        // Mark local TX immediately so UI never flashes CHANNEL BUSY for ourselves.
        localTransmissionId = transmissionId
        _isTransmitting.value = true

        val granted = if (coordinatorId == settings.deviceId) {
            grantLocally(
                deviceId = settings.deviceId,
                deviceName = settings.deviceName,
                roomId = settings.roomId,
                transmissionId = transmissionId,
                announce = true,
            )
        } else {
            val coordinator = onlinePaired.firstOrNull { it.id == coordinatorId }
            val host = coordinator?.hostAddress
            val port = coordinator?.controlPort ?: NetworkDefaults.TCP_CONTROL_PORT
            if (host.isNullOrBlank()) {
                HomeTalkLog.w(LogArea.AUDIO, "Coordinator has no host address")
                abortLocalGrant()
                return false
            }
            val response = tcpControlClient.exchange(
                host = host,
                port = port,
                message = ControlMessage.TxRequest(
                    deviceId = settings.deviceId,
                    deviceName = settings.deviceName,
                    roomId = settings.roomId,
                    transmissionId = transmissionId,
                ),
            ).getOrElse {
                HomeTalkLog.w(LogArea.AUDIO, "TxRequest failed", it)
                abortLocalGrant()
                return false
            }
            when (response) {
                is ControlMessage.TxGrant -> {
                    applyBusy(
                        deviceId = response.deviceId,
                        deviceName = response.deviceName,
                        transmissionId = response.transmissionId,
                        leaseExpiresAtEpochMs = response.leaseExpiresAtEpochMs,
                    )
                    true
                }
                is ControlMessage.TxDeny -> {
                    if (response.currentTransmitterId != null) {
                        applyBusy(
                            deviceId = response.currentTransmitterId,
                            deviceName = response.currentTransmitterName
                                ?: response.currentTransmitterId,
                            transmissionId = "",
                            leaseExpiresAtEpochMs = System.currentTimeMillis() +
                                NetworkDefaults.REMOTE_AUDIO_IDLE_CLEAR_MS * 4,
                        )
                    }
                    false
                }
                else -> false
            }
        }

        if (!granted) {
            abortLocalGrant()
            return false
        }

        jitterBuffer.clear()
        audioEngine.startCapture()
        HomeTalkLog.i(LogArea.AUDIO, "Transmission started tx=$transmissionId")
        return true
    }

    override suspend fun releaseTalk() = talkMutex.withLock {
        val settings = settingsRepository.settings.first()
        val transmissionId = localTransmissionId
        val wasTransmitting = _isTransmitting.value
        if (!wasTransmitting && transmissionId == null) {
            if (_channelState.value.currentTransmitterId == settings.deviceId) {
                clearChannel(_channelState.value.transmissionId, announce = true)
            }
            return
        }
        audioEngine.stopCapture()
        _isTransmitting.value = false
        localTransmissionId = null
        if (transmissionId != null) {
            // Clear local state first so late packets / announces cannot stick us busy.
            clearChannel(transmissionId, announce = false)
            appScope.launch(ioDispatcher) {
                notifyRelease(settings.deviceId, transmissionId)
                broadcast(ControlMessage.TxClear(transmissionId = transmissionId))
            }
        }
        HomeTalkLog.i(LogArea.AUDIO, "Transmission released tx=$transmissionId")
    }

    override suspend fun handleControlMessage(
        remoteHost: String,
        message: ControlMessage,
    ): ControlMessage? {
        return when (message) {
            is ControlMessage.TxRequest -> handleTxRequest(message)
            is ControlMessage.TxRelease -> {
                if (_isTransmitting.value) {
                    // Ignore release for someone else while we hold the mic.
                    return ControlMessage.TxAck()
                }
                if (_channelState.value.transmissionId == message.transmissionId ||
                    _channelState.value.currentTransmitterId == message.deviceId ||
                    message.transmissionId.isBlank()
                ) {
                    clearChannel(message.transmissionId, announce = false)
                }
                ControlMessage.TxAck()
            }
            is ControlMessage.TxAnnounce -> {
                if (_isTransmitting.value &&
                    message.deviceId != settingsRepository.settings.first().deviceId
                ) {
                    // Another phone announced while we think we own the channel; keep ours.
                    return ControlMessage.TxAck()
                }
                applyBusy(
                    deviceId = message.deviceId,
                    deviceName = message.deviceName,
                    transmissionId = message.transmissionId,
                    leaseExpiresAtEpochMs = message.leaseExpiresAtEpochMs,
                )
                ControlMessage.TxAck()
            }
            is ControlMessage.TxClear -> {
                if (_isTransmitting.value) return ControlMessage.TxAck()
                if (_channelState.value.transmissionId == message.transmissionId ||
                    message.transmissionId.isBlank() ||
                    _channelState.value.transmissionId.isNullOrBlank()
                ) {
                    clearChannel(message.transmissionId, announce = false)
                }
                ControlMessage.TxAck()
            }
            else -> null
        }
    }

    private suspend fun handleTxRequest(request: ControlMessage.TxRequest): ControlMessage {
        val settings = settingsRepository.settings.first()
        val onlinePaired = deviceRegistryRepository.devices.first()
            .filter { it.isOnline && it.isPaired }
            .map { it.id }
        val coordinatorId = ChannelArbitration.electCoordinator(settings.deviceId, onlinePaired)
        if (coordinatorId != settings.deviceId) {
            return ControlMessage.TxDeny(reason = "not_coordinator")
        }
        if (!pairedDevicesRepository.isPaired(request.deviceId)) {
            return ControlMessage.TxDeny(reason = "not_paired")
        }
        clearStaleBusyIfNeeded(settings.deviceId)
        val current = _channelState.value
        if (current.isBusy &&
            current.currentTransmitterId != null &&
            current.currentTransmitterId != request.deviceId
        ) {
            return ControlMessage.TxDeny(
                reason = "busy",
                currentTransmitterId = current.currentTransmitterId,
                currentTransmitterName = current.currentTransmitterName,
            )
        }
        val lease = System.currentTimeMillis() + NetworkDefaults.TRANSMISSION_LEASE_MS
        grantLocally(
            deviceId = request.deviceId,
            deviceName = request.deviceName,
            roomId = request.roomId,
            transmissionId = request.transmissionId,
            announce = true,
            leaseExpiresAtEpochMs = lease,
        )
        return ControlMessage.TxGrant(
            deviceId = request.deviceId,
            deviceName = request.deviceName,
            roomId = request.roomId,
            transmissionId = request.transmissionId,
            leaseExpiresAtEpochMs = lease,
        )
    }

    private suspend fun grantLocally(
        deviceId: String,
        deviceName: String,
        roomId: String,
        transmissionId: String,
        announce: Boolean,
        leaseExpiresAtEpochMs: Long = System.currentTimeMillis() + NetworkDefaults.TRANSMISSION_LEASE_MS,
    ): Boolean {
        val current = _channelState.value
        if (current.isBusy &&
            current.currentTransmitterId != null &&
            current.currentTransmitterId != deviceId
        ) {
            return false
        }
        applyBusy(deviceId, deviceName, transmissionId, leaseExpiresAtEpochMs)
        if (announce) {
            // Do not block PTT on peer fan-out.
            appScope.launch(ioDispatcher) {
                broadcast(
                    ControlMessage.TxAnnounce(
                        deviceId = deviceId,
                        deviceName = deviceName,
                        roomId = roomId,
                        transmissionId = transmissionId,
                        leaseExpiresAtEpochMs = leaseExpiresAtEpochMs,
                    ),
                )
            }
        }
        return true
    }

    private fun applyBusy(
        deviceId: String,
        deviceName: String,
        transmissionId: String,
        leaseExpiresAtEpochMs: Long,
    ) {
        _channelState.value = ChannelState(
            isBusy = true,
            currentTransmitterId = deviceId,
            currentTransmitterName = deviceName,
            transmissionId = transmissionId.ifBlank { null },
            leaseExpiresAtEpochMs = leaseExpiresAtEpochMs,
        )
        HomeTalkLog.i(LogArea.AUDIO, "Channel busy: $deviceName tx=$transmissionId")
    }

    private fun clearChannel(transmissionId: String?, announce: Boolean) {
        val current = _channelState.value
        if (current.transmissionId != null &&
            !transmissionId.isNullOrBlank() &&
            current.transmissionId != transmissionId
        ) {
            return
        }
        _channelState.value = ChannelState()
        jitterBuffer.clear()
        if (announce) {
            appScope.launch(ioDispatcher) {
                broadcast(ControlMessage.TxClear(transmissionId = transmissionId.orEmpty()))
            }
        }
    }

    private fun abortLocalGrant() {
        _isTransmitting.value = false
        localTransmissionId = null
        // Keep remote busy state if present; only clear if we were the holder.
        val state = _channelState.value
        // settings lookup avoided here; clear only when transmission id matches local attempt
        if (state.transmissionId == null || state.currentTransmitterName == null) {
            // nothing
        }
    }

    private fun clearStaleBusyIfNeeded(localDeviceId: String) {
        val state = _channelState.value
        if (!state.isBusy) return
        if (state.currentTransmitterId == localDeviceId) return
        val now = System.currentTimeMillis()
        val leaseExpired = state.leaseExpiresAtEpochMs?.let { now > it } == true
        val lastAudio = lastRemoteAudioAtMs.get()
        val audioIdle = lastAudio == 0L ||
            now - lastAudio > NetworkDefaults.REMOTE_AUDIO_IDLE_CLEAR_MS
        if (leaseExpired || audioIdle) {
            HomeTalkLog.i(
                LogArea.AUDIO,
                "Clearing stale busy holder=${state.currentTransmitterName} leaseExpired=$leaseExpired audioIdle=$audioIdle",
            )
            _channelState.value = ChannelState()
            jitterBuffer.clear()
        }
    }

    private suspend fun notifyRelease(deviceId: String, transmissionId: String) {
        val settings = settingsRepository.settings.first()
        val onlinePaired = deviceRegistryRepository.devices.first()
            .filter { it.isOnline && it.isPaired && it.id != settings.deviceId }
        val coordinatorId = ChannelArbitration.electCoordinator(
            settings.deviceId,
            onlinePaired.map { it.id },
        )
        val message = ControlMessage.TxRelease(deviceId = deviceId, transmissionId = transmissionId)
        if (coordinatorId != settings.deviceId) {
            val coordinator = onlinePaired.firstOrNull { it.id == coordinatorId }
            val host = coordinator?.hostAddress
            if (!host.isNullOrBlank()) {
                tcpControlClient.exchange(
                    host,
                    coordinator.controlPort ?: NetworkDefaults.TCP_CONTROL_PORT,
                    message,
                )
            }
        }
        broadcast(message)
    }

    private suspend fun broadcast(message: ControlMessage) {
        val settings = settingsRepository.settings.first()
        deviceRegistryRepository.devices.first()
            .filter { it.isOnline && it.isPaired && it.id != settings.deviceId }
            .forEach { peer ->
                val host = peer.hostAddress ?: return@forEach
                tcpControlClient.exchange(
                    host = host,
                    port = peer.controlPort ?: NetworkDefaults.TCP_CONTROL_PORT,
                    message = message,
                )
            }
    }

    private suspend fun sendEncodedFrame(encoded: ByteArray, sequence: Int, timestampEpochMs: Long) {
        if (!_isTransmitting.value) return
        val settings = settingsRepository.settings.first()
        val transmissionId = localTransmissionId ?: return
        val packet = PacketProtocol.AudioPacket(
            version = PacketProtocol.VERSION,
            roomId = settings.roomId,
            senderDeviceId = settings.deviceId,
            transmissionId = transmissionId,
            sequence = sequence,
            timestampEpochMs = timestampEpochMs,
            payload = encoded,
        )
        val bytes = PacketProtocol.encode(packet)
        deviceRegistryRepository.devices.first()
            .filter { it.isOnline && it.isPaired && it.id != settings.deviceId }
            .forEach { peer ->
                val host = peer.hostAddress ?: return@forEach
                udpAudioSender.send(host, NetworkDefaults.UDP_AUDIO_PORT, bytes)
            }
    }

    private fun onIncomingAudioPacket(packet: PacketProtocol.AudioPacket) {
        appScope.launch(ioDispatcher) {
            val settings = settingsRepository.settings.first()
            if (_isTransmitting.value) return@launch
            if (packet.senderDeviceId == settings.deviceId) return@launch
            if (packet.roomId != settings.roomId) return@launch
            if (!pairedDevicesRepository.isPaired(packet.senderDeviceId)) return@launch

            val channel = _channelState.value
            // Ignore late packets from a finished transmission.
            if (channel.transmissionId != null &&
                channel.transmissionId != packet.transmissionId
            ) {
                return@launch
            }
            if (!channel.isBusy) {
                // Do not revive a cleared channel from delayed UDP. Only extend an
                // already-announced session, or accept audio briefly if announce was lost
                // and packet is fresh.
                val ageMs = System.currentTimeMillis() - packet.timestampEpochMs
                if (ageMs > NetworkDefaults.REMOTE_AUDIO_IDLE_CLEAR_MS) {
                    return@launch
                }
                val name = deviceRegistryRepository.devices.first()
                    .firstOrNull { it.id == packet.senderDeviceId }
                    ?.name
                    ?: packet.senderDeviceId.take(8)
                applyBusy(
                    deviceId = packet.senderDeviceId,
                    deviceName = name,
                    transmissionId = packet.transmissionId,
                    leaseExpiresAtEpochMs = System.currentTimeMillis() +
                        NetworkDefaults.REMOTE_AUDIO_IDLE_CLEAR_MS * 2,
                )
            } else if (channel.currentTransmitterId == packet.senderDeviceId) {
                // Refresh short idle lease while audio is flowing.
                val refreshed = channel.copy(
                    leaseExpiresAtEpochMs = System.currentTimeMillis() +
                        NetworkDefaults.TRANSMISSION_LEASE_MS,
                )
                _channelState.value = refreshed
            }

            lastRemoteAudioAtMs.set(System.currentTimeMillis())
            jitterBuffer.offer(packet)
        }
    }

    private suspend fun playbackLoop() {
        while (active) {
            val packet = jitterBuffer.poll()
            if (packet != null && !_isTransmitting.value) {
                val settings = settingsRepository.settings.first()
                if (settings.speakerEnabled) {
                    audioEngine.setPlaybackVolume(settings.speakerVolume)
                    audioEngine.ensureLoudspeaker()
                    audioEngine.decodeAndPlay(packet.payload, packet.payload.size)
                }
            } else {
                delay(5)
            }
        }
    }

    private suspend fun leaseWatchLoop() {
        while (active) {
            delay(500)
            val settings = settingsRepository.settings.first()
            clearStaleBusyIfNeeded(settings.deviceId)
            val state = _channelState.value
            val expires = state.leaseExpiresAtEpochMs ?: continue
            if (state.isBusy && System.currentTimeMillis() > expires) {
                HomeTalkLog.w(LogArea.AUDIO, "Transmission lease expired")
                if (_isTransmitting.value) {
                    releaseTalk()
                } else {
                    clearChannel(state.transmissionId, announce = false)
                }
            }
        }
    }
}
