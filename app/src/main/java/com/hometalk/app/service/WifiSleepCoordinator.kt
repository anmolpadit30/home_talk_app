package com.hometalk.app.service

import com.hometalk.app.di.ApplicationScope
import com.hometalk.app.di.IoDispatcher
import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.domain.model.PhaseConfig
import com.hometalk.app.domain.repository.ConnectionRepository
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stops the foreground walkie service after prolonged Wi-Fi loss to save battery,
 * then starts listening again when Wi-Fi returns (while the process is alive).
 */
@Singleton
class WifiSleepCoordinator @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: SettingsRepository,
    private val serviceController: ServiceController,
    private val talkController: TalkController,
) {
    private val _state = MutableStateFlow<WifiSleepState>(WifiSleepState.Active)
    val state: StateFlow<WifiSleepState> = _state.asStateFlow()

    private var started = false
    private var sleepJob: Job? = null

    fun start() {
        if (started) return
        started = true
        if (!PhaseConfig.WIFI_SLEEP_ENABLED) {
            HomeTalkLog.i(LogArea.SERVICE, "Wi-Fi sleep disabled by PhaseConfig")
            return
        }
        HomeTalkLog.i(
            LogArea.SERVICE,
            "WifiSleepCoordinator started graceMs=${NetworkDefaults.WIFI_SLEEP_AFTER_MS}",
        )
        appScope.launch(ioDispatcher) {
            connectionRepository.wifiStatus.collectLatest { status ->
                if (status.connected) {
                    onWifiConnected()
                } else {
                    onWifiDisconnected()
                }
            }
        }
    }

    private suspend fun onWifiConnected() {
        sleepJob?.cancel()
        sleepJob = null
        val wasSleeping = _state.value is WifiSleepState.Sleeping ||
            _state.value is WifiSleepState.GracePeriod
        _state.value = WifiSleepState.Active
        if (wasSleeping) {
            HomeTalkLog.i(LogArea.SERVICE, "Wi-Fi restored — resuming listener")
        }
        val settings = settingsRepository.settings.first()
        if (settings.keepServiceInBackground) {
            serviceController.startService()
        }
    }

    private suspend fun onWifiDisconnected() {
        runCatching { talkController.releaseTalk() }
        val existing = _state.value
        val disconnectedAt = when (existing) {
            is WifiSleepState.GracePeriod -> existing.disconnectedAtEpochMs
            WifiSleepState.Sleeping -> return
            WifiSleepState.Active -> System.currentTimeMillis()
        }
        _state.value = WifiSleepState.GracePeriod(disconnectedAt)
        val remaining = NetworkDefaults.WIFI_SLEEP_AFTER_MS -
            (System.currentTimeMillis() - disconnectedAt)
        if (remaining <= 0L) {
            enterSleep()
            return
        }
        sleepJob?.cancel()
        sleepJob = appScope.launch(ioDispatcher) {
            HomeTalkLog.i(
                LogArea.SERVICE,
                "Wi-Fi lost — will sleep in ${remaining / 1000}s if still offline",
            )
            delay(remaining)
            enterSleep()
        }
    }

    private suspend fun enterSleep() {
        val wifi = connectionRepository.wifiStatus.first()
        if (wifi.connected) {
            _state.value = WifiSleepState.Active
            return
        }
        HomeTalkLog.i(LogArea.SERVICE, "Wi-Fi still down — sleeping foreground service")
        _state.value = WifiSleepState.Sleeping
        serviceController.stopService()
    }
}
