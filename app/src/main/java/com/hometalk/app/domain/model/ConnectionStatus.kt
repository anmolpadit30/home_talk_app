package com.hometalk.app.domain.model

data class WifiStatus(
    val connected: Boolean,
    val localIpv4: String? = null,
)

enum class HomeNetworkStatus {
    WifiConnected,
    WifiDisconnected,
}

data class ConnectionStatus(
    val wifi: WifiStatus = WifiStatus(connected = false),
    val homeNetwork: HomeNetworkStatus = HomeNetworkStatus.WifiDisconnected,
    val onlinePeerCount: Int = 0,
)
