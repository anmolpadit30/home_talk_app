package com.hometalk.app.util

import android.util.Log

/**
 * Centralized logging. Keep area tags stable so audio, networking, and
 * service lifecycle can be filtered in logcat during later phases.
 */
object HomeTalkLog {
    private const val TAG = "HomeTalk"

    fun d(area: String, message: String) {
        Log.d(TAG, "[$area] $message")
    }

    fun i(area: String, message: String) {
        Log.i(TAG, "[$area] $message")
    }

    fun w(area: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(TAG, "[$area] $message")
        } else {
            Log.w(TAG, "[$area] $message", throwable)
        }
    }

    fun e(area: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(TAG, "[$area] $message")
        } else {
            Log.e(TAG, "[$area] $message", throwable)
        }
    }
}

object LogArea {
    const val APP = "App"
    const val UI = "UI"
    const val PREFS = "Prefs"
    const val WIFI = "Wifi"
    const val PERMISSION = "Permission"
    const val AUDIO = "Audio"
    const val NETWORK = "Network"
    const val SERVICE = "Service"
    const val DISCOVERY = "Discovery"
}
