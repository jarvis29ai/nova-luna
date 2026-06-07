package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.screen.ScreenRecoveryAdvisor
import com.nova.luna.screen.ScreenRiskSignal
import com.nova.luna.screen.ScreenState
import com.nova.luna.screen.ScreenStateReader
import com.nova.luna.util.AccessibilityReadiness
import java.util.Locale

class ScreenUnderstandingModel(
    private val screenStateReader: ScreenStateReader = ScreenStateReader(),
    private val recoveryAdvisor: ScreenRecoveryAdvisor = ScreenRecoveryAdvisor(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) : PhoneBrainModel {
    override val role: BrainModelRole = BrainModelRole.SCREEN_UNDERSTANDING
    override val available: Boolean = true

    override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
        val screenState = request.screenState ?: screenStateReader.captureScreenState()
        val candidateAction = buildCandidateAction(request, screenState, routeDecision)

        return BrainModelResult.available(
            role = role,
            candidateAction = candidateAction,
            rawResponse = codec.encode(candidateAction),
            reason = if (screenState == null) {
                "Screen understanding returned a safe accessibility fallback."
            } else {
                "Screen understanding produced a structured local screen summary."
            },
            safetyNotes = routeDecision.safetyNotes + buildSafetyNotes(screenState)
        )
    }

    private fun buildCandidateAction(
        request: BrainRequest,
        screenState: ScreenState?,
        routeDecision: BrainRouteDecision
    ): BrainAction {
        if (screenState == null) {
            val reply = recoveryAdvisor.buildUnavailableMessage(AccessibilityReadiness.isBound())
            return BrainAction(
                intent = "screen_understanding",
                reply = reply,
                actionType = BrainActionType.READ_ONLY,
                riskLevel = BrainRiskLevel.SAFE,
                requiresConfirmation = false,
                finalActionAllowed = false,
                params = buildUnavailableParams(request, routeDecision),
                nextQuestion = if (AccessibilityReadiness.isBound()) {
                    "Please keep the target app open and try again."
                } else {
                    "Please enable accessibility and try again."
                }
            )
        }

        val queryMode = detectQueryMode(request.rawText)
        val summary = buildReply(screenState, queryMode)
        val nextQuestion = recoveryAdvisor.buildNextQuestion(screenState)

        return BrainAction(
            intent = "screen_understanding",
            reply = summary,
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = buildParams(request, screenState, queryMode),
            nextQuestion = nextQuestion
        )
    }

    private fun buildUnavailableParams(
        request: BrainRequest,
        routeDecision: BrainRouteDecision
    ): Map<String, String> {
        return buildMap {
            put("rawText", request.rawText)
            put("screenAvailable", "false")
            put("screenAccessibilityReady", AccessibilityReadiness.isBound().toString())
            put("routeReason", routeDecision.reason)
        }
    }

    private fun buildParams(
        request: BrainRequest,
        screenState: ScreenState,
        queryMode: String
    ): Map<String, String> {
        return buildMap {
            put("rawText", request.rawText)
            put("screenPackageName", screenState.packageName)
            screenState.appName?.takeIf { it.isNotBlank() }?.let { put("screenAppName", it) }
            screenState.className?.takeIf { it.isNotBlank() }?.let { put("screenClassName", it) }
            put("screenSummary", screenState.summarizedState)
            put("screenConfidence", String.format(Locale.US, "%.2f", screenState.confidence))
            put("screenRawNodeCount", screenState.rawNodeCount.toString())
            put("screenTruncated", screenState.truncated.toString())
            put("screenAccessibilityReady", screenState.isAccessibilityReady.toString())
            put("screenVisibleTextCount", screenState.visibleText.size.toString())
            put("screenClickableCount", screenState.clickableElements.size.toString())
            put("screenEditableCount", screenState.editableFields.size.toString())
            put("screenScrollableCount", screenState.scrollableElements.size.toString())
            put("screenSelectedCount", screenState.selectedElements.size.toString())
            put("screenEnabledCount", screenState.enabledElements.size.toString())
            put("screenDisabledCount", screenState.disabledElements.size.toString())
            put("screenQueryMode", queryMode)
            screenState.focusedElement?.primaryLabel()?.takeIf { it.isNotBlank() }?.let { put("screenFocusedElement", it) }
            put("screenRiskSignals", screenState.riskSignals.joinToString(separator = ",") { it.name.lowercase(Locale.US) })
            put("screenErrorMessages", screenState.errorMessages.joinToString(separator = " | "))
            put("screenLoadingSignals", screenState.loadingSignals.joinToString(separator = " | "))
            put("screenPermissionSignals", screenState.permissionSignals.joinToString(separator = " | "))
            put("screenLoginSignals", screenState.loginSignals.joinToString(separator = " | "))
            put("screenPaymentSignals", screenState.paymentSignals.joinToString(separator = " | "))
            put("screenOtpSignals", screenState.otpSignals.joinToString(separator = " | "))
            put("screenPasswordSignals", screenState.passwordSignals.joinToString(separator = " | "))
            put("screenCaptchaSignals", screenState.captchaSignals.joinToString(separator = " | "))
            put("screenBiometricSignals", screenState.biometricSignals.joinToString(separator = " | "))
        }
    }

    private fun buildReply(screenState: ScreenState, queryMode: String): String {
        val baseSummary = when (queryMode) {
            "app" -> buildAppSummary(screenState)
            "buttons" -> buildButtonSummary(screenState)
            "fields" -> buildFieldSummary(screenState)
            "errors" -> buildErrorSummary(screenState)
            "permissions" -> buildPermissionSummary(screenState)
            "risk" -> buildRiskSummary(screenState)
            else -> screenState.summarizedState
        }

        val safetyMessage = recoveryAdvisor.buildRecoveryMessage(screenState)
        return listOf(baseSummary, safetyMessage)
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .trim()
            .take(280)
    }

    private fun buildAppSummary(screenState: ScreenState): String {
        val appLabel = screenState.appName?.takeIf { it.isNotBlank() }
            ?: screenState.packageName

        return if (appLabel.isBlank()) {
            "I can see the current screen."
        } else {
            "You are on $appLabel."
        }
    }

    private fun buildButtonSummary(screenState: ScreenState): String {
        val labels = screenState.possibleButtons
            .mapNotNull { it.primaryLabel() }
            .distinctBy { it.lowercase(Locale.US) }
            .take(5)

        return if (labels.isEmpty()) {
            "I can see the screen, but I do not see any obvious buttons yet."
        } else {
            "I can see these visible buttons: ${labels.joinToString(separator = ", ")}."
        }
    }

    private fun buildFieldSummary(screenState: ScreenState): String {
        val labels = screenState.possibleSearchFields
            .mapNotNull { it.primaryLabel() }
            .distinctBy { it.lowercase(Locale.US) }
            .take(5)

        return if (labels.isEmpty()) {
            "I can see the screen, but I do not see any obvious input fields yet."
        } else {
            "I can see these visible input fields: ${labels.joinToString(separator = ", ")}."
        }
    }

    private fun buildErrorSummary(screenState: ScreenState): String {
        return if (screenState.errorMessages.isEmpty()) {
            "I do not see an obvious error message."
        } else {
            "I see this error message: ${screenState.errorMessages.take(2).joinToString(separator = " | ")}."
        }
    }

    private fun buildPermissionSummary(screenState: ScreenState): String {
        return if (screenState.permissionSignals.isEmpty()) {
            "I do not see an obvious permission prompt."
        } else {
            "I see a permission prompt: ${screenState.permissionSignals.take(2).joinToString(separator = " | ")}."
        }
    }

    private fun buildRiskSummary(screenState: ScreenState): String {
        if (screenState.riskSignals.isEmpty()) {
            return "I do not see any obvious sensitive fields."
        }

        val signalNames = screenState.riskSignals.joinToString(separator = ", ") { signal ->
            when (signal) {
                ScreenRiskSignal.LOGIN -> "login"
                ScreenRiskSignal.PAYMENT -> "payment"
                ScreenRiskSignal.OTP -> "OTP"
                ScreenRiskSignal.PASSWORD -> "password"
                ScreenRiskSignal.PIN -> "PIN"
                ScreenRiskSignal.CVV -> "CVV"
                ScreenRiskSignal.CAPTCHA -> "CAPTCHA"
                ScreenRiskSignal.BIOMETRIC -> "biometric"
                ScreenRiskSignal.PERMISSION -> "permission"
                ScreenRiskSignal.LOADING -> "loading"
                ScreenRiskSignal.ERROR -> "error"
                ScreenRiskSignal.SENSITIVE_FIELD -> "sensitive field"
                ScreenRiskSignal.WRONG_APP -> "wrong app"
            }
        }

        return "I detected ${signalNames.lowercase(Locale.US)} on the screen."
    }

    private fun detectQueryMode(rawText: String): String {
        val normalized = rawText.lowercase(Locale.US)
        return when {
            normalized.contains("what app") ||
                normalized.contains("which app") ||
                normalized.contains("current app") ||
                normalized.contains("foreground app") ||
                normalized.contains("app is open") -> "app"

            normalized.contains("button") ||
                normalized.contains("tap") ||
                normalized.contains("click") ||
                normalized.contains("press") -> "buttons"

            normalized.contains("field") ||
                normalized.contains("input") ||
                normalized.contains("type") ||
                normalized.contains("enter") ||
                normalized.contains("search") -> "fields"

            normalized.contains("error") ||
                normalized.contains("failed") ||
                normalized.contains("problem") ||
                normalized.contains("issue") -> "errors"

            normalized.contains("permission") ||
                normalized.contains("allow") ||
                normalized.contains("deny") -> "permissions"

            normalized.contains("login") ||
                normalized.contains("password") ||
                normalized.contains("otp") ||
                normalized.contains("pin") ||
                normalized.contains("cvv") ||
                normalized.contains("captcha") ||
                normalized.contains("biometric") ||
                normalized.contains("pay") ||
                normalized.contains("checkout") -> "risk"

            else -> "summary"
        }
    }

    private fun buildSafetyNotes(screenState: ScreenState?): List<String> {
        if (screenState == null) {
            return listOf(
                "Screen understanding stays read-only.",
                "It must never execute phone actions directly.",
                "Accessibility capture is the only input source."
            )
        }

        val notes = mutableListOf(
            "Screen understanding stays read-only.",
            "It must never execute phone actions directly.",
            "Accessibility capture is the only input source.",
            "Sensitive fields stay manual or confirmation-gated."
        )

        if (screenState.isSensitiveScreen()) {
            notes.add("Login, payment, OTP, PIN, CVV, CAPTCHA, and biometric flows must remain manual.")
        }

        return notes
    }
}
