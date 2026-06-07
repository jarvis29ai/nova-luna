package com.nova.luna.screen

import java.util.Locale

class ScreenStateAnalyzer(
    private val recoveryAdvisor: ScreenRecoveryAdvisor = ScreenRecoveryAdvisor()
) {
    fun analyze(
        packageName: String,
        appName: String?,
        className: String?,
        timestampMillis: Long,
        isAccessibilityReady: Boolean,
        nodes: List<ScreenNode>,
        rawNodeCount: Int,
        truncated: Boolean
    ): ScreenState {
        val orderedVisibleText = distinctText(nodes.mapNotNull { it.text })
        val orderedContentDescriptions = distinctText(nodes.mapNotNull { it.contentDescription })
        val clickableElements = nodes.filter { it.isClickable }
        val editableFields = nodes.filter { it.isEditable }
        val scrollableElements = nodes.filter { it.isScrollable }
        val selectedElements = nodes.filter { it.isSelected }
        val focusedElement = nodes.firstOrNull { it.isFocused }
        val enabledElements = nodes.filter { it.isEnabled }
        val disabledElements = nodes.filterNot { it.isEnabled }
        val possibleButtons = nodes.filter { it.semanticRole == ScreenElementType.BUTTON || looksLikeButton(it) }
        val possibleSearchFields = nodes.filter { it.semanticRole == ScreenElementType.SEARCH_FIELD || looksLikeSearchField(it) }
        val possibleListsOrCards = nodes.filter {
            it.semanticRole == ScreenElementType.LIST ||
                it.semanticRole == ScreenElementType.CARD ||
                it.isScrollable ||
                looksLikeListOrCard(it)
        }

        val errorMessages = collectLabels(nodes, setOf(ScreenRiskSignal.ERROR)) { hasAnyKeyword(it, ERROR_KEYWORDS) }
        val loadingSignals = collectLabels(nodes, setOf(ScreenRiskSignal.LOADING)) { hasAnyKeyword(it, LOADING_KEYWORDS) }
        val permissionSignals = collectLabels(nodes, setOf(ScreenRiskSignal.PERMISSION)) { hasAnyKeyword(it, PERMISSION_KEYWORDS) }
        val loginSignals = collectLabels(nodes, setOf(ScreenRiskSignal.LOGIN)) { hasAnyKeyword(it, LOGIN_KEYWORDS) }
        val paymentSignals = collectLabels(nodes, setOf(ScreenRiskSignal.PAYMENT)) { hasAnyKeyword(it, PAYMENT_KEYWORDS) }
        val otpSignals = collectLabels(nodes, setOf(ScreenRiskSignal.OTP)) { hasAnyKeyword(it, OTP_KEYWORDS) }
        val passwordSignals = collectLabels(nodes, setOf(ScreenRiskSignal.PASSWORD, ScreenRiskSignal.PIN, ScreenRiskSignal.CVV)) {
            hasAnyKeyword(it, PASSWORD_KEYWORDS) || hasAnyKeyword(it, PIN_KEYWORDS) || hasAnyKeyword(it, CVV_KEYWORDS)
        }
        val captchaSignals = collectLabels(nodes, setOf(ScreenRiskSignal.CAPTCHA)) { hasAnyKeyword(it, CAPTCHA_KEYWORDS) }
        val biometricSignals = collectLabels(nodes, setOf(ScreenRiskSignal.BIOMETRIC)) { hasAnyKeyword(it, BIOMETRIC_KEYWORDS) }

        val riskSignals = buildList {
            if (loginSignals.isNotEmpty()) add(ScreenRiskSignal.LOGIN)
            if (paymentSignals.isNotEmpty()) add(ScreenRiskSignal.PAYMENT)
            if (otpSignals.isNotEmpty()) add(ScreenRiskSignal.OTP)
            if (passwordSignals.isNotEmpty()) add(ScreenRiskSignal.PASSWORD)
            if (containsPinSignal(nodes)) add(ScreenRiskSignal.PIN)
            if (containsCvvSignal(nodes)) add(ScreenRiskSignal.CVV)
            if (captchaSignals.isNotEmpty()) add(ScreenRiskSignal.CAPTCHA)
            if (biometricSignals.isNotEmpty()) add(ScreenRiskSignal.BIOMETRIC)
            if (permissionSignals.isNotEmpty()) add(ScreenRiskSignal.PERMISSION)
            if (loadingSignals.isNotEmpty()) add(ScreenRiskSignal.LOADING)
            if (errorMessages.isNotEmpty()) add(ScreenRiskSignal.ERROR)
            if (containsSensitiveField(nodes)) add(ScreenRiskSignal.SENSITIVE_FIELD)
        }.distinct()

        val summaryAdvisorState = ScreenState(
            packageName = packageName,
            appName = appName,
            className = className,
            timestampMillis = timestampMillis,
            isAccessibilityReady = isAccessibilityReady,
            visibleText = orderedVisibleText,
            contentDescriptions = orderedContentDescriptions,
            clickableElements = clickableElements,
            editableFields = editableFields,
            scrollableElements = scrollableElements,
            selectedElements = selectedElements,
            focusedElement = focusedElement,
            enabledElements = enabledElements,
            disabledElements = disabledElements,
            possibleButtons = possibleButtons,
            possibleSearchFields = possibleSearchFields,
            possibleListsOrCards = possibleListsOrCards,
            errorMessages = errorMessages,
            loadingSignals = loadingSignals,
            permissionSignals = permissionSignals,
            loginSignals = loginSignals,
            paymentSignals = paymentSignals,
            otpSignals = otpSignals,
            passwordSignals = passwordSignals,
            captchaSignals = captchaSignals,
            biometricSignals = biometricSignals,
            riskSignals = riskSignals,
            summarizedState = "",
            confidence = 0f,
            rawNodeCount = rawNodeCount,
            truncated = truncated,
            nodes = nodes
        )

        val summarizedState = recoveryAdvisor.buildSummary(summaryAdvisorState)
        return summaryAdvisorState.copy(
            summarizedState = summarizedState,
            confidence = confidence(
                packageName = packageName,
                appName = appName,
                className = className,
                visibleTextCount = orderedVisibleText.size,
                clickableCount = clickableElements.size,
                editableCount = editableFields.size,
                scrollableCount = scrollableElements.size,
                riskCount = riskSignals.size,
                truncated = truncated
            )
        )
    }

    private fun confidence(
        packageName: String,
        appName: String?,
        className: String?,
        visibleTextCount: Int,
        clickableCount: Int,
        editableCount: Int,
        scrollableCount: Int,
        riskCount: Int,
        truncated: Boolean
    ): Float {
        var score = 0.18f
        if (packageName.isNotBlank()) score += 0.12f
        if (!appName.isNullOrBlank()) score += 0.08f
        if (!className.isNullOrBlank()) score += 0.06f
        score += (visibleTextCount.coerceAtMost(12) * 0.03f)
        score += (clickableCount.coerceAtMost(8) * 0.025f)
        score += (editableCount.coerceAtMost(6) * 0.02f)
        score += (scrollableCount.coerceAtMost(4) * 0.015f)
        score += (riskCount.coerceAtMost(6) * 0.01f)
        if (truncated) score -= 0.08f
        return score.coerceIn(0f, 1f)
    }

    private fun distinctText(values: List<String>): List<String> {
        val result = mutableListOf<String>()
        values.forEach { value ->
            val trimmed = value.trim()
            if (trimmed.isNotBlank() && result.none { it.equals(trimmed, ignoreCase = true) }) {
                result.add(trimmed)
            }
        }
        return result
    }

    private fun collectLabels(
        nodes: List<ScreenNode>,
        expectedRiskSignals: Set<ScreenRiskSignal>,
        predicate: (String) -> Boolean
    ): List<String> {
        val result = mutableListOf<String>()
        nodes.forEach { node ->
            val label = node.primaryLabel()?.trim().orEmpty()
            val riskMatch = node.riskLabels.any { expectedRiskSignals.contains(it) }
            val safeLabel = if (label.isNotBlank()) {
                label
            } else if (riskMatch) {
                node.riskLabels.firstOrNull { expectedRiskSignals.contains(it) }
                    ?.name
                    ?.lowercase(Locale.US)
                    ?.replace('_', ' ')
                    ?: "sensitive field"
            } else {
                ""
            }

            if (safeLabel.isNotBlank() &&
                (riskMatch || predicate(normalize(label))) &&
                result.none { it.equals(safeLabel, ignoreCase = true) }
            ) {
                result.add(safeLabel)
            }
        }
        return result
    }

    private fun looksLikeButton(node: ScreenNode): Boolean {
        val label = normalize(node.primaryLabel().orEmpty())
        val className = normalize(node.className.orEmpty())
        return node.isClickable &&
            (
                className.contains("button") ||
                    className.contains("imagebutton") ||
                    hasAnyKeyword(label, BUTTON_KEYWORDS)
            )
    }

    private fun looksLikeSearchField(node: ScreenNode): Boolean {
        val label = normalize(node.primaryLabel().orEmpty())
        val className = normalize(node.className.orEmpty())
        return node.isEditable &&
            (
                className.contains("search") ||
                    label.contains("search") ||
                    label.contains("find")
            )
    }

    private fun looksLikeListOrCard(node: ScreenNode): Boolean {
        val className = normalize(node.className.orEmpty())
        val label = normalize(node.primaryLabel().orEmpty())
        return className.contains("recycler") ||
            className.contains("list") ||
            className.contains("scroll") ||
            className.contains("card") ||
            label.contains("list") ||
            label.contains("card")
    }

    private fun containsSensitiveField(nodes: List<ScreenNode>): Boolean {
        return nodes.any { node ->
            val label = normalize(node.primaryLabel().orEmpty())
            node.isEditable &&
                (
                    node.riskLabels.any {
                        it == ScreenRiskSignal.OTP ||
                            it == ScreenRiskSignal.PASSWORD ||
                            it == ScreenRiskSignal.PIN ||
                            it == ScreenRiskSignal.CVV ||
                            it == ScreenRiskSignal.CAPTCHA ||
                            it == ScreenRiskSignal.BIOMETRIC
                    } ||
                    hasAnyKeyword(label, PASSWORD_KEYWORDS) ||
                        hasAnyKeyword(label, OTP_KEYWORDS) ||
                        hasAnyKeyword(label, PAYMENT_KEYWORDS) ||
                        hasAnyKeyword(label, CAPTCHA_KEYWORDS) ||
                        hasAnyKeyword(label, BIOMETRIC_KEYWORDS)
                )
        }
    }

    private fun containsPinSignal(nodes: List<ScreenNode>): Boolean {
        return nodes.any { node ->
            val label = normalize(node.primaryLabel().orEmpty())
            hasAnyKeyword(label, PIN_KEYWORDS)
        }
    }

    private fun containsCvvSignal(nodes: List<ScreenNode>): Boolean {
        return nodes.any { node ->
            val label = normalize(node.primaryLabel().orEmpty())
            hasAnyKeyword(label, CVV_KEYWORDS)
        }
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.US).trim()
    }

    private fun hasAnyKeyword(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    companion object {
        private val ERROR_KEYWORDS = listOf(
            "error",
            "failed",
            "failure",
            "something went wrong",
            "try again",
            "unable",
            "invalid",
            "not working",
            "could not"
        )

        private val LOADING_KEYWORDS = listOf(
            "loading",
            "please wait",
            "wait",
            "processing",
            "progress",
            "checking",
            "syncing",
            "fetching"
        )

        private val PERMISSION_KEYWORDS = listOf(
            "permission",
            "allow",
            "deny",
            "grant",
            "while using the app",
            "only this time",
            "not now"
        )

        private val LOGIN_KEYWORDS = listOf(
            "login",
            "log in",
            "sign in",
            "signin",
            "continue with",
            "enter email",
            "email address",
            "phone number"
        )

        private val PAYMENT_KEYWORDS = listOf(
            "payment",
            "pay",
            "pay now",
            "checkout",
            "checkout now",
            "place order",
            "complete payment",
            "card",
            "wallet",
            "upi",
            "amount",
            "billing"
        )

        private val OTP_KEYWORDS = listOf(
            "otp",
            "one time password",
            "verification code",
            "verification otp"
        )

        private val PASSWORD_KEYWORDS = listOf(
            "password",
            "passcode",
            "pin",
            "cvv",
            "cvc"
        )

        private val PIN_KEYWORDS = listOf(
            "pin",
            "upi pin",
            "card pin"
        )

        private val CVV_KEYWORDS = listOf(
            "cvv",
            "cvc"
        )

        private val CAPTCHA_KEYWORDS = listOf(
            "captcha",
            "i'm not a robot",
            "im not a robot",
            "not a robot",
            "human verification",
            "robot verification"
        )

        private val BIOMETRIC_KEYWORDS = listOf(
            "biometric",
            "fingerprint",
            "face unlock",
            "face id",
            "face recognition"
        )

        private val BUTTON_KEYWORDS = listOf(
            "add",
            "open",
            "submit",
            "continue",
            "next",
            "save",
            "done",
            "okay",
            "ok",
            "search",
            "retry",
            "allow",
            "deny"
        )
    }
}
