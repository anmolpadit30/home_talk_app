package com.hometalk.app.domain.usecase

import com.hometalk.app.domain.model.ChannelState
import com.hometalk.app.domain.model.HomeSnapshot
import com.hometalk.app.domain.repository.ConnectionRepository
import com.hometalk.app.domain.repository.DeviceRegistryRepository
import com.hometalk.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveHomeStateUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val connectionRepository: ConnectionRepository,
    private val deviceRegistryRepository: DeviceRegistryRepository,
) {
    operator fun invoke(
        isLocallyTransmitting: Flow<Boolean>,
        channel: Flow<ChannelState>,
        microphonePermissionGranted: Flow<Boolean>,
        notificationPermissionGranted: Flow<Boolean>,
        nearbyWifiPermissionGranted: Flow<Boolean>,
    ): Flow<HomeSnapshot> = combine(
        settingsRepository.settings,
        connectionRepository.wifiStatus,
        deviceRegistryRepository.devices,
        isLocallyTransmitting,
        combine(
            channel,
            microphonePermissionGranted,
            notificationPermissionGranted,
            nearbyWifiPermissionGranted,
        ) { channelState, mic, notifications, nearbyWifi ->
            PermissionAndChannel(
                channel = channelState,
                microphonePermissionGranted = mic,
                notificationPermissionGranted = notifications,
                nearbyWifiPermissionGranted = nearbyWifi,
            )
        },
    ) { settings, wifi, devices, transmitting, extras ->
        HomeSnapshot(
            settings = settings,
            wifiConnected = wifi.connected,
            devices = devices,
            channel = extras.channel,
            isLocallyTransmitting = transmitting,
            microphonePermissionGranted = extras.microphonePermissionGranted,
            notificationPermissionGranted = extras.notificationPermissionGranted,
            nearbyWifiPermissionGranted = extras.nearbyWifiPermissionGranted,
        )
    }

    private data class PermissionAndChannel(
        val channel: ChannelState,
        val microphonePermissionGranted: Boolean,
        val notificationPermissionGranted: Boolean,
        val nearbyWifiPermissionGranted: Boolean,
    )
}
