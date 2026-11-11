package com.hometalk.app.domain.repository

import com.hometalk.app.data.network.ControlMessage
import com.hometalk.app.domain.model.ChannelState
import kotlinx.coroutines.flow.StateFlow

interface TalkController {
    val channelState: StateFlow<ChannelState>
    val isTransmitting: StateFlow<Boolean>
    fun start()
    fun stop()
    suspend fun requestTalk(): Boolean
    suspend fun releaseTalk()
    suspend fun handleControlMessage(remoteHost: String, message: ControlMessage): ControlMessage?
}
