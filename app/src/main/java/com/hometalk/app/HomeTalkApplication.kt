package com.hometalk.app

import android.app.Application
import com.hometalk.app.domain.model.PhaseConfig
import com.hometalk.app.domain.repository.DiscoveryController
import com.hometalk.app.service.WifiSleepCoordinator
import com.hometalk.app.util.HomeTalkLog
import com.hometalk.app.util.LogArea
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HomeTalkApplication : Application() {
    @Inject
    lateinit var discoveryController: DiscoveryController

    @Inject
    lateinit var wifiSleepCoordinator: WifiSleepCoordinator

    override fun onCreate() {
        super.onCreate()
        HomeTalkLog.i(LogArea.APP, "HomeTalk started phase=${PhaseConfig.CURRENT_PHASE}")
        discoveryController.start()
        wifiSleepCoordinator.start()
    }
}
