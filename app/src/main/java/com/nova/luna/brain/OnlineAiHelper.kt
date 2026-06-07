package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision

class OnlineAiHelper(
    private val config: OnlineAiConfig = OnlineAiConfig.fromBuildConfig(),
    private val provider: OnlineAiProvider = OnlineAiProviderFactory.create(config),
    private val networkStatusProvider: NetworkStatusProvider = StaticNetworkStatusProvider(false),
    private val policy: OnlineAiPolicy = OnlineAiPolicy(),
    private val privacyFilter: OnlineAiPrivacyFilter = OnlineAiPrivacyFilter(),
    private val promptBuilder: OnlineAiPromptBuilder = OnlineAiPromptBuilder(),
    private val sanitizer: OnlineAiOutputSanitizer = OnlineAiOutputSanitizer(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) : PhoneBrainModel {
    override val role: BrainModelRole = BrainModelRole.ONLINE_AI_HELPER

    override val available: Boolean
        get() {
            val sanitizedConfig = config.sanitized()
            return sanitizedConfig.enabled &&
                provider.available &&
                sanitizedConfig.providerType != OnlineAiProviderType.UNAVAILABLE &&
                networkStatusProvider.isInternetAvailable()
        }

    override fun generate(request: BrainRequest, routeDecision: BrainRouteDecision): BrainModelResult {
        val sanitizedConfig = config.sanitized()
        val networkAvailable = networkStatusProvider.isInternetAvailable()
        val policyResult = policy.evaluate(
            request = request,
            config = sanitizedConfig,
            networkStatusProvider = networkStatusProvider,
            userConsentGiven = request.onlineConsentGiven
        )

        val providerName = provider.diagnostics().ifBlank { provider.providerType.wireValue }
        val providerType = provider.providerType
        val privacyResult = privacyFilter.filter(request, sanitizedConfig)

        if (policyResult.decision == OnlineAiPolicyDecision.ASK_USER_PERMISSION && !provider.available) {
            val reason = "Online provider ${provider.providerType.wireValue} is unavailable."
            return unavailableResult(
                reason = reason,
                safetyNotes = routeDecision.safetyNotes + onlineSafetyNotes(),
                trace = OnlineAiTrace.failed(
                    providerType = providerType,
                    providerName = providerName,
                    decision = policyResult.decision,
                    reason = reason,
                    networkAvailable = networkAvailable,
                    redactionCount = policyResult.redactionCount,
                    userConsentGiven = request.onlineConsentGiven,
                    promptBuilt = false
                )
            )
        }

        when (policyResult.decision) {
            OnlineAiPolicyDecision.ASK_USER_PERMISSION -> {
                val action = buildPermissionAction(
                    request = request,
                    routeDecision = routeDecision,
                    providerName = providerName,
                    policyResult = policyResult
                )
                val trace = OnlineAiTrace.askPermission(
                    providerType = providerType,
                    providerName = providerName,
                    reason = policyResult.reason,
                    networkAvailable = networkAvailable,
                    userConsentGiven = request.onlineConsentGiven
                )
                return BrainModelResult.available(
                    role = role,
                    candidateAction = action,
                    rawResponse = codec.encode(action),
                    reason = policyResult.reason,
                    safetyNotes = routeDecision.safetyNotes + onlineSafetyNotes(),
                    promptBuilt = false,
                    jsonParsed = true,
                    onlineTrace = trace
                )
            }

            OnlineAiPolicyDecision.FALLBACK_LOCAL -> {
                return unavailableResult(
                    reason = policyResult.reason,
                    safetyNotes = routeDecision.safetyNotes + onlineSafetyNotes(),
                    trace = OnlineAiTrace.skipped(
                        providerType = providerType,
                        providerName = providerName,
                        reason = policyResult.reason,
                        networkAvailable = networkAvailable,
                        userConsentGiven = request.onlineConsentGiven
                    )
                )
            }

            OnlineAiPolicyDecision.DENY_USER_DISABLED,
            OnlineAiPolicyDecision.DENY_NO_INTERNET,
            OnlineAiPolicyDecision.DENY_PRIVACY,
            OnlineAiPolicyDecision.DENY_SENSITIVE,
            OnlineAiPolicyDecision.DENY_TASK_NOT_NEEDED -> {
                return unavailableResult(
                    reason = policyResult.reason,
                    safetyNotes = routeDecision.safetyNotes + onlineSafetyNotes(),
                    trace = OnlineAiTrace.blocked(
                        providerType = providerType,
                        providerName = providerName,
                        decision = policyResult.decision,
                        reason = policyResult.reason,
                        networkAvailable = networkAvailable,
                        redactionCount = policyResult.redactionCount,
                        userConsentGiven = request.onlineConsentGiven,
                        privacyBlocked = policyResult.privacyBlocked
                    )
                )
            }

            OnlineAiPolicyDecision.ALLOW -> Unit
        }

        if (!provider.available) {
            val reason = "Online provider ${provider.providerType.wireValue} is unavailable."
            return unavailableResult(
                reason = reason,
                safetyNotes = routeDecision.safetyNotes + onlineSafetyNotes(),
                trace = OnlineAiTrace.failed(
                    providerType = providerType,
                    providerName = providerName,
                    decision = policyResult.decision,
                    reason = reason,
                    networkAvailable = networkAvailable,
                    redactionCount = policyResult.redactionCount,
                    userConsentGiven = request.onlineConsentGiven,
                    promptBuilt = false
                )
            )
        }

        val prompt = promptBuilder.buildBrainActionPrompt(
            request = request,
            routeDecision = routeDecision,
            config = sanitizedConfig,
            policyResult = policyResult,
            privacyResult = privacyResult
        )

        val providerResult = runCatching {
            provider.generate(prompt, sanitizedConfig.timeoutMs)
        }.getOrElse { error ->
            OnlineAiResult(
                providerType = providerType,
                status = OnlineAiStatus.FAILED,
                available = false,
                rawResponse = null,
                reason = "Online provider failed: ${error.message.orEmpty()}",
                redactionCount = policyResult.redactionCount,
                promptBuilt = true,
                policyDecision = policyResult.decision,
                latencyMillis = null,
                providerName = providerName
            )
        }

        val promptBuilt = providerResult.promptBuilt || prompt.isNotBlank()
        val jsonParsed = looksLikeJsonResponse(providerResult.rawResponse)
        val sanitizedResult = sanitizer.sanitize(
            providerResult = providerResult.copy(promptBuilt = promptBuilt),
            request = request,
            routeDecision = routeDecision,
            policyResult = policyResult,
            userConsentGiven = request.onlineConsentGiven,
            networkAvailable = networkAvailable
        )

        val trace = sanitizedResult.trace ?: OnlineAiTrace.failed(
            providerType = providerType,
            providerName = providerName,
            decision = policyResult.decision,
            reason = sanitizedResult.reason,
            networkAvailable = networkAvailable,
            redactionCount = sanitizedResult.redactionCount,
            userConsentGiven = request.onlineConsentGiven,
            promptBuilt = promptBuilt
        )

        if (sanitizedResult.available && sanitizedResult.candidateAction != null) {
            return BrainModelResult.available(
                role = role,
                candidateAction = sanitizedResult.candidateAction,
                rawResponse = sanitizedResult.rawResponse ?: codec.encode(sanitizedResult.candidateAction),
                reason = sanitizedResult.reason,
                safetyNotes = routeDecision.safetyNotes + onlineSafetyNotes(),
                promptBuilt = sanitizedResult.promptBuilt,
                jsonParsed = jsonParsed,
                latencyMillis = sanitizedResult.latencyMillis,
                onlineTrace = trace
            )
        }

        return BrainModelResult.unavailable(
            role = role,
            reason = sanitizedResult.reason,
            safetyNotes = routeDecision.safetyNotes + onlineSafetyNotes(),
            rawResponse = sanitizedResult.rawResponse,
            promptBuilt = sanitizedResult.promptBuilt,
            jsonParsed = jsonParsed,
            latencyMillis = sanitizedResult.latencyMillis,
            onlineTrace = trace
        )
    }

    private fun buildPermissionAction(
        request: BrainRequest,
        routeDecision: BrainRouteDecision,
        providerName: String,
        policyResult: OnlineAiPolicyResult
    ): BrainAction {
        return BrainAction(
            intent = "online_ai_permission",
            reply = "Say yes and I can use online AI to research or draft this safely.",
            actionType = BrainActionType.PREPARE,
            riskLevel = BrainRiskLevel.CONFIRMATION_REQUIRED,
            requiresConfirmation = true,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to request.rawText,
                "activeCabSession" to request.activeCabSession.toString(),
                "activeGrocerySession" to request.activeGrocerySession.toString(),
                "activeFoodSession" to request.activeFoodSession.toString(),
                "provider" to providerName,
                "routeRole" to routeDecision.selectedRole.wireValue,
                "policyDecision" to policyResult.decision.wireValue,
                "redactionCount" to policyResult.redactionCount.toString(),
                "sanitizedText" to policyResult.sanitizedText.take(2_000),
                "onlineConsentGiven" to request.onlineConsentGiven.toString()
            ),
            nextQuestion = "Say yes to use online AI, or I will keep it local."
        )
    }

    private fun unavailableResult(
        reason: String,
        safetyNotes: List<String>,
        trace: OnlineAiTrace
    ): BrainModelResult {
        return BrainModelResult.unavailable(
            role = role,
            reason = reason,
            safetyNotes = safetyNotes,
            onlineTrace = trace
        )
    }

    private fun onlineSafetyNotes(): List<String> {
        return listOf(
            "Online helper only produces research, drafts, summaries, or safe suggestions.",
            "It never controls the phone directly.",
            "BrainActionValidator and SafetyGate still decide what is safe to use."
        )
    }

    private fun looksLikeJsonResponse(rawResponse: String?): Boolean {
        val value = rawResponse?.trim().orEmpty()
        if (value.isBlank()) return false

        val directDecode = codec.decode(value)
        if (directDecode != null) return true

        val fenced = stripFence(value)
        return fenced != null && codec.decode(fenced) != null
    }

    private fun stripFence(value: String): String? {
        val fencedRegex = Regex(
            pattern = "^```(?:json)?\\s*([\\s\\S]*?)\\s*```$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val match = fencedRegex.matchEntire(value.trim()) ?: return null
        return match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }
}
