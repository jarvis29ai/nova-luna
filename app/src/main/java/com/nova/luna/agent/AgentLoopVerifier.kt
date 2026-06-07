package com.nova.luna.agent

import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.screen.ScreenState
import com.nova.luna.screen.ScreenStateVerifier
import com.nova.luna.screen.ScreenVerificationResult

data class AgentLoopVerification(
    val screenVerification: ScreenVerificationResult,
    val progressObserved: Boolean,
    val verified: Boolean,
    val message: String
)

class AgentLoopVerifier(
    private val screenStateVerifier: ScreenStateVerifier = ScreenStateVerifier()
) {
    fun verify(
        before: ScreenState?,
        after: ScreenState?,
        commandIntent: CommandIntent,
        commandResult: CommandResult
    ): AgentLoopVerification {
        val screenVerification = screenStateVerifier.verify(
            before = before,
            after = after,
            commandIntent = commandIntent,
            commandResult = commandResult
        )
        val progressObserved = screenVerification.verified ||
            screenVerification.changed ||
            commandResult.success

        return AgentLoopVerification(
            screenVerification = screenVerification,
            progressObserved = progressObserved,
            verified = screenVerification.verified,
            message = screenVerification.message
        )
    }
}
