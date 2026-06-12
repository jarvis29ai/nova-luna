package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.SafetyStatus
import com.nova.luna.safety.SafetyGate
import java.util.Locale

class OnlineAiOutputSanitizer(
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec(),
    private val validator: BrainActionValidator = BrainActionValidator(),
    private val safetyGate: SafetyGate = SafetyGate()
) {
    fun sanitize(
        providerResult: OnlineAiResult,
        request: BrainRequest,
        routeDecision: com.nova.luna.model.BrainRouteDecision,
        policyResult: OnlineAiPolicyResult,
        userConsentGiven: Boolean,
        networkAvailable: Boolean = true
    ): OnlineAiResult {
        val rawResponse = providerResult.rawResponse?.trim().orEmpty()
        if (rawResponse.isBlank()) {
            return providerResult.copy(
                status = OnlineAiStatus.FAILED,
                available = false,
                reason = "Online provider returned an empty response.",
                trace = providerResult.trace ?: buildTrace(
                    providerResult = providerResult,
                    policyResult = policyResult,
                    routeDecision = routeDecision,
                    status = OnlineAiStatus.FAILED,
                    reason = "Online provider returned an empty response.",
                    promptBuilt = providerResult.promptBuilt,
                    providerSucceeded = false,
                    sanitizerSucceeded = false,
                    userConsentGiven = userConsentGiven,
                    networkAvailable = networkAvailable,
                    latencyMillis = providerResult.latencyMillis
                )
            )
        }

        if (containsUnsafeClaim(rawResponse)) {
            return rejected(providerResult, policyResult, routeDecision, userConsentGiven, networkAvailable, "Unsafe completion claims are not allowed.")
        }

        val candidateAction = decodeCandidateAction(rawResponse)
            ?: buildPlainTextCandidate(request, routeDecision, policyResult, rawResponse)
        val safeCandidateAction = normalizeCandidateAction(candidateAction)
            ?: return rejected(
                providerResult,
                policyResult,
                routeDecision,
                userConsentGiven,
                networkAvailable,
                "Online helper output must stay read-only or draft-only."
            )

        if (!validator.isAcceptable(safeCandidateAction)) {
            return rejected(providerResult, policyResult, routeDecision, userConsentGiven, networkAvailable, "Online helper output was rejected by BrainActionValidator.")
        }

        val safetyDecision = safetyGate.evaluate(
            action = safeCandidateAction,
            originalUserText = request.rawText,
            userConfirmed = userConsentGiven
        )
        if (safetyDecision.status == SafetyStatus.BLOCKED) {
            return rejected(providerResult, policyResult, routeDecision, userConsentGiven, networkAvailable, safetyDecision.reason)
        }

        val trace = buildTrace(
            providerResult = providerResult,
            policyResult = policyResult,
            routeDecision = routeDecision,
            status = OnlineAiStatus.SANITIZED,
            reason = "Online output sanitized and validated.",
            promptBuilt = true,
            providerSucceeded = true,
            sanitizerSucceeded = true,
            userConsentGiven = userConsentGiven,
            networkAvailable = networkAvailable,
            latencyMillis = providerResult.latencyMillis
        )

        return providerResult.copy(
            status = OnlineAiStatus.SANITIZED,
            available = true,
            sanitizedText = safeCandidateAction.reply,
            candidateAction = safeCandidateAction,
            reason = "Online output sanitized and validated.",
            redactionCount = policyResult.redactionCount,
            promptBuilt = true,
            policyDecision = policyResult.decision,
            trace = trace,
            latencyMillis = providerResult.latencyMillis
        )
    }

    private fun rejected(
        providerResult: OnlineAiResult,
        policyResult: OnlineAiPolicyResult,
        routeDecision: com.nova.luna.model.BrainRouteDecision,
        userConsentGiven: Boolean,
        networkAvailable: Boolean,
        reason: String
    ): OnlineAiResult {
        return providerResult.copy(
            status = OnlineAiStatus.REJECTED,
            available = false,
            candidateAction = null,
            sanitizedText = null,
            reason = reason,
            redactionCount = policyResult.redactionCount,
            policyDecision = policyResult.decision,
            trace = buildTrace(
                providerResult = providerResult,
                policyResult = policyResult,
                routeDecision = routeDecision,
                status = OnlineAiStatus.REJECTED,
                reason = reason,
                promptBuilt = providerResult.promptBuilt,
                providerSucceeded = providerResult.rawResponse != null,
                sanitizerSucceeded = false,
                userConsentGiven = userConsentGiven,
                networkAvailable = networkAvailable,
                latencyMillis = providerResult.latencyMillis
            ),
            latencyMillis = providerResult.latencyMillis
        )
    }

    private fun buildPlainTextCandidate(
        request: BrainRequest,
        routeDecision: com.nova.luna.model.BrainRouteDecision,
        policyResult: OnlineAiPolicyResult,
        rawResponse: String
    ): BrainAction {
        val intent = when {
            looksLikeComparisonRequest(request.rawText) -> "online_research_comparison"
            looksLikeTranslationRequest(request.rawText) -> "online_translation"
            looksLikeSummaryRequest(request.rawText) -> "online_summary"
            looksLikeContentDraftRequest(request.rawText) -> "online_content_draft"
            else -> "online_helper"
        }

        val reply = rawResponse
            .replace(Regex("\\s+"), " ")
            .trim()

        val text = if (reply.isBlank()) {
            "I can prepare a safe draft locally."
        } else {
            reply.take(6_000)
        }

        return BrainAction(
            intent = intent,
            reply = text,
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = buildMap {
                put("rawText", request.rawText)
                put("routeRole", routeDecision.selectedRole.wireValue)
                put("policyDecision", policyResult.decision.wireValue)
                put("redactionCount", policyResult.redactionCount.toString())
            },
            nextQuestion = "Would you like me to turn this into a local draft?"
        )
    }

    private fun decodeCandidateAction(rawResponse: String): BrainAction? {
        stripFence(rawResponse)?.let { fenced ->
            codec.decode(fenced)?.let { return it }
        }

        if (looksLikeJson(rawResponse)) {
            return codec.decode(rawResponse)
        }

        return null
    }

    private fun stripFence(value: String): String? {
        val fenced = markdownFenceRegex.matchEntire(value.trim()) ?: return null
        val body = fenced.groupValues.getOrNull(1)?.trim().orEmpty()
        return body.takeIf { it.isNotBlank() }
    }

    private fun looksLikeJson(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("{") &&
            trimmed.contains("\"intent\"") &&
            trimmed.contains("\"reply\"") &&
            trimmed.contains("\"actionType\"") &&
            trimmed.contains("\"riskLevel\"")
    }

    private fun containsUnsafeClaim(text: String): Boolean {
        val normalized = text.lowercase(Locale.US)
        val unsafePatterns = listOf(
            "i paid",
            "i booked",
            "i sent it",
            "i sent the message",
            "i sent the email",
            "i completed the payment",
            "payment completed",
            "payment done",
            "booked successfully",
            "message sent",
            "email sent",
            "entered otp",
            "enter otp",
            "enter pin",
            "enter password",
            "enter cvv",
            "captcha solved"
        )

        return unsafePatterns.any { pattern -> normalized.contains(pattern) }
    }

    private fun looksLikeComparisonRequest(rawText: String): Boolean {
        val normalized = rawText.lowercase(Locale.US)
        return normalized.contains("compare") ||
            normalized.contains("best") ||
            normalized.contains("price") ||
            normalized.contains("phone") ||
            normalized.contains("laptop") ||
            normalized.contains("washing machine")
    }

    private fun looksLikeTranslationRequest(rawText: String): Boolean {
        val normalized = rawText.lowercase(Locale.US)
        return normalized.contains("translate")
    }

    private fun looksLikeSummaryRequest(rawText: String): Boolean {
        val normalized = rawText.lowercase(Locale.US)
        return normalized.contains("summarize") ||
            normalized.contains("summary") ||
            normalized.contains("article") ||
            normalized.contains("news")
    }

    private fun looksLikeContentDraftRequest(rawText: String): Boolean {
        val normalized = rawText.lowercase(Locale.US)
        return normalized.contains("blog") ||
            normalized.contains("email") ||
            normalized.contains("ppt") ||
            normalized.contains("presentation") ||
            normalized.contains("prompt") ||
            normalized.contains("draft")
    }

    private fun normalizeCandidateAction(candidateAction: BrainAction): BrainAction? {
        if (candidateAction.actionType == BrainActionType.EXTERNAL_ACTION) {
            return null
        }

        return candidateAction.copy(
            finalActionAllowed = false
        )
    }

    private fun buildTrace(
        providerResult: OnlineAiResult,
        policyResult: OnlineAiPolicyResult,
        routeDecision: com.nova.luna.model.BrainRouteDecision,
        status: OnlineAiStatus,
        reason: String,
        promptBuilt: Boolean,
        providerSucceeded: Boolean,
        sanitizerSucceeded: Boolean,
        userConsentGiven: Boolean,
        networkAvailable: Boolean,
        latencyMillis: Long?
    ): OnlineAiTrace {
        return OnlineAiTrace(
            providerType = providerResult.providerType,
            providerName = providerResult.providerName ?: providerResult.providerType.name,
            policyDecision = policyResult.decision,
            status = status,
            used = status == OnlineAiStatus.SANITIZED,
            skipped = status == OnlineAiStatus.SKIPPED,
            blocked = status == OnlineAiStatus.BLOCKED_NO_INTERNET ||
                status == OnlineAiStatus.BLOCKED_PRIVACY ||
                status == OnlineAiStatus.BLOCKED_SENSITIVE ||
                status == OnlineAiStatus.BLOCKED_TASK_NOT_NEEDED ||
                status == OnlineAiStatus.BLOCKED_USER_DISABLED,
            failed = status == OnlineAiStatus.FAILED || status == OnlineAiStatus.REJECTED,
            fallbackUsed = status != OnlineAiStatus.SANITIZED,
            redactionCount = policyResult.redactionCount,
            promptBuilt = promptBuilt,
            providerSucceeded = providerSucceeded,
            sanitizerSucceeded = sanitizerSucceeded,
            reason = reason,
            userConsentGiven = userConsentGiven,
            networkAvailable = networkAvailable,
            privacyBlocked = policyResult.privacyBlocked,
            latencyMillis = latencyMillis
        )
    }

    private companion object {
        val markdownFenceRegex = Regex(
            pattern = "^```(?:json)?\\s*([\\s\\S]*?)\\s*```$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    }
}
