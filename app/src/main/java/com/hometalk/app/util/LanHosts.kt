package com.hometalk.app.util

/**
 * Helpers for deciding whether a stored peer IP is still usable on the
 * current home LAN. Stale emulator addresses (10.0.2.x) were causing TCP/UDP
 * to go nowhere after phones moved to a real Wi-Fi network.
 */
object LanHosts {
    fun isUsable(host: String?): Boolean {
        val value = host?.trim().orEmpty()
        if (value.isEmpty()) return false
        if (value == "127.0.0.1" || value == "0.0.0.0") return false
        // Android emulator NAT — never reachable from a physical phone.
        if (value.startsWith("10.0.2.")) return false
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }

    /** Rough same-/24 check for typical home routers. */
    fun likelySameLan(localIp: String?, peerIp: String?): Boolean {
        if (!isUsable(localIp) || !isUsable(peerIp)) return false
        val a = localIp!!.split('.')
        val b = peerIp!!.split('.')
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2]
    }
}
