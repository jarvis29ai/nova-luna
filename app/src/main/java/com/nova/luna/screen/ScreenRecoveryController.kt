package com.nova.luna.screen

interface ScreenRecoveryController {
    fun recover(snapshot: ScreenSnapshot, lastStep: ScreenStep, error: String): RecoveryStrategy
}
