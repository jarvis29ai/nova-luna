package com.nova.luna.screen

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import java.util.Locale

enum class ScreenVerificationStatus {
    NOT_APPLICABLE,
    UNAVAILABLE,
    VERIFIED,
    CHANGED,
    UNCHANGED
}

data class ScreenVerificationResult(
    val status: ScreenVerificationStatus,
    val applicable: Boolean,
    val changed: Boolean,
    val verified: Boolean,
    val message: String,
    val beforePackageName: String? = null,
    val afterPackageName: String? = null,
    val beforeClassName: String? = null,
    val afterClassName: String? = null
) {
    fun toEntityMap(): Map<String, String> {
        return buildMap {
            put("screenVerificationStatus", status.name.lowercase(Locale.US))
            put("screenVerificationApplicable", applicable.toString())
            put("screenVerificationChanged", changed.toString())
            put("screenVerificationVerified", verified.toString())
            put("screenVerificationMessage", message)
            beforePackageName?.takeIf { it.isNotBlank() }?.let { put("screenBeforePackage", it) }
            afterPackageName?.takeIf { it.isNotBlank() }?.let { put("screenAfterPackage", it) }
            beforeClassName?.takeIf { it.isNotBlank() }?.let { put("screenBeforeClass", it) }
            afterClassName?.takeIf { it.isNotBlank() }?.let { put("screenAfterClass", it) }
        }
    }

    companion object {
        fun notApplicable(actionType: ActionType): ScreenVerificationResult {
            return ScreenVerificationResult(
                status = ScreenVerificationStatus.NOT_APPLICABLE,
                applicable = false,
                changed = false,
                verified = false,
                message = "Screen verification is not needed for ${actionType.name.lowercase(Locale.US)}."
            )
        }
    }
}

class ScreenStateVerifier(
    private val screenRecoveryAdvisor: ScreenRecoveryAdvisor = ScreenRecoveryAdvisor()
) {
    fun verify(
        before: ScreenState?,
        after: ScreenState?,
        commandIntent: CommandIntent,
        commandResult: CommandResult
    ): ScreenVerificationResult {
        val actionType = commandIntent.actionType
        if (!isRelevant(actionType)) {
            return ScreenVerificationResult.notApplicable(actionType)
        }

        if (!commandResult.success) {
            return ScreenVerificationResult(
                status = ScreenVerificationStatus.UNCHANGED,
                applicable = true,
                changed = false,
                verified = false,
                message = "The action did not succeed, so the screen could not be verified.",
                beforePackageName = before?.packageName,
                afterPackageName = after?.packageName,
                beforeClassName = before?.className,
                afterClassName = after?.className
            )
        }

        if (before == null || after == null) {
            val readinessMessage = when {
                after == null && before == null -> screenRecoveryAdvisor.buildUnavailableMessage(false)
                else -> "I could not capture the screen after the action."
            }
            return ScreenVerificationResult(
                status = ScreenVerificationStatus.UNAVAILABLE,
                applicable = true,
                changed = false,
                verified = false,
                message = readinessMessage,
                beforePackageName = before?.packageName,
                afterPackageName = after?.packageName,
                beforeClassName = before?.className,
                afterClassName = after?.className
            )
        }

        val changed = screenChanged(before, after)
        val status = if (changed) ScreenVerificationStatus.CHANGED else ScreenVerificationStatus.UNCHANGED
        val message = if (changed) {
            "Screen verification passed: the visible screen changed."
        } else {
            "Screen verification could not confirm a visible screen change yet."
        }

        return ScreenVerificationResult(
            status = status,
            applicable = true,
            changed = changed,
            verified = changed,
            message = message,
            beforePackageName = before.packageName,
            afterPackageName = after.packageName,
            beforeClassName = before.className,
            afterClassName = after.className
        )
    }

    private fun isRelevant(actionType: ActionType): Boolean {
        return actionType in setOf(
            ActionType.LAUNCH_APP,
            ActionType.GO_HOME,
            ActionType.GO_BACK,
            ActionType.OPEN_RECENTS,
            ActionType.OPEN_NOTIFICATIONS,
            ActionType.CLICK_TEXT,
            ActionType.SCROLL_FORWARD,
            ActionType.SCROLL_BACKWARD,
            ActionType.TYPE_TEXT,
            ActionType.OPEN_SETTINGS,
            ActionType.OPEN_ACCESSIBILITY_SETTINGS,
            ActionType.OPEN_USAGE_ACCESS_SETTINGS
        )
    }

    private fun screenChanged(before: ScreenState, after: ScreenState): Boolean {
        if (before.packageName != after.packageName) return true
        if (before.className != after.className) return true
        if (before.signature() != after.signature()) return true
        return false
    }
}
