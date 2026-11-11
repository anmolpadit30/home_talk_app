package com.hometalk.app.presentation.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hometalk.app.data.network.PairingQrCodec
import com.hometalk.app.domain.model.Device
import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.domain.model.PairingQrPayload
import com.hometalk.app.domain.model.PairingSession
import com.hometalk.app.domain.model.PhaseConfig
import com.hometalk.app.domain.model.UserSettings
import com.hometalk.app.domain.repository.DiscoveryController
import com.hometalk.app.domain.repository.DeviceRegistryRepository
import com.hometalk.app.domain.repository.SettingsRepository
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val devices: List<Device> = emptyList(),
    val discoveryEnabled: Boolean = PhaseConfig.DISCOVERY_ENABLED,
    val discoveryStatus: String = "",
    val pairingSession: PairingSession? = null,
    val pairingQrPayload: String? = null,
    val showPairDialogFor: Device? = null,
    val pairPinInput: String = "",
    val showManualPairDialog: Boolean = false,
    val manualHostInput: String = "",
    val manualPinInput: String = "",
    val actionMessage: String? = null,
    val isBusy: Boolean = false,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    deviceRegistryRepository: DeviceRegistryRepository,
    private val discoveryController: DiscoveryController,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val pairPinInput = MutableStateFlow("")
    private val showPairDialogFor = MutableStateFlow<Device?>(null)
    private val showManualPairDialog = MutableStateFlow(false)
    private val manualHostInput = MutableStateFlow("")
    private val manualPinInput = MutableStateFlow("")
    private val actionMessage = MutableStateFlow<String?>(null)
    private val isBusy = MutableStateFlow(false)
    private val localSettings = MutableStateFlow(UserSettings.uninitialized())

    val uiState: StateFlow<DevicesUiState> = combine(
        deviceRegistryRepository.devices,
        discoveryController.pairingSession,
        discoveryController.statusMessage,
        combine(
            pairPinInput,
            showPairDialogFor,
            showManualPairDialog,
            manualHostInput,
            manualPinInput,
        ) { pin, pairTarget, manualVisible, manualHost, manualPin ->
            DialogSlice(pin, pairTarget, manualVisible, manualHost, manualPin)
        },
        combine(actionMessage, isBusy, localSettings) { message, busy, settings ->
            Triple(message, busy, settings)
        },
    ) { devices, session, status, dialogs, extras ->
        val settings = extras.third
        DevicesUiState(
            devices = devices.sortedWith(
                compareByDescending<Device> { it.isOnline }
                    .thenByDescending { it.isPaired }
                    .thenBy { it.name.lowercase() },
            ),
            discoveryStatus = status,
            pairingSession = session,
            pairingQrPayload = session?.let { active ->
                val host = active.localHostAddress ?: return@let null
                PairingQrCodec.encode(
                    PairingQrPayload(
                        protocolVersion = NetworkDefaults.PROTOCOL_VERSION.toInt(),
                        deviceId = settings.deviceId,
                        deviceName = settings.deviceName,
                        roomId = settings.roomId,
                        roomName = settings.roomName,
                        hostAddress = host,
                        controlPort = active.controlPort,
                        pin = active.pin,
                    ),
                )
            },
            showPairDialogFor = dialogs.pairTarget,
            pairPinInput = dialogs.pin,
            showManualPairDialog = dialogs.manualVisible,
            manualHostInput = dialogs.manualHost,
            manualPinInput = dialogs.manualPin,
            actionMessage = extras.first,
            isBusy = extras.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DevicesUiState(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.ensureInitialized()
            settingsRepository.settings.collect { localSettings.value = it }
        }
    }

    fun refresh() {
        discoveryController.refresh()
        actionMessage.value = "Refreshing discovery…"
    }

    fun startHostingPairing() {
        viewModelScope.launch {
            isBusy.value = true
            try {
                discoveryController.beginPairingSession()
                actionMessage.value = "Share this PIN or QR with the other phone"
            } catch (error: Exception) {
                HomeTalkLog.e(LogArea.DISCOVERY, "Failed to start pairing session", error)
                actionMessage.value = error.message ?: "Could not start pairing"
            } finally {
                isBusy.value = false
            }
        }
    }

    fun stopHostingPairing() {
        discoveryController.endPairingSession()
        actionMessage.value = "Pairing stopped"
    }

    fun openPairDialog(device: Device) {
        showPairDialogFor.value = device
        pairPinInput.value = ""
    }

    fun dismissPairDialog() {
        showPairDialogFor.value = null
        pairPinInput.value = ""
    }

    fun onPairPinChanged(value: String) {
        pairPinInput.value = value.filter { it.isDigit() }.take(NetworkDefaults.PAIRING_PIN_LENGTH)
    }

    fun confirmPairWithDevice() {
        val device = showPairDialogFor.value ?: return
        val pin = pairPinInput.value
        viewModelScope.launch {
            isBusy.value = true
            val result = discoveryController.pairWithDiscoveredDevice(device.id, pin)
            isBusy.value = false
            result.onSuccess {
                actionMessage.value = "Paired with ${device.name}"
                dismissPairDialog()
            }.onFailure { error ->
                actionMessage.value = error.message ?: "Pairing failed"
                HomeTalkLog.w(LogArea.DISCOVERY, "Pair failed for ${device.id}", error)
            }
        }
    }

    fun openManualPairDialog() {
        showManualPairDialog.value = true
        manualHostInput.value = ""
        manualPinInput.value = ""
    }

    fun dismissManualPairDialog() {
        showManualPairDialog.value = false
    }

    fun onManualHostChanged(value: String) {
        manualHostInput.value = value.trim()
    }

    fun onManualPinChanged(value: String) {
        manualPinInput.value = value.filter { it.isDigit() }.take(NetworkDefaults.PAIRING_PIN_LENGTH)
    }

    fun confirmManualPair() {
        val host = manualHostInput.value
        val pin = manualPinInput.value
        viewModelScope.launch {
            isBusy.value = true
            val result = discoveryController.pairWithHost(
                hostAddress = host,
                controlPort = NetworkDefaults.TCP_CONTROL_PORT,
                pin = pin,
            )
            isBusy.value = false
            result.onSuccess {
                actionMessage.value = "Paired with $host"
                dismissManualPairDialog()
            }.onFailure { error ->
                actionMessage.value = error.message ?: "Manual pairing failed"
            }
        }
    }

    fun unpair(device: Device) {
        viewModelScope.launch {
            discoveryController.unpair(device.id)
            actionMessage.value = "Removed ${device.name}"
        }
    }

    fun consumeActionMessage() {
        actionMessage.update { null }
    }

    private data class DialogSlice(
        val pin: String,
        val pairTarget: Device?,
        val manualVisible: Boolean,
        val manualHost: String,
        val manualPin: String,
    )
}
