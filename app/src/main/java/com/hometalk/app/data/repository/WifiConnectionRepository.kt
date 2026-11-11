package com.hometalk.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.hometalk.app.di.ApplicationScope
import com.hometalk.app.di.IoDispatcher
import com.hometalk.app.domain.model.WifiStatus
import com.hometalk.app.domain.repository.ConnectionRepository
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes Wi-Fi transport only. Does not require internet validation so
 * HomeTalk can work on an offline home LAN.
 */
@Singleton
class WifiConnectionRepository @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ConnectionRepository {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val wifiStatus: Flow<WifiStatus> = callbackFlow {
        val wifiNetworks = mutableSetOf<Network>()

        fun localIpv4(): String? {
            for (network in wifiNetworks) {
                val link: LinkProperties =
                    connectivityManager.getLinkProperties(network) ?: continue
                val address = link.linkAddresses
                    .mapNotNull { it.address as? Inet4Address }
                    .firstOrNull { !it.isLoopbackAddress }
                    ?.hostAddress
                if (!address.isNullOrBlank()) return address
            }
            return null
        }

        fun emitState() {
            val connected = wifiNetworks.isNotEmpty()
            val ip = if (connected) localIpv4() else null
            HomeTalkLog.d(
                LogArea.WIFI,
                "Wi-Fi connected=$connected networks=${wifiNetworks.size} ip=$ip",
            )
            trySend(WifiStatus(connected = connected, localIpv4 = ip))
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiNetworks.add(network)
                emitState()
            }

            override fun onLost(network: Network) {
                wifiNetworks.remove(network)
                emitState()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (wifiNetworks.contains(network)) {
                    emitState()
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    wifiNetworks.add(network)
                } else {
                    wifiNetworks.remove(network)
                }
                emitState()
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val active = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(active)
        if (active != null && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            wifiNetworks.add(active)
        }
        emitState()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.WIFI, "Failed to register network callback", error)
            trySend(WifiStatus(connected = false))
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (error: Exception) {
                HomeTalkLog.w(LogArea.WIFI, "Failed to unregister network callback", error)
            }
        }
    }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)
        .shareIn(appScope, SharingStarted.Eagerly, replay = 1)
}