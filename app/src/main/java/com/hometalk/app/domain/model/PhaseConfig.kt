package com.hometalk.app.domain.model

/**
 * Feature flags for the current development milestone.
 * Phase 5: reliability, Wi-Fi change refresh, and Wi-Fi-off sleep.
 */
object PhaseConfig {
    const val CURRENT_PHASE = 5
    const val DISCOVERY_ENABLED = true
    const val AUDIO_ENABLED = true
    const val BACKGROUND_SERVICE_ENABLED = true
    const val PAIRING_ENABLED = true
    const val PTT_UI_PREVIEW = false
    const val WIFI_SLEEP_ENABLED = true
}
