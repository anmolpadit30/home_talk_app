package com.hometalk.app.domain.repository

import com.hometalk.app.domain.model.WifiStatus
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    val wifiStatus: Flow<WifiStatus>
}
