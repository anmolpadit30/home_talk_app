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
import com.hometalk.app.util.LanHosts
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
    private val sessionMutex = Mutex()
    private val jitterBuffer = JitterBuffer()
    private val lastRemoteAudioAtMs = AtomicLong(0L)
    private val udpRxCount = AtomicLong(0L)
    private val udpTxCount = AtomicLong(0L)

    private val _channelState = MutableStateFlow(ChannelState())
    override val channelState: StateFlow<ChannelState> = _channelState.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    override val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _talkingToDeviceId = MutableStateFlow<String?>(null)
    override val talkingToDeviceId: StateFlow<String?> = _talkingToDeviceId.asStateFlow()

    private var active = false
    private var localTransmissionId: String? = null
    /** When set, UDP + TxAnnounce go only to this peer. */
    private var directedTargetId: String? = null
    private var playbackJob: Job? = null
    private var leaseWatchJob: Job? = null

    override suspend fun start() = sessionMutex.withLock {
        if (!PhaseConfig.AUDIO_ENABLED) return
        if (active) return
        active = true
        udpRxCount.set(0L)
        udpTxCount.set(0L)
        udpAudioSender.ensureSocket()
        udpAudioReceiver.setListener { packet -> onIncomingAudioPacket(packet) }
        udpAudioReceiver.start()
        audioEngine.setEncodedFrameListener { encoded, _, sequence, timestamp ->
            appScope.launch(ioDispatcher) {
                sendEncodedFrame(encoded, sequence, timestamp)
            }
        }
        audioEngine.startPlayback()
        var settings = settingsRepository.settings.first()
        if (!settings.speakerEnabled) {
            // Mute from notification testing was leaving all receive silent.
            settingsRepository.updateSpeakerEnabled(true)
            settings = settingsRepository.settings.first()
            HomeTalkLog.w(LogArea.AUDIO, "Speaker was muted — re-enabled")
        }
        audioEngine.setPlaybackVolume(settings.speakerVolume)
        HomeTalkLog.i(
            LogArea.AUDIO,
            "Playback ready speakerEnabled=${settings.speakerEnabled} volume=${settings.speakerVolume}",
        )
        playbackJob = appScope.launch(ioDispatcher) { playbackLoop() }
        leaseWatchJob = appScope.launch(ioDispatcher) { leaseWatchLoop() }
        HomeTalkLog.i(LogArea.AUDIO, "TalkSessionManager started")
    }

    override suspend fun stop() = sessionMutex.withLock {
        if (!active) return
        active = false
        playbackJob?.cancel()
        playbackJob = null
        leaseWatchJob?.cancel()
        leaseWatchJob = null
        runCatching { releaseTalk() }
        audioEngine.stopCapture()
        audioEngine.stopPlayback()
        udpAudioReceiver.stop()
        udpAudioSender.close()
        audioEngine.setEncodedFrameListener(null)
        jitterBuffer.clear()
        lastRemoteAudioAtMs.set(0L)
        directedTargetId = null
        _talkingToDeviceId.value = null
        _channelState.value = ChannelState()
        _isTransmitting.value = false
        HomeTalkLog.i(LogArea.AUDIO, "TalkSessionManager stopped")
    }

    override suspend fun refreshListening(
        hardRestartUdp: Boolean,
        forceRebuildPlayback: Boolean,
    ) = sessionMutex.withLock {
        if (!active) return
        val talking = _isTransmitting.value || _channelState.value.isBusy
        HomeTalkLog.i(
            LogArea.AUDIO,
            "Refreshing listen transport hardUdp=$hardRestartUdp forceTrack=$forceRebuildPlayback talking=$talking",
        )
        udpAudioSender.ensureSocket()
        val udpDead = !udpAudioReceiver.isListening()
        // Never tear down the socket mid-call unless it is already dead.
        if (udpDead || (hardRestartUdp && !talking)) {
            udpAudioReceiver.restart()
        }
        audioEngine.ensurePlaybackAlive(
            forceRebuild = forceRebuildPlayback && !talking,
        )
        val settings = settingsRepository.settings.first()
        audioEngine.setPlaybackVolume(settings.speakerVolume)
        audioEngine.ensureLoudspeaker()
        if (!talking) {
            warmUdpPath(settings.deviceId)
        }
    }

    override suspend fun requestTalk(targetDeviceId: String?): Boolean = talkMutex.withLock {
        if (!PhaseConfig.AUDIO_ENABLED) return false
        if (_isTransmitting.value) {
            return directedTargetId == targetDeviceId || targetDeviceId == null && directedTargetId == null
        }
        val settings = settingsRepository.settings.first()
        val onlinePaired = deviceRegistryRepository.devices.first()
            .filter { it.isOnline && it.isPaired && it.id != settings.deviceId }
        if (onlinePaired.isEmpty()) {
            HomeTalkLog.w(LogArea.AUDIO, "No online paired peers for talk")
            return false
        }
        if (targetDeviceId != null) {
            val target = onlinePaired.firstOrNull { it.id == targetDeviceId }
            if (target == null || !LanHosts.isUsable(target.hostAddress)) {
                HomeTalkLog.w(LogArea.AUDIO, "Direct talk target offline or unreachable id=$targetDeviceId")
                return false
            }
        }

        val transmissionId = UUID.randomUUID().toString()
        HomeTalkLog.i(
            LogArea.AUDIO,
            "PTT duplex start tx=$transmissionId direct=${targetDeviceId?.take(8)}",
        )

        localTransmissionId = transmissionId
        directedTargetId = targetDeviceId
        _talkingToDeviceId.value = targetDeviceId
        _isTransmitting.value = true

        // Full duplex: always grant locally and announce — never wait for exclusive channel.
        val granted = grantLocally(
            deviceId = settings.deviceId,
            deviceName = settings.deviceName,
            roomId = settings.roomId,
            transmissionId = transmissionId,
            announce = true,
            announceOnlyDeviceId = targetDeviceId,
        )
        if (!granted) {
            abortLocalGrant()
            return false
        }

        // Keep receiving / playing while we transmit.
        audioEngine.startCapture()
        HomeTalkLog.i(
            LogArea.AUDIO,
            "Transmission started (duplex) tx=$transmissionId direct=${targetDeviceId?.take(8) ?: "broadcast"}",
        )
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
        val directTarget = directedTargetId
        _isTransmitting.value = false
        localTransmissionId = null
        directedTargetId = null
        _talkingToDeviceId.value = null
        if (transmissionId != null) {
            // Only clear presence if the channel still shows us (keep remote talker UI).
            if (_channelState.value.currentTransmitterId == settings.deviceId) {
                clearChannel(transmissionId, announce = false)
            }
            appScope.launch(ioDispatcher) {
                if (directTarget == null) {
                    notifyRelease(settings.deviceId, transmissionId)
                }
                broadcast(
                    ControlMessage.TxClear(transmissionId = transmissionId),
                    onlyDeviceId = directTarget,
                )
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
                if (_channelState.value.transmissionId == message.transmissionId ||
                    _channelState.value.currentTransmitterId == message.deviceId ||
                    message.transmissionId.isBlank()
                ) {
                    clearChannel(message.transmissionId, announce = false)
                }
                ControlMessage.TxAck()
            }
            is ControlMessage.TxAnnounce -> {
                // Full duplex: always surface who else is talking, even while we TX.
                if (message.deviceId != settingsRepository.settings.first().deviceId) {
                    applyBusy(
                        deviceId = message.deviceId,
                        deviceName = message.deviceName,
                        transmissionId = message.transmissionId,
                        leaseExpiresAtEpochMs = message.leaseExpiresAtEpochMs,
                    )
                }
                ControlMessage.TxAck()
            }
            is ControlMessage.TxClear -> {
                val localId = settingsRepository.settings.first().deviceId
                val showingRemote =
                    _channelState.value.currentTransmitterId != null &&
                        _channelState.value.currentTransmitterId != localId
                if (showingRemote &&
                    (_channelState.value.transmissionId == message.transmissionId ||
                        message.transmissionId.isBlank() ||
                        _channelState.value.transmissionId.isNullOrBlank())
                ) {
                    clearChannel(message.transmissionId, announce = false)
                } else if (!_isTransmitting.value &&
                    (_channelState.value.transmissionId == message.transmissionId ||
                        message.transmissionId.isBlank() ||
                        _channelState.value.transmissionId.isNullOrBlank())
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
        // Full duplex: always grant — never deny for "busy".
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
        announceOnlyDeviceId: String? = null,
        leaseExpiresAtEpochMs: Long = System.currentTimeMillis() + NetworkDefaults.TRANSMISSION_LEASE_MS,
    ): Boolean {
        val localId = settingsRepository.settings.first().deviceId
        val remoteTalker =
            _channelState.value.isBusy &&
                _channelState.value.currentTransmitterId != null &&
                _channelState.value.currentTransmitterId != localId
        // Don't overwrite "hearing remote" presence when we start our own TX.
        if (!(deviceId == localId && remoteTalker)) {
            applyBusy(deviceId, deviceName, transmissionId, leaseExpiresAtEpochMs)
        }
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
                    onlyDeviceId = announceOnlyDeviceId,
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
        lastRemoteAudioAtMs.set(0L)
        if (announce) {
            appScope.launch(ioDispatcher) {
                broadcast(ControlMessage.TxClear(transmissionId = transmissionId.orEmpty()))
            }
        }
    }

    private fun abortLocalGrant() {
        _isTransmitting.value = false
        localTransmissionId = null
        directedTargetId = null
        _talkingToDeviceId.value = null
    }

    private fun clearStaleBusyIfNeeded(localDeviceId: String) {
        val state = _channelState.value
        if (!state.isBusy) return
        if (state.currentTransmitterId == localDeviceId) return
        val now = System.currentTimeMillis()
        val leaseExpired = state.leaseExpiresAtEpochMs?.let { now > it } == true
        val lastAudio = lastRemoteAudioAtMs.get()
        // lastAudio==0 means "UDP has not arrived yet", NOT idle. Clearing on that
        // wiped TxAnnounce and killed receive (busy flash + silence).
        val audioIdle = lastAudio > 0L &&
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

    private suspend fun broadcast(message: ControlMessage, onlyDeviceId: String? = null) {
        val settings = settingsRepository.settings.first()
        deviceRegistryRepository.devices.first()
            .filter { it.isOnline && it.isPaired && it.id != settings.deviceId }
            .filter { onlyDeviceId == null || it.id == onlyDeviceId }
            .forEach { peer ->
                val host = peer.hostAddress ?: return@forEach
                if (!LanHosts.isUsable(host)) return@forEach
                tcpControlClient.exchange(
                    host = host,
                    port = peer.controlPort ?: NetworkDefaults.TCP_CONTROL_PORT,
                    message = message,
                )
            }
    }

    /**
     * Nudge the Wi-Fi radio / NAT path while the screen is off so the first
     * real talk packet is not dropped by OEM power saving.
     */
    private suspend fun warmUdpPath(localDeviceId: String) {
        val probe = byteArrayOf(0x00)
        val peers = deviceRegistryRepository.devices.first()
            .filter { it.isOnline && it.isPaired && it.id != localDeviceId }
        peers.forEach { peer ->
            val host = peer.hostAddress
            if (!LanHosts.isUsable(host)) return@forEach
            udpAudioSender.send(host!!, NetworkDefaults.UDP_AUDIO_PORT, probe)
        }
    }

    private suspend fun sendEncodedFrame(encoded: ByteArray, sequence: Int, timestampEpochMs: Long) {
        if (!_isTransmitting.value) return
        val settings = settingsRepository.settings.first()
        val transmissionId = localTransmissionId ?: return
        val directTarget = directedTargetId
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
        val peers = deviceRegistryRepository.devices.first()
            .filter { it.isOnline && it.isPaired && it.id != settings.deviceId }
            .filter { directTarget == null || it.id == directTarget }
        if (peers.isEmpty()) {
            HomeTalkLog.w(LogArea.AUDIO, "UDP TX skipped — no online paired peers")
            return
        }
        peers.forEach { peer ->
            val host = peer.hostAddress
            if (!LanHosts.isUsable(host)) {
                HomeTalkLog.w(LogArea.AUDIO, "UDP TX skipped peer=${peer.name} host=$host — unusable")
                return@forEach
            }
            udpAudioSender.send(host!!, NetworkDefaults.UDP_AUDIO_PORT, bytes)
            val n = udpTxCount.incrementAndGet()
            if (n == 1L || n % 50L == 0L) {
                HomeTalkLog.i(LogArea.AUDIO, "UDP TX count=$n → $host:${NetworkDefaults.UDP_AUDIO_PORT} peer=${peer.name}")
            }
        }
    }

    private fun onIncomingAudioPacket(packet: PacketProtocol.AudioPacket) {
        appScope.launch(ioDispatcher) {
            val settings = settingsRepository.settings.first()
            if (packet.senderDeviceId == settings.deviceId) return@launch
            if (!pairedDevicesRepository.isPaired(packet.senderDeviceId)) {
                if (udpRxCount.get() < 3L) {
                    HomeTalkLog.w(LogArea.AUDIO, "UDP drop not paired sender=${packet.senderDeviceId.take(8)}")
                }
                return@launch
            }
            // Room IDs can drift after re-pairing different phones; still accept
            // audio from an explicitly paired peer so Talk keeps working.
            if (packet.roomId != settings.roomId && udpRxCount.get() < 3L) {
                HomeTalkLog.w(
                    LogArea.AUDIO,
                    "UDP room mismatch packetRoom=${packet.roomId.take(8)} localRoom=${settings.roomId.take(8)} — accepting paired peer",
                )
            }

            val channel = _channelState.value
            val lease = System.currentTimeMillis() + NetworkDefaults.TRANSMISSION_LEASE_MS
            if (channel.currentTransmitterId == packet.senderDeviceId) {
                _channelState.value = channel.copy(leaseExpiresAtEpochMs = lease)
            } else {
                // Prefer showing the remote talker (even over local announce presence).
                val name = deviceRegistryRepository.devices.first()
                    .firstOrNull { it.id == packet.senderDeviceId }
                    ?.name
                    ?: packet.senderDeviceId.take(8)
                applyBusy(
                    deviceId = packet.senderDeviceId,
                    deviceName = name,
                    transmissionId = packet.transmissionId,
                    leaseExpiresAtEpochMs = lease,
                )
            }

            lastRemoteAudioAtMs.set(System.currentTimeMillis())
            jitterBuffer.offer(packet)
            val n = udpRxCount.incrementAndGet()
            if (n == 1L || n % 50L == 0L) {
                HomeTalkLog.i(
                    LogArea.AUDIO,
                    "UDP RX count=$n from=${packet.senderDeviceId.take(8)} seq=${packet.sequence} jitter=${jitterBuffer.size()} duplexTx=${_isTransmitting.value}",
                )
            }
        }
    }

    private suspend fun playbackLoop() {
        var lastRouteRefreshMs = 0L
        var cachedSpeakerEnabled = true
        var cachedVolume = com.hometalk.app.domain.model.UserSettings.DEFAULT_SPEAKER_VOLUME
        var lastSettingsRefreshMs = 0L
        var played = 0L
        while (active) {
            val packet = jitterBuffer.poll()
            if (packet != null) {
                val now = System.currentTimeMillis()
                if (now - lastSettingsRefreshMs > 1_000L) {
                    val settings = settingsRepository.settings.first()
                    cachedSpeakerEnabled = settings.speakerEnabled
                    cachedVolume = settings.speakerVolume
                    lastSettingsRefreshMs = now
                    if (!cachedSpeakerEnabled) {
                        HomeTalkLog.w(LogArea.AUDIO, "Speaker muted — UDP audio not played")
                    }
                }
                if (cachedSpeakerEnabled) {
                    if (now - lastRouteRefreshMs > 3_000L) {
                        // Duck slightly while we also transmit to reduce speaker→mic echo.
                        val volume = if (_isTransmitting.value) {
                            (cachedVolume * 0.72f).coerceIn(0.2f, 1f)
                        } else {
                            cachedVolume
                        }
                        audioEngine.setPlaybackVolume(volume)
                        audioEngine.ensureLoudspeaker()
                        lastRouteRefreshMs = now
                    }
                    audioEngine.decodeAndPlay(packet.payload, packet.payload.size)
                    played++
                    if (played == 1L || played % 50L == 0L) {
                        HomeTalkLog.i(LogArea.AUDIO, "Playback frames=$played duplexTx=${_isTransmitting.value}")
                    }
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
            if (!state.isBusy || System.currentTimeMillis() <= expires) continue
            HomeTalkLog.w(
                LogArea.AUDIO,
                "Presence lease expired holder=${state.currentTransmitterName} localTx=${_isTransmitting.value}",
            )
            when {
                // Still holding PTT with our own presence — extend, don't stop TX.
                state.currentTransmitterId == settings.deviceId && _isTransmitting.value -> {
                    _channelState.value = state.copy(
                        leaseExpiresAtEpochMs = System.currentTimeMillis() +
                            NetworkDefaults.TRANSMISSION_LEASE_MS,
                    )
                }
                else -> clearChannel(state.transmissionId, announce = false)
            }
        }
    }
}
