package com.hometalk.app.domain.model

/**
 * Shared protocol defaults. Concrete packet layout lands in Phase 3.
 * Values live in one place so later TCP/UDP code cannot drift.
 */
object NetworkDefaults {
    const val PROTOCOL_VERSION: Byte = 1
    const val NSD_SERVICE_TYPE = "_hometalk._tcp."
    const val NSD_SERVICE_NAME_PREFIX = "HomeTalk"
    const val TCP_CONTROL_PORT = 49_870
    const val UDP_AUDIO_PORT = 49_871
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNELS = 1
    const val FRAME_DURATION_MS = 20
    const val HEARTBEAT_INTERVAL_MS = 2_000L
    const val PEER_TIMEOUT_MS = 8_000L
    const val TRANSMISSION_LEASE_MS = 30_000L
    /** Clear a remote "busy" lock if no audio arrives for this long. */
    const val REMOTE_AUDIO_IDLE_CLEAR_MS = 1_500L
    const val PAIRING_PIN_LENGTH = 6
    const val PAIRING_SESSION_TTL_MS = 5 * 60_000L
    const val TCP_CONNECT_TIMEOUT_MS = 3_000
    const val TCP_READ_TIMEOUT_MS = 4_000
    const val MANUAL_PEER_PROBE_INTERVAL_MS = 10_000L
    /** Stop the foreground listener after Wi-Fi has been down this long. */
    const val WIFI_SLEEP_AFTER_MS = 10 * 60_000L
    /** Burst of peer probes right after Wi-Fi returns or the local IP changes. */
    const val RECONNECT_PROBE_BURST = 3
    const val RECONNECT_PROBE_GAP_MS = 1_000L
}
