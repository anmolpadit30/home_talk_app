package com.hometalk.app.domain.repository

import com.hometalk.app.data.network.ControlMessage
import com.hometalk.app.domain.model.ChannelState
import kotlinx.coroutines.flow.StateFlow

interface TalkController {
    val channelState: StateFlow<ChannelState>
    val isTransmitting: StateFlow<Boolean>
    /** Non-null while a private (direct) transmission is active. */
    val talkingToDeviceId: StateFlow<String?>
    suspend fun start()
    suspend fun stop()
    /**
     * Wake AudioTrack / locks after OEM deep-sleep.
     * @param hardRestartUdp rebind the UDP socket (screen-off keepalive / screen-on).
     * @param forceRebuildPlayback recreate AudioTrack even if it still reports playing.
     */
    suspend fun refreshListening(
        hardRestartUdp: Boolean = true,
        forceRebuildPlayback: Boolean = false,
    )
    /**
     * Start push-to-talk.
     * @param targetDeviceId when set, audio/control go only to that paired peer.
     */
    suspend fun requestTalk(targetDeviceId: String? = null): Boolean
    suspend fun releaseTalk()
    suspend fun handleControlMessage(remoteHost: String, message: ControlMessage): ControlMessage?
}
