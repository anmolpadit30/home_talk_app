package com.hometalk.app.domain.model

object ResolveTalkButtonState {
    operator fun invoke(
        wifiConnected: Boolean,
        hasOnlinePairedPeer: Boolean,
        isTransmitting: Boolean,
        isChannelBusy: Boolean,
        pttUiPreview: Boolean = false,
    ): TalkButtonState {
        if (!wifiConnected) return TalkButtonState.Disconnected
        val channelReady = hasOnlinePairedPeer || pttUiPreview
        if (!channelReady) return TalkButtonState.Disconnected
        if (isTransmitting) return TalkButtonState.Transmitting
        // Kept for tests / legacy; duplex UI never passes true.
        if (isChannelBusy) return TalkButtonState.Busy
        return TalkButtonState.Idle
    }
}
