package com.hometalk.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hometalk.app.di.ApplicationScope
import com.hometalk.app.di.IoDispatcher
import com.hometalk.app.domain.model.PhaseConfig
import com.hometalk.app.domain.repository.SettingsRepository
import com.hometalk.app.domain.repository.TalkController
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val talkController: TalkController,
    private val settingsRepository: SettingsRepository,
) : ServiceController {

    override fun startService() {
        if (!PhaseConfig.BACKGROUND_SERVICE_ENABLED) {
            HomeTalkLog.i(LogArea.SERVICE, "Background service disabled by PhaseConfig")
            return
        }
        appScope.launch(ioDispatcher) {
            val settings = settingsRepository.settings.first()
            if (!settings.keepServiceInBackground) {
                HomeTalkLog.i(LogArea.SERVICE, "User disabled keep-in-background")
                return@launch
            }
            val intent = Intent(context, WalkieTalkieService::class.java).apply {
                action = ServiceActions.ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
                HomeTalkLog.i(LogArea.SERVICE, "Requested WalkieTalkieService start")
            } catch (error: Exception) {
                HomeTalkLog.e(LogArea.SERVICE, "Failed to start WalkieTalkieService", error)
            }
        }
    }

    override fun stopService() {
        val intent = Intent(context, WalkieTalkieService::class.java).apply {
            action = ServiceActions.ACTION_STOP
        }
        try {
            // Prefer explicit stop command; also stopService as fallback.
            context.startService(intent)
            context.stopService(Intent(context, WalkieTalkieService::class.java))
            HomeTalkLog.i(LogArea.SERVICE, "Requested WalkieTalkieService stop")
        } catch (error: Exception) {
            HomeTalkLog.e(LogArea.SERVICE, "Failed to stop WalkieTalkieService", error)
        }
    }

    override fun startTalk() {
        appScope.launch(ioDispatcher) {
            val granted = talkController.requestTalk()
            HomeTalkLog.i(LogArea.SERVICE, "ServiceController startTalk granted=$granted")
        }
    }

    override fun stopTalk() {
        appScope.launch(ioDispatcher) {
            talkController.releaseTalk()
            HomeTalkLog.i(LogArea.SERVICE, "ServiceController stopTalk")
        }
    }
}
