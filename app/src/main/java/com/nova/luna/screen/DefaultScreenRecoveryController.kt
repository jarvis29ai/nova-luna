package com.nova.luna.screen

class DefaultScreenRecoveryController : ScreenRecoveryController {
    private var retryCount = 0
    private val maxRetries = 2

    override fun recover(snapshot: ScreenSnapshot, lastStep: ScreenStep, error: String): RecoveryStrategy {
        if (snapshot.riskSignals.isNotEmpty()) {
            retryCount = 0
            return RecoveryStrategy.FAIL_SAFE
        }

        if (retryCount >= maxRetries) {
            retryCount = 0
            return RecoveryStrategy.ASK_USER_MANUAL_STEP
        }

        retryCount++
        return when (error) {
            "ELEMENT_NOT_FOUND" -> {
                if (lastStep.action == ScreenAction.CLICK || lastStep.action == ScreenAction.TYPE_TEXT) {
                    RecoveryStrategy.SCROLL_AND_RETRY
                } else {
                    RecoveryStrategy.WAIT_AND_RESNAPSHOT
                }
            }
            "SCREEN_NOT_UNDERSTOOD" -> RecoveryStrategy.WAIT_AND_RESNAPSHOT
            else -> RecoveryStrategy.FAIL_SAFE
        }
    }
}
