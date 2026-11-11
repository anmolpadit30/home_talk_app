package com.hometalk.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.hometalk.app.audio.AudioEngine
import com.hometalk.app.audio.AudioEngineImpl
import com.hometalk.app.audio.TalkSessionManager
import com.hometalk.app.data.discovery.DeviceDiscovery
import com.hometalk.app.data.discovery.DiscoveryCoordinator
import com.hometalk.app.data.discovery.NsdDeviceDiscovery
import com.hometalk.app.data.preferences.homeTalkDataStore
import com.hometalk.app.data.repository.DeviceRegistryRepositoryImpl
import com.hometalk.app.data.repository.PairedDevicesRepositoryImpl
import com.hometalk.app.data.repository.SettingsRepositoryImpl
import com.hometalk.app.data.repository.WifiConnectionRepository
import com.hometalk.app.domain.repository.ConnectionRepository
import com.hometalk.app.domain.repository.DeviceRegistryRepository
import com.hometalk.app.domain.repository.DiscoveryController
import com.hometalk.app.domain.repository.PairedDevicesRepository
import com.hometalk.app.domain.repository.SettingsRepository
import com.hometalk.app.domain.repository.TalkController
import com.hometalk.app.service.ServiceController
import com.hometalk.app.service.ServiceControllerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.homeTalkDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRegistryRepository(
        impl: DeviceRegistryRepositoryImpl,
    ): DeviceRegistryRepository

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(impl: WifiConnectionRepository): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindPairedDevicesRepository(
        impl: PairedDevicesRepositoryImpl,
    ): PairedDevicesRepository

    @Binds
    @Singleton
    abstract fun bindDiscoveryController(impl: DiscoveryCoordinator): DiscoveryController

    @Binds
    @Singleton
    abstract fun bindDeviceDiscovery(impl: NsdDeviceDiscovery): DeviceDiscovery

    @Binds
    @Singleton
    abstract fun bindTalkController(impl: TalkSessionManager): TalkController

    @Binds
    @Singleton
    abstract fun bindAudioEngine(impl: AudioEngineImpl): AudioEngine

    @Binds
    @Singleton
    abstract fun bindServiceController(impl: ServiceControllerImpl): ServiceController
}
