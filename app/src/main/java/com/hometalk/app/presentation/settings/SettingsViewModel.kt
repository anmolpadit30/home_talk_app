package com.hometalk.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hometalk.app.domain.model.PairedDeviceRecord
import com.hometalk.app.domain.model.UserSettings
import com.hometalk.app.domain.repository.DiscoveryController
import com.hometalk.app.domain.repository.PairedDevicesRepository
import com.hometalk.app.domain.repository.SettingsRepository
import com.hometalk.app.service.ServiceController
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pairedDevicesRepository: PairedDevicesRepository,
    private val discoveryController: DiscoveryController,
    private val serviceController: ServiceController,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings.uninitialized(),
    )

    val pairedDevices: StateFlow<List<PairedDeviceRecord>> = pairedDevicesRepository.pairedDevices.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch { settingsRepository.ensureInitialized() }
    }

    fun updateDeviceName(name: String) = launchUpdate("deviceName") {
        settingsRepository.updateDeviceName(name)
        discoveryController.republishIdentity()
    }

    fun updateRoomName(name: String) = launchUpdate("roomName") {
        settingsRepository.updateRoomName(name)
        discoveryController.republishIdentity()
    }

    fun updateSpeakerVolume(volume: Float) = launchUpdate("speakerVolume") {
        settingsRepository.updateSpeakerVolume(volume)
    }

    fun updateNotificationsEnabled(enabled: Boolean) = launchUpdate("notifications") {
        settingsRepository.updateNotificationsEnabled(enabled)
    }

    fun updateAudioBitrate(bitrateKbps: Int) = launchUpdate("bitrate") {
        settingsRepository.updateAudioBitrateKbps(bitrateKbps)
    }

    fun addManualPeer(address: String) {
        val cleaned = address.trim()
        if (cleaned.isBlank()) return
        val current = settings.value.manualPeerAddresses
        updateManualPeers(current + cleaned)
        viewModelScope.launch {
            discoveryController.probeManualPeer(cleaned)
        }
    }

    fun removeManualPeer(address: String) {
        updateManualPeers(settings.value.manualPeerAddresses.filterNot { it == address })
    }

    fun unpair(deviceId: String) {
        viewModelScope.launch {
            discoveryController.unpair(deviceId)
        }
    }

    fun updateKeepServiceInBackground(enabled: Boolean) = launchUpdate("backgroundService") {
        settingsRepository.updateKeepServiceInBackground(enabled)
        if (enabled) {
            serviceController.startService()
        } else {
            serviceController.stopService()
        }
    }

    private fun updateManualPeers(addresses: List<String>) = launchUpdate("manualPeers") {
        settingsRepository.updateManualPeerAddresses(addresses)
        discoveryController.refresh()
    }

    private fun launchUpdate(field: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                HomeTalkLog.d(LogArea.PREFS, "Updated $field")
            } catch (error: Exception) {
                HomeTalkLog.e(LogArea.PREFS, "Failed to update $field", error)
            }
        }
    }
}
