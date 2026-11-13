package com.hometalk.app.data.discovery

import android.content.Context
import com.hometalk.app.data.network.ControlMessage
import com.hometalk.app.data.network.TcpControlClient
import com.hometalk.app.data.network.TcpControlServer
import com.hometalk.app.di.ApplicationScope
import com.hometalk.app.di.IoDispatcher
import com.hometalk.app.domain.model.Device
import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.domain.model.PairedDeviceRecord
import com.hometalk.app.domain.model.PairingSession
import com.hometalk.app.domain.model.PhaseConfig
import com.hometalk.app.domain.model.UserSettings
import com.hometalk.app.domain.repository.ConnectionRepository
import com.hometalk.app.domain.repository.DeviceRegistryRepository
import com.hometalk.app.domain.repository.DiscoveryController
import com.hometalk.app.domain.repository.PairedDevicesRepository
import com.hometalk.app.domain.repository.SettingsRepository
import com.hometalk.app.domain.repository.TalkController
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LanHosts
import com.hometalk.app.util.LocalIpv4
import com.hometalk.app.util.LogArea
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class DiscoveryCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val nsdDeviceDiscovery: NsdDeviceDiscovery,
    private val tcpControlServer: TcpControlServer,
    private val tcpControlClient: TcpControlClient,
    private val settingsRepository: SettingsRepository,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val deviceRegistryRepository: DeviceRegistryRepository,
    private val connectionRepository: ConnectionRepository,
    private val talkController: TalkController,
) : DiscoveryController {

    private val serviceNameToDeviceId = ConcurrentHashMap<String, String>()
    private val startStopMutex = Mutex()
    private val secureRandom = SecureRandom()

    private val _pairingSession = MutableStateFlow<PairingSession?>(null)
    override val pairingSession: StateFlow<PairingSession?> = _pairingSession.asStateFlow()

    private val _statusMessage = MutableStateFlow("Discovery idle")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var started = false
    private var networkActive = false
    private var maintenanceJob: Job? = null
    private var wifiJob: Job? = null
    private var lastLocalIpv4: String? = null

    override fun start() {
        if (!PhaseConfig.DISCOVERY_ENABLED) {
            HomeTalkLog.i(LogArea.DISCOVERY, "Discovery disabled by PhaseConfig")
            return
        }
        if (started) return
        started = true
        HomeTalkLog.i(LogArea.DISCOVERY, "DiscoveryCoordinator starting")
        appScope.launch(ioDispatcher) {
            settingsRepository.ensureInitialized()
            seedPairedDevicesOffline()
        }
        nsdDeviceDiscovery.setListener(
            object : DiscoveryListener {
                override fun onServiceResolved(info: DiscoveredServiceInfo) {
                    appScope.launch(ioDispatcher) { onPeerResolved(info) }
                }

                override fun onServiceLost(serviceName: String) {
                    appScope.launch(ioDispatcher) { onPeerServiceLost(serviceName) }
                }
            },
        )
        wifiJob = appScope.launch(ioDispatcher) {
            connectionRepository.wifiStatus.collectLatest { status ->
                if (status.connected) {
                    val previousIp = lastLocalIpv4
                    val newIp = status.localIpv4
                    val ipChanged = !newIp.isNullOrBlank() &&
                        !previousIp.isNullOrBlank() &&
                        newIp != previousIp
                    lastLocalIpv4 = newIp
                    if (ipChanged && networkActive) {
                        HomeTalkLog.i(
                            LogArea.DISCOVERY,
                            "Local IP changed $previousIp → $newIp — refreshing discovery",
                        )
                        stopNetworkServices(markOffline = false)
                    }
                    startNetworkServices()
                    reconnectProbeBurst()
                } else {
                    lastLocalIpv4 = null
                    stopNetworkServices(markOffline = true)
                }
            }
        }
    }

    override fun stop() {
        started = false
        wifiJob?.cancel()
        wifiJob = null
        appScope.launch(ioDispatcher) {
            stopNetworkServices(markOffline = true)
            nsdDeviceDiscovery.setListener(null)
        }
    }

    override fun refresh() {
        appScope.launch(ioDispatcher) {
            if (!networkActive) return@launch
            nsdDeviceDiscovery.restartBrowsing()
            probeAllKnownPeers()
            _statusMessage.value = "Refreshing discovery…"
        }
    }

    override fun republishIdentity() {
        appScope.launch(ioDispatcher) {
            if (!networkActive) return@launch
            republishAdvertisement()
        }
    }

    override suspend fun beginPairingSession(): PairingSession {
        settingsRepository.ensureInitialized()
        val settings = settingsRepository.settings.first()
        val pin = generatePin()
        val host = LocalIpv4.resolve(context)
        val session = PairingSession(
            pin = pin,
            expiresAtEpochMs = System.currentTimeMillis() + NetworkDefaults.PAIRING_SESSION_TTL_MS,
            localHostAddress = host,
            controlPort = NetworkDefaults.TCP_CONTROL_PORT,
        )
        _pairingSession.value = session
        HomeTalkLog.i(LogArea.DISCOVERY, "Pairing session started pin=$pin host=$host")
        return session
    }

    override fun endPairingSession() {
        _pairingSession.value = null
        HomeTalkLog.i(LogArea.DISCOVERY, "Pairing session ended")
    }

    override suspend fun pairWithDiscoveredDevice(deviceId: String, pin: String): Result<Unit> {
        val device = deviceRegistryRepository.devices.first().firstOrNull { it.id == deviceId }
            ?: return Result.failure(IllegalStateException("Device is not currently discoverable"))
        val host = device.hostAddress
            ?: return Result.failure(IllegalStateException("Device has no host address yet"))
        val port = device.controlPort ?: NetworkDefaults.TCP_CONTROL_PORT
        return pairWithHost(host, port, pin)
    }

    override suspend fun pairWithHost(hostAddress: String, controlPort: Int, pin: String): Result<Unit> {
        val settings = settingsRepository.settings.first()
        if (settings.deviceId.isBlank()) {
            return Result.failure(IllegalStateException("Local device is not ready"))
        }
        val cleanedPin = pin.trim()
        if (cleanedPin.length != NetworkDefaults.PAIRING_PIN_LENGTH) {
            return Result.failure(IllegalArgumentException("PIN must be ${NetworkDefaults.PAIRING_PIN_LENGTH} digits"))
        }
        val request = ControlMessage.PairRequest(
            deviceId = settings.deviceId,
            deviceName = settings.deviceName,
            roomId = settings.roomId,
            roomName = settings.roomName,
            pin = cleanedPin,
            controlPort = NetworkDefaults.TCP_CONTROL_PORT,
        )
        val response = tcpControlClient.exchange(hostAddress, controlPort, request)
            .getOrElse { return Result.failure(it) }
        return when (response) {
            is ControlMessage.PairAccept -> {
                completePairingAsGuest(response, hostAddress)
                Result.success(Unit)
            }
            is ControlMessage.PairReject -> {
                Result.failure(IllegalStateException(response.reason.ifBlank { "Pairing rejected" }))
            }
            else -> Result.failure(IllegalStateException("Unexpected pairing response"))
        }
    }

    override suspend fun unpair(deviceId: String) {
        pairedDevicesRepository.remove(deviceId)
        deviceRegistryRepository.remove(deviceId)
    }

    override suspend fun probeManualPeer(hostAddress: String) {
        val host = hostAddress.trim()
        if (host.isBlank()) return
        helloProbe(host, NetworkDefaults.TCP_CONTROL_PORT)
    }

    private suspend fun startNetworkServices() = startStopMutex.withLock {
        if (networkActive) {
            republishAdvertisement()
            return
        }
        networkActive = true
        _statusMessage.value = "Listening on home Wi-Fi"
        tcpControlServer.start(::handleIncomingControlMessage)
        nsdDeviceDiscovery.start()
        republishAdvertisement()
        talkController.start()
        maintenanceJob?.cancel()
        maintenanceJob = appScope.launch(ioDispatcher) { maintenanceLoop() }
        HomeTalkLog.i(LogArea.DISCOVERY, "Network discovery services started")
    }

    private suspend fun stopNetworkServices(markOffline: Boolean) = startStopMutex.withLock {
        if (!networkActive && maintenanceJob == null) return
        networkActive = false
        maintenanceJob?.cancel()
        maintenanceJob = null
        talkController.stop()
        tcpControlServer.stop()
        nsdDeviceDiscovery.stop()
        serviceNameToDeviceId.clear()
        if (markOffline) {
            val now = System.currentTimeMillis()
            deviceRegistryRepository.devices.first().forEach { device ->
                if (device.isOnline) {
                    deviceRegistryRepository.markOffline(device.id, now)
                }
            }
        }
        _statusMessage.value = "Wi-Fi disconnected"
        HomeTalkLog.i(LogArea.DISCOVERY, "Network discovery services stopped")
    }

    private suspend fun republishAdvertisement() {
        val settings = settingsRepository.settings.first()
        if (settings.deviceId.isBlank()) return
        nsdDeviceDiscovery.startAdvertising(
            deviceId = settings.deviceId,
            deviceName = settings.deviceName,
            roomId = settings.roomId,
            roomName = settings.roomName,
        )
    }

    private suspend fun seedPairedDevicesOffline() {
        val paired = pairedDevicesRepository.getSnapshot()
        paired.forEach { record ->
            val usableHost = record.hostAddress?.takeIf { LanHosts.isUsable(it) }
            if (record.hostAddress != null && usableHost == null) {
                HomeTalkLog.w(
                    LogArea.DISCOVERY,
                    "Scrubbing stale peer host ${record.hostAddress} for ${record.name}",
                )
                pairedDevicesRepository.upsert(record.copy(hostAddress = null))
            }
            deviceRegistryRepository.upsert(
                Device(
                    id = record.id,
                    name = record.name,
                    isOnline = false,
                    isPaired = true,
                    lastSeenEpochMs = record.lastSeenEpochMs,
                    hostAddress = usableHost,
                    controlPort = record.controlPort,
                ),
            )
        }
    }

    private suspend fun onPeerResolved(info: DiscoveredServiceInfo) {
        serviceNameToDeviceId[info.serviceName] = info.deviceId
        val paired = pairedDevicesRepository.isPaired(info.deviceId)
        val now = System.currentTimeMillis()
        deviceRegistryRepository.upsert(
            Device(
                id = info.deviceId,
                name = info.deviceName,
                isOnline = true,
                isPaired = paired,
                lastSeenEpochMs = now,
                hostAddress = info.hostAddress,
                controlPort = info.controlPort,
            ),
        )
        if (paired) {
            pairedDevicesRepository.upsert(
                PairedDeviceRecord(
                    id = info.deviceId,
                    name = info.deviceName,
                    hostAddress = info.hostAddress,
                    controlPort = info.controlPort,
                    pairedAtEpochMs = pairedDevicesRepository.getSnapshot()
                        .firstOrNull { it.id == info.deviceId }
                        ?.pairedAtEpochMs
                        ?: now,
                    lastSeenEpochMs = now,
                ),
            )
            helloProbe(info.hostAddress, info.controlPort)
        }
        _statusMessage.value = "Found ${info.deviceName}"
    }

    private suspend fun onPeerServiceLost(serviceName: String) {
        val deviceId = serviceNameToDeviceId.remove(serviceName) ?: return
        deviceRegistryRepository.markOffline(deviceId, System.currentTimeMillis())
    }

    private suspend fun handleIncomingControlMessage(
        remoteHost: String,
        message: ControlMessage,
    ): ControlMessage? {
        val settings = settingsRepository.settings.first()
        return when (message) {
            is ControlMessage.Hello -> {
                upsertFromHello(message, remoteHost)
                ControlMessage.HelloAck(
                    deviceId = settings.deviceId,
                    deviceName = settings.deviceName,
                    roomId = settings.roomId,
                    roomName = settings.roomName,
                    paired = pairedDevicesRepository.isPaired(message.deviceId),
                    controlPort = NetworkDefaults.TCP_CONTROL_PORT,
                )
            }
            is ControlMessage.Heartbeat -> {
                touchPeer(message.deviceId, remoteHost, null)
                ControlMessage.Heartbeat(
                    deviceId = settings.deviceId,
                    timestampEpochMs = System.currentTimeMillis(),
                )
            }
            is ControlMessage.PairRequest -> handlePairRequest(message, remoteHost, settings)
            is ControlMessage.TxRequest,
            is ControlMessage.TxRelease,
            is ControlMessage.TxAnnounce,
            is ControlMessage.TxClear,
            -> talkController.handleControlMessage(remoteHost, message)
            else -> ControlMessage.PairReject(reason = "unsupported_message")
        }
    }

    private suspend fun handlePairRequest(
        request: ControlMessage.PairRequest,
        remoteHost: String,
        settings: UserSettings,
    ): ControlMessage {
        if (!PhaseConfig.PAIRING_ENABLED) {
            return ControlMessage.PairReject(reason = "pairing_disabled")
        }
        val session = _pairingSession.value
        if (session == null || session.isExpired()) {
            endPairingSession()
            return ControlMessage.PairReject(reason = "pairing_not_active")
        }
        if (request.pin != session.pin) {
            return ControlMessage.PairReject(reason = "invalid_pin")
        }
        if (request.deviceId == settings.deviceId) {
            return ControlMessage.PairReject(reason = "cannot_pair_with_self")
        }
        val now = System.currentTimeMillis()
        pairedDevicesRepository.upsert(
            PairedDeviceRecord(
                id = request.deviceId,
                name = request.deviceName,
                hostAddress = remoteHost,
                controlPort = request.controlPort,
                pairedAtEpochMs = now,
                lastSeenEpochMs = now,
            ),
        )
        deviceRegistryRepository.upsert(
            Device(
                id = request.deviceId,
                name = request.deviceName,
                isOnline = true,
                isPaired = true,
                lastSeenEpochMs = now,
                hostAddress = remoteHost,
                controlPort = request.controlPort,
            ),
        )
        endPairingSession()
        HomeTalkLog.i(LogArea.DISCOVERY, "Accepted pair from ${request.deviceName} ($remoteHost)")
        _statusMessage.value = "Paired with ${request.deviceName}"
        return ControlMessage.PairAccept(
            deviceId = settings.deviceId,
            deviceName = settings.deviceName,
            roomId = settings.roomId,
            roomName = settings.roomName,
            controlPort = NetworkDefaults.TCP_CONTROL_PORT,
        )
    }

    private suspend fun completePairingAsGuest(
        accept: ControlMessage.PairAccept,
        hostAddress: String,
    ) {
        val now = System.currentTimeMillis()
        settingsRepository.updateRoomIdentity(accept.roomId, accept.roomName)
        pairedDevicesRepository.upsert(
            PairedDeviceRecord(
                id = accept.deviceId,
                name = accept.deviceName,
                hostAddress = hostAddress,
                controlPort = accept.controlPort,
                pairedAtEpochMs = now,
                lastSeenEpochMs = now,
            ),
        )
        deviceRegistryRepository.upsert(
            Device(
                id = accept.deviceId,
                name = accept.deviceName,
                isOnline = true,
                isPaired = true,
                lastSeenEpochMs = now,
                hostAddress = hostAddress,
                controlPort = accept.controlPort,
            ),
        )
        republishAdvertisement()
        _statusMessage.value = "Paired with ${accept.deviceName}"
        HomeTalkLog.i(LogArea.DISCOVERY, "Paired as guest with ${accept.deviceName} room=${accept.roomId}")
    }

    private suspend fun upsertFromHello(hello: ControlMessage.Hello, remoteHost: String) {
        val paired = pairedDevicesRepository.isPaired(hello.deviceId)
        val now = System.currentTimeMillis()
        deviceRegistryRepository.upsert(
            Device(
                id = hello.deviceId,
                name = hello.deviceName,
                isOnline = true,
                isPaired = paired,
                lastSeenEpochMs = now,
                hostAddress = remoteHost,
                controlPort = hello.controlPort,
            ),
        )
        if (paired) {
            pairedDevicesRepository.upsert(
                PairedDeviceRecord(
                    id = hello.deviceId,
                    name = hello.deviceName,
                    hostAddress = remoteHost,
                    controlPort = hello.controlPort,
                    pairedAtEpochMs = pairedDevicesRepository.getSnapshot()
                        .firstOrNull { it.id == hello.deviceId }
                        ?.pairedAtEpochMs
                        ?: now,
                    lastSeenEpochMs = now,
                ),
            )
        }
    }

    private suspend fun touchPeer(deviceId: String, host: String?, controlPort: Int?) {
        val existing = deviceRegistryRepository.devices.first().firstOrNull { it.id == deviceId }
        val paired = pairedDevicesRepository.isPaired(deviceId)
        val now = System.currentTimeMillis()
        deviceRegistryRepository.upsert(
            Device(
                id = deviceId,
                name = existing?.name ?: deviceId.take(8),
                isOnline = true,
                isPaired = paired,
                lastSeenEpochMs = now,
                hostAddress = host ?: existing?.hostAddress,
                controlPort = controlPort ?: existing?.controlPort,
            ),
        )
    }

    private suspend fun helloProbe(host: String, port: Int): Boolean {
        if (!LanHosts.isUsable(host)) return false
        val settings = settingsRepository.settings.first()
        if (settings.deviceId.isBlank()) return false
        val result = tcpControlClient.exchange(
            host = host,
            port = port,
            message = ControlMessage.Hello(
                deviceId = settings.deviceId,
                deviceName = settings.deviceName,
                roomId = settings.roomId,
                roomName = settings.roomName,
            ),
        )
        val response = result.getOrNull()
        if (response is ControlMessage.HelloAck) {
            upsertFromHello(
                ControlMessage.Hello(
                    deviceId = response.deviceId,
                    deviceName = response.deviceName,
                    roomId = response.roomId,
                    roomName = response.roomName,
                    controlPort = response.controlPort,
                    audioPort = response.audioPort,
                ),
                remoteHost = host,
            )
            if (response.paired || pairedDevicesRepository.isPaired(response.deviceId)) {
                val now = System.currentTimeMillis()
                deviceRegistryRepository.upsert(
                    Device(
                        id = response.deviceId,
                        name = response.deviceName,
                        isOnline = true,
                        isPaired = pairedDevicesRepository.isPaired(response.deviceId),
                        lastSeenEpochMs = now,
                        hostAddress = host,
                        controlPort = response.controlPort,
                    ),
                )
            }
            return true
        }
        if (result.isFailure) {
            clearStaleHost(host)
        }
        return false
    }

    private suspend fun maintenanceLoop() {
        var tick = 0
        while (networkActive) {
            expirePairingIfNeeded()
            expireStalePeers()
            if (tick % 5 == 0) {
                probeAllKnownPeers()
            }
            if (tick % 3 == 0) {
                nsdDeviceDiscovery.restartBrowsing()
            }
            tick++
            delay(NetworkDefaults.HEARTBEAT_INTERVAL_MS)
        }
    }

    private suspend fun reconnectProbeBurst() {
        repeat(NetworkDefaults.RECONNECT_PROBE_BURST) { index ->
            if (!networkActive) return
            HomeTalkLog.d(LogArea.DISCOVERY, "Reconnect probe burst ${index + 1}")
            nsdDeviceDiscovery.restartBrowsing()
            republishAdvertisement()
            probeAllKnownPeers()
            if (index < NetworkDefaults.RECONNECT_PROBE_BURST - 1) {
                delay(NetworkDefaults.RECONNECT_PROBE_GAP_MS)
            }
        }
        _statusMessage.value = "Reconnected — searching for family phones…"
    }

    private suspend fun probeAllKnownPeers() {
        val settings = settingsRepository.settings.first()
        val devices = deviceRegistryRepository.devices.first()
        val hosts = linkedSetOf<String>()
        devices.mapNotNull { it.hostAddress }.forEach { hosts += it }
        pairedDevicesRepository.getSnapshot().mapNotNull { it.hostAddress }.forEach { hosts += it }
        settings.manualPeerAddresses.forEach { hosts += it }
        val localIp = lastLocalIpv4 ?: LocalIpv4.resolve(context)
        hosts
            .filter { LanHosts.isUsable(it) && it != localIp }
            .forEach { host -> helloProbe(host, NetworkDefaults.TCP_CONTROL_PORT) }
    }

    private suspend fun clearStaleHost(host: String) {
        deviceRegistryRepository.devices.first()
            .filter { it.hostAddress == host }
            .forEach { device ->
                HomeTalkLog.w(LogArea.DISCOVERY, "Clearing unreachable host=$host for ${device.name}")
                deviceRegistryRepository.upsert(device.copy(isOnline = false, hostAddress = null))
                if (device.isPaired) {
                    val existing = pairedDevicesRepository.getSnapshot()
                        .firstOrNull { it.id == device.id } ?: return@forEach
                    pairedDevicesRepository.upsert(existing.copy(hostAddress = null))
                }
            }
    }

    private suspend fun expireStalePeers() {
        val now = System.currentTimeMillis()
        deviceRegistryRepository.devices.first()
            .filter { it.isOnline }
            .forEach { device ->
                val lastSeen = device.lastSeenEpochMs ?: return@forEach
                if (now - lastSeen > NetworkDefaults.PEER_TIMEOUT_MS) {
                    deviceRegistryRepository.markOffline(device.id, now)
                }
            }
    }

    private fun expirePairingIfNeeded() {
        val session = _pairingSession.value ?: return
        if (session.isExpired()) {
            endPairingSession()
        }
    }

    private fun generatePin(): String {
        val max = 10.0.pow(NetworkDefaults.PAIRING_PIN_LENGTH).toInt()
        val value = secureRandom.nextInt(max)
        return value.toString().padStart(NetworkDefaults.PAIRING_PIN_LENGTH, '0')
    }
}
