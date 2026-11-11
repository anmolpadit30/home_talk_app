package com.hometalk.app.service

/**
 * Controls the foreground WalkieTalkieService.
 * Notification Talk is tap-to-start / tap-to-stop, not press-and-hold.
 */
interface ServiceController {
    fun startService()
    fun stopService()
    fun startTalk()
    fun stopTalk()
}
