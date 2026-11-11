package com.hometalk.app.presentation.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object AppPermissions {
    fun requiredRuntimePermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return permissions.toTypedArray()
    }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun microphoneGranted(context: Context): Boolean =
        isGranted(context, Manifest.permission.RECORD_AUDIO)

    fun notificationsGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    fun nearbyWifiGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            true
        }
}

data class PermissionSnapshot(
    val microphone: Boolean,
    val notifications: Boolean,
    val nearbyWifi: Boolean,
) {
    val essentialGranted: Boolean get() = microphone
}

fun Context.permissionSnapshot(): PermissionSnapshot = PermissionSnapshot(
    microphone = AppPermissions.microphoneGranted(this),
    notifications = AppPermissions.notificationsGranted(this),
    nearbyWifi = AppPermissions.nearbyWifiGranted(this),
)
