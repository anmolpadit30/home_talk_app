package com.hometalk.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hometalk.app.di.ApplicationScope
import com.hometalk.app.di.IoDispatcher
import com.hometalk.app.domain.model.ChannelState
import com.hometalk.app.domain.model.NetworkDefaults
import com.hometalk.app.domain.model.PhaseConfig
import com.hometalk.app.domain.repository.ConnectionRepository
import com.hometalk.app.domain.repository.DeviceRegistryRepository
import com.hometalk.app.domain.repository.DiscoveryController
import com.hometalk.app.domain.repository.SettingsRepository
import com.hometalk.app.domain.repository.TalkController
import com.hometalk.app.presentation.permissions.AppPermissions
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WalkieTalkieService : Service() {

    @Inject lateinit var talkController: TalkController
    @Inject lateinit var discoveryController: DiscoveryController
    @Inject lateinit var deviceRegistryRepository: DeviceRegistryRepository
    @Inject lateinit var connectionRepository: ConnectionRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notificationManager: HomeTalkNotificationManager
    @Inject lateinit var wifiSleepCoordinator: WifiSleepCoordinator
    @Inject lateinit var powerKeepAlive: ServicePowerKeepAlive
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private var observeJob: Job? = null
    private var keepAliveJob: Job? = null
    private var screenReceiverRegistered = false
    private var latestSnapshot: NotificationSnapshot = NotificationSnapshot(
        wifiConnected = false,
        connectedCount = 0,
        isTransmitting = false,
        channel = ChannelState(),
        speakerEnabled = true,
        sleepState = WifiSleepState.Active,
        notificationsEnabled = true,
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    HomeTalkLog.i(LogArea.SERVICE, "Screen off — locking listen path awake")
                    appScope.launch(ioDispatcher) {
                        // Grab locks + fresh UDP/AudioTrack before OEM deep-sleep settles in.
                        refreshListenPath(
                            probePeers = true,
                            hardRestartUdp = true,
                            forceRebuildPlayback = true,
                        )
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT,
                -> {
                    HomeTalkLog.i(LogArea.SERVICE, "Screen awake ($action) — refreshing listen path")
                    appScope.launch(ioDispatcher) {
                        refreshListenPath(
                            probePeers = true,
                            hardRestartUdp = true,
                            forceRebuildPlayback = true,
                        )
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        HomeTalkLog.i(LogArea.SERVICE, "WalkieTalkieService created")
        notificationManager.ensureChannel()
        powerKeepAlive.acquire()
        promoteToForeground(latestSnapshot)
        registerScreenReceiver()
        observeJob = appScope.launch(ioDispatcher) { observeAndUpdateNotification() }
        keepAliveJob = appScope.launch(ioDispatcher) { keepAliveLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ServiceActions.ACTION_STOP -> {
                HomeTalkLog.i(LogArea.SERVICE, "Stop requested")
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ServiceActions.ACTION_TALK -> {
                HomeTalkLog.i(LogArea.SERVICE, "Notification Talk tapped")
                appScope.launch(ioDispatcher) { handleNotificationTalk() }
            }
            ServiceActions.ACTION_STOP_TALK -> {
                HomeTalkLog.i(LogArea.SERVICE, "Notification Stop tapped")
                appScope.launch(ioDispatcher) {
                    talkController.releaseTalk()
                    promoteToForeground(latestSnapshot.copy(isTransmitting = false))
                }
            }
            ServiceActions.ACTION_TOGGLE_SPEAKER -> {
                HomeTalkLog.i(LogArea.SERVICE, "Notification mute/unmute tapped")
                appScope.launch(ioDispatcher) { handleToggleSpeaker() }
            }
            ServiceActions.ACTION_REFRESH_LISTEN -> {
                HomeTalkLog.i(LogArea.SERVICE, "Refresh listen requested")
                appScope.launch(ioDispatcher) {
                    refreshListenPath(
                        probePeers = true,
                        hardRestartUdp = true,
                        forceRebuildPlayback = true,
                    )
                }
            }
            else -> {
                powerKeepAlive.acquire()
                promoteToForeground(latestSnapshot)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        observeJob?.cancel()
        observeJob = null
        unregisterScreenReceiver()
        powerKeepAlive.release()
        appScope.launch(ioDispatcher) {
            runCatching { talkController.releaseTalk() }
        }
        notificationManager.cancel()
        HomeTalkLog.i(LogArea.SERVICE, "WalkieTalkieService destroyed")
        super.onDestroy()
    }

    private suspend fun keepAliveLoop() {
        var tick = 0
        while (true) {
            delay(NetworkDefaults.LISTEN_KEEPALIVE_INTERVAL_MS)
            if (latestSnapshot.sleepState is WifiSleepState.Sleeping) continue
            tick++
            // Hard UDP every cycle (restart is race-safe). Rebuild AudioTrack often —
            // Vivo/Realme pause silent MEDIA tracks after a few minutes screen-off.
            refreshListenPath(
                probePeers = tick % 2 == 0,
                hardRestartUdp = true,
                forceRebuildPlayback = tick % 2 == 0,
            )
        }
    }

    private suspend fun refreshListenPath(
        probePeers: Boolean,
        hardRestartUdp: Boolean,
        forceRebuildPlayback: Boolean = false,
    ) {
        powerKeepAlive.acquire()
        runCatching {
            talkController.refreshListening(
                hardRestartUdp = hardRestartUdp,
                forceRebuildPlayback = forceRebuildPlayback,
            )
        }.onFailure { HomeTalkLog.w(LogArea.SERVICE, "refreshListening failed", it) }
        if (probePeers) {
            runCatching { discoveryController.refresh() }
        }
        promoteToForeground(latestSnapshot)
    }

    private suspend fun observeAndUpdateNotification() {
        combine(
            connectionRepository.wifiStatus,
            deviceRegistryRepository.devices,
            talkController.isTransmitting,
            talkController.channelState,
            combine(
                settingsRepository.settings,
                wifiSleepCoordinator.state,
            ) { settings, sleep -> settings to sleep },
        ) { wifi, devices, transmitting, channel, settingsAndSleep ->
            val (settings, sleep) = settingsAndSleep
            NotificationSnapshot(
                wifiConnected = wifi.connected,
                connectedCount = devices.count { it.isOnline && it.isPaired },
                isTransmitting = transmitting,
                channel = channel,
                speakerEnabled = settings.speakerEnabled,
                sleepState = sleep,
                notificationsEnabled = settings.notificationsEnabled,
            ) to settings.keepServiceInBackground
        }.collectLatest { (snapshot, keepBackground) ->
            latestSnapshot = snapshot
            if (!keepBackground) {
                HomeTalkLog.i(LogArea.SERVICE, "Background service disabled in settings")
                stopSelfSafely()
                return@collectLatest
            }
            if (snapshot.sleepState is WifiSleepState.Sleeping) {
                HomeTalkLog.i(LogArea.SERVICE, "Sleeping — stopping foreground service")
                stopSelfSafely()
                return@collectLatest
            }
            powerKeepAlive.acquire()
            promoteToForeground(snapshot)
        }
    }

    private suspend fun handleNotificationTalk() {
        if (!AppPermissions.microphoneGranted(this)) {
            HomeTalkLog.w(LogArea.SERVICE, "Talk from notification blocked: no microphone permission")
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch != null) startActivity(launch)
            return
        }
        refreshListenPath(
            probePeers = false,
            hardRestartUdp = false,
            forceRebuildPlayback = false,
        )
        promoteToForeground(latestSnapshot.copy(isTransmitting = true))
        val granted = talkController.requestTalk()
        HomeTalkLog.i(LogArea.SERVICE, "Notification Talk granted=$granted")
        if (!granted) {
            promoteToForeground(latestSnapshot)
        }
    }

    private suspend fun handleToggleSpeaker() {
        val enabled = !latestSnapshot.speakerEnabled
        settingsRepository.updateSpeakerEnabled(enabled)
        HomeTalkLog.i(LogArea.SERVICE, "Speaker enabled=$enabled from notification")
        promoteToForeground(latestSnapshot.copy(speakerEnabled = enabled))
    }

    private fun promoteToForeground(snapshot: NotificationSnapshot) {
        val notification = notificationManager.buildNotification(snapshot)
        val type = foregroundServiceType()
        try {
            ServiceCompat.startForeground(
                this,
                HomeTalkNotificationManager.NOTIFICATION_ID,
                notification,
                type,
            )
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.SERVICE, "startForeground failed type=$type", error)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    ServiceCompat.startForeground(
                        this,
                        HomeTalkNotificationManager.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    )
                }.onFailure {
                    HomeTalkLog.e(LogArea.SERVICE, "Fallback startForeground failed", it)
                }
            }
        }
        notificationManager.notify(snapshot)
    }

    private fun foregroundServiceType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        val micGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            screenReceiverRegistered = true
        }.onFailure {
            HomeTalkLog.w(LogArea.SERVICE, "Screen receiver register failed", it)
        }
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    private fun stopSelfSafely() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        observeJob?.cancel()
        observeJob = null
        unregisterScreenReceiver()
        powerKeepAlive.release()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        notificationManager.cancel()
        stopSelf()
    }

    companion object {
        fun isEnabledByPhase(): Boolean = PhaseConfig.BACKGROUND_SERVICE_ENABLED
    }
}
