package com.hometalk.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hometalk.app.domain.model.HomeSnapshot
import com.hometalk.app.domain.model.PhaseConfig
import com.hometalk.app.domain.model.TalkButtonState
import com.hometalk.app.domain.repository.SettingsRepository
import com.hometalk.app.domain.repository.TalkController
import com.hometalk.app.domain.usecase.ObserveHomeStateUseCase
import com.hometalk.app.presentation.permissions.PermissionSnapshot
import com.hometalk.app.service.ServiceController
import com.hometalk.app.service.WifiSleepCoordinator
import com.hometalk.app.service.WifiSleepState
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHomeState: ObserveHomeStateUseCase,
    private val settingsRepository: SettingsRepository,
    private val talkController: TalkController,
    private val serviceController: ServiceController,
    wifiSleepCoordinator: WifiSleepCoordinator,
) : ViewModel() {

    private val microphonePermission = MutableStateFlow(false)
    private val notificationPermission = MutableStateFlow(true)
    private val nearbyWifiPermission = MutableStateFlow(true)

    val uiState: StateFlow<HomeUiState> = combine(
        observeHomeState(
            isLocallyTransmitting = talkController.isTransmitting,
            channel = talkController.channelState,
            microphonePermissionGranted = microphonePermission,
            notificationPermissionGranted = notificationPermission,
            nearbyWifiPermissionGranted = nearbyWifiPermission,
        ),
        wifiSleepCoordinator.state,
    ) { snapshot, sleep -> snapshot.toUiState(sleep) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    init {
        viewModelScope.launch {
            settingsRepository.ensureInitialized()
        }
    }

    fun updatePermissions(snapshot: PermissionSnapshot) {
        val micBecameGranted = snapshot.microphone && !microphonePermission.value
        microphonePermission.value = snapshot.microphone
        notificationPermission.value = snapshot.notifications
        nearbyWifiPermission.value = snapshot.nearbyWifi
        HomeTalkLog.d(
            LogArea.PERMISSION,
            "mic=${snapshot.microphone} notifications=${snapshot.notifications} nearbyWifi=${snapshot.nearbyWifi}",
        )
        if (micBecameGranted || snapshot.notifications) {
            // Restart/refresh FGS so it can claim the microphone service type.
            serviceController.startService()
        }
    }

    fun onTalkPressed() {
        val current = uiState.value
        if (current.talkButtonState == TalkButtonState.Disconnected) {
            HomeTalkLog.i(LogArea.UI, "Talk pressed while disconnected")
            return
        }
        if (!current.microphonePermissionGranted) {
            HomeTalkLog.w(LogArea.PERMISSION, "Talk pressed without microphone permission")
            return
        }
        // Always ask TalkController — it clears stale busy locks. UI Busy is advisory.
        viewModelScope.launch {
            val granted = talkController.requestTalk()
            HomeTalkLog.i(
                LogArea.UI,
                "PTT down granted=$granted priorState=${current.talkButtonState} audioEnabled=${PhaseConfig.AUDIO_ENABLED}",
            )
        }
    }

    fun onTalkReleased() {
        viewModelScope.launch {
            talkController.releaseTalk()
            HomeTalkLog.i(LogArea.UI, "PTT up")
        }
    }

    fun toggleSpeaker() {
        viewModelScope.launch {
            val enabled = !(uiState.value.speakerEnabled)
            settingsRepository.updateSpeakerEnabled(enabled)
            HomeTalkLog.d(LogArea.UI, "Speaker enabled=$enabled")
        }
    }

    private fun HomeSnapshot.toUiState(sleep: WifiSleepState): HomeUiState {
        val online = onlinePairedDevices
        val unpairedOnline = devices.count { it.isOnline && !it.isPaired }
        val unreachablePaired = devices.count { it.isPaired && !it.isOnline }
        val statusMessage = when {
            sleep is WifiSleepState.Sleeping ->
                "Sleeping to save battery. HomeTalk will listen again when Wi-Fi reconnects."
            sleep is WifiSleepState.GracePeriod ->
                "Wi-Fi disconnected. Listening will sleep after 10 minutes offline to save battery."
            !wifiConnected -> "Connect this phone to your home Wi-Fi to use HomeTalk."
            online.isEmpty() && unpairedOnline > 0 ->
                "Found $unpairedOnline nearby phone(s). Open Family to pair before talking."
            online.isEmpty() && unreachablePaired > 0 ->
                "Paired phones are unreachable. They may be offline or on another network."
            online.isEmpty() ->
                "Waiting for paired family members. Open Family to discover and pair phones."
            isLocallyTransmitting -> "You are transmitting"
            channel.currentTransmitterName != null ->
                "${channel.currentTransmitterName} is talking"
            else -> "Hold Talk to speak to everyone."
        }
        return HomeUiState(
            isLoading = settings.deviceId.isBlank(),
            deviceName = settings.deviceName,
            roomName = settings.roomName,
            wifiConnected = wifiConnected,
            connectedDeviceCount = online.size,
            connectedDevices = online,
            currentTransmitterName = channel.currentTransmitterName,
            talkButtonState = talkButtonState,
            speakerEnabled = settings.speakerEnabled,
            microphonePermissionGranted = microphonePermissionGranted,
            notificationPermissionGranted = notificationPermissionGranted,
            nearbyWifiPermissionGranted = nearbyWifiPermissionGranted,
            statusMessage = statusMessage,
        )
    }
}
