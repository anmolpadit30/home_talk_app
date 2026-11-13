package com.hometalk.app.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds CPU / Wi-Fi / multicast locks while the foreground service listens.
 * Vivo/OriginOS and Realme/ColorOS otherwise freeze UDP after minutes with the screen off.
 */
@Singleton
class ServicePowerKeepAlive @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Suppress("DEPRECATION")
    private var wakeLock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    private var wifiLock: WifiManager.WifiLock? = null

    private var multicastLock: WifiManager.MulticastLock? = null

    @Synchronized
    fun acquire() {
        refreshWakeLock()
        refreshWifiLock()
        refreshMulticastLock()
    }

    @Synchronized
    fun release() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.SERVICE, "WakeLock release failed", error)
        }
        wakeLock = null
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.SERVICE, "WifiLock release failed", error)
        }
        wifiLock = null
        try {
            multicastLock?.takeIf { it.isHeld }?.release()
        } catch (error: Exception) {
            HomeTalkLog.w(LogArea.SERVICE, "MulticastLock release failed", error)
        }
        multicastLock = null
        HomeTalkLog.i(LogArea.SERVICE, "Power keep-alive released")
    }

    private fun refreshWakeLock() {
        val held = wakeLock?.isHeld == true
        if (!held) {
            val lock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HomeTalk::Listening",
            ).apply {
                setReferenceCounted(false)
                // Finite timeout; keepalive re-acquires often so OEM cannot age it out.
                acquire(10 * 60_000L)
            }
            wakeLock = lock
            HomeTalkLog.i(LogArea.SERVICE, "PARTIAL_WAKE_LOCK acquired")
        } else {
            runCatching { wakeLock?.acquire(10 * 60_000L) }
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshWifiLock() {
        if (wifiLock?.isHeld == true) return
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        wifiLock = null
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        runCatching {
            val lock = wifiManager.createWifiLock(mode, "HomeTalk::Wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
            wifiLock = lock
            HomeTalkLog.i(LogArea.SERVICE, "WifiLock acquired mode=$mode")
        }.onFailure { error ->
            // Low-latency can fail on some builds; fall back to high-perf.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val lock = wifiManager.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                        "HomeTalk::Wifi",
                    ).apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                    wifiLock = lock
                    HomeTalkLog.i(LogArea.SERVICE, "WifiLock acquired mode=HIGH_PERF (fallback)")
                }.onFailure {
                    HomeTalkLog.w(LogArea.SERVICE, "WifiLock acquire failed", it)
                }
            } else {
                HomeTalkLog.w(LogArea.SERVICE, "WifiLock acquire failed", error)
            }
        }
    }

    private fun refreshMulticastLock() {
        if (multicastLock?.isHeld == true) return
        runCatching {
            val lock = wifiManager.createMulticastLock("HomeTalk::Multicast").apply {
                setReferenceCounted(false)
                acquire()
            }
            multicastLock = lock
            HomeTalkLog.i(LogArea.SERVICE, "MulticastLock acquired")
        }.onFailure { error ->
            HomeTalkLog.w(LogArea.SERVICE, "MulticastLock acquire failed", error)
        }
    }
}
