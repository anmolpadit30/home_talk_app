package com.hometalk.app.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

interface DiscoveryListener {
    fun onServiceResolved(info: DiscoveredServiceInfo)
    fun onServiceLost(serviceName: String)
}

@Singleton
class NsdDeviceDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceDiscovery {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val running = AtomicBoolean(false)
    private val resolving = ConcurrentHashMap.newKeySet<String>()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null
    private var callback: DiscoveryListener? = null
    private var localDeviceId: String? = null

    fun setListener(listener: DiscoveryListener?) {
        callback = listener
    }

    fun startAdvertising(
        deviceId: String,
        deviceName: String,
        roomId: String,
        roomName: String,
    ) {
        localDeviceId = deviceId
        if (!running.get()) {
            startInternal()
        }
        stopAdvertisingOnly()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "${NetworkDefaults.NSD_SERVICE_NAME_PREFIX}-${deviceId.take(8)}"
            serviceType = NetworkDefaults.NSD_SERVICE_TYPE
            port = NetworkDefaults.TCP_CONTROL_PORT
            NsdTxtAttributes.apply(this, deviceId, deviceName, roomId, roomName)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
                HomeTalkLog.i(LogArea.DISCOVERY, "NSD registered name=${info.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                HomeTalkLog.e(LogArea.DISCOVERY, "NSD registration failed code=$errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                HomeTalkLog.i(LogArea.DISCOVERY, "NSD unregistered name=${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                HomeTalkLog.w(LogArea.DISCOVERY, "NSD unregistration failed code=$errorCode")
            }
        }
        registrationListener = listener
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.DISCOVERY, "NSD register threw", error)
        }
    }

    override fun start() {
        startInternal()
    }

    private fun startInternal() {
        if (!running.compareAndSet(false, true)) return
        acquireMulticastLock()
        startBrowsing()
        HomeTalkLog.i(LogArea.DISCOVERY, "NSD discovery started")
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        stopBrowsing()
        stopAdvertisingOnly()
        releaseMulticastLock()
        resolving.clear()
        HomeTalkLog.i(LogArea.DISCOVERY, "NSD discovery stopped")
    }

    fun restartBrowsing() {
        if (!running.get()) return
        stopBrowsing()
        startBrowsing()
    }

    private fun startBrowsing() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                HomeTalkLog.d(LogArea.DISCOVERY, "NSD browse started type=$serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != NetworkDefaults.NSD_SERVICE_TYPE &&
                    !serviceInfo.serviceType.contains("hometalk")
                ) {
                    return
                }
                val name = serviceInfo.serviceName
                if (name == registeredServiceName) return
                if (!resolving.add(name)) return
                HomeTalkLog.d(LogArea.DISCOVERY, "NSD found name=$name – resolving")
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                HomeTalkLog.d(LogArea.DISCOVERY, "NSD lost name=${serviceInfo.serviceName}")
                callback?.onServiceLost(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                HomeTalkLog.d(LogArea.DISCOVERY, "NSD browse stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                HomeTalkLog.e(LogArea.DISCOVERY, "NSD start browse failed code=$errorCode")
                running.set(false)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                HomeTalkLog.w(LogArea.DISCOVERY, "NSD stop browse failed code=$errorCode")
            }
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices(
                NetworkDefaults.NSD_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener,
            )
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.DISCOVERY, "NSD discover threw", error)
        }
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        try {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolving.remove(serviceInfo.serviceName)
                        HomeTalkLog.w(
                            LogArea.DISCOVERY,
                            "NSD resolve failed name=${serviceInfo.serviceName} code=$errorCode",
                        )
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        resolving.remove(resolved.serviceName)
                        val host = resolved.host?.hostAddress
                        if (host.isNullOrBlank()) {
                            HomeTalkLog.w(LogArea.DISCOVERY, "NSD resolved without host")
                            return
                        }
                        val parsed = NsdTxtAttributes.parse(resolved, host, resolved.port)
                        if (parsed == null) {
                            HomeTalkLog.w(LogArea.DISCOVERY, "NSD missing TXT attrs name=${resolved.serviceName}")
                            return
                        }
                        if (parsed.deviceId == localDeviceId) return
                        HomeTalkLog.i(
                            LogArea.DISCOVERY,
                            "NSD resolved id=${parsed.deviceId} name=${parsed.deviceName} host=${parsed.hostAddress}:${parsed.controlPort}",
                        )
                        callback?.onServiceResolved(parsed)
                    }
                },
            )
        } catch (error: Exception) {
            resolving.remove(serviceInfo.serviceName)
            HomeTalkLog.e(LogArea.DISCOVERY, "NSD resolve threw", error)
        }
    }

    private fun stopBrowsing() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.DISCOVERY, "NSD stop browse threw", error)
        }
    }

    private fun stopAdvertisingOnly() {
        val listener = registrationListener ?: return
        registrationListener = null
        try {
            nsdManager.unregisterService(listener)
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.DISCOVERY, "NSD unregister threw", error)
        }
        registeredServiceName = null
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val lock = wifiManager.createMulticastLock("HomeTalkNsd").apply {
            setReferenceCounted(false)
            acquire()
        }
        multicastLock = lock
        HomeTalkLog.d(LogArea.DISCOVERY, "Multicast lock acquired")
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.takeIf { it.isHeld }?.release()
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.DISCOVERY, "Multicast lock release failed", error)
        }
        multicastLock = null
    }
}
