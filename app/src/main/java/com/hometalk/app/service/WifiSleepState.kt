package com.hometalk.app.service

/**
 * Battery-saving lifecycle while home Wi-Fi is unavailable.
 * After [com.hometalk.app.domain.model.NetworkDefaults.WIFI_SLEEP_AFTER_MS]
 * without Wi-Fi, the foreground service stops until Wi-Fi returns.
 */
sealed class WifiSleepState {
    data object Active : WifiSleepState()

    data class GracePeriod(
        val disconnectedAtEpochMs: Long,
    ) : WifiSleepState()

    data object Sleeping : WifiSleepState()
}
