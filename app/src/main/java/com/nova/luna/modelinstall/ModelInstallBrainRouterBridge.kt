package com.nova.luna.modelinstall

import com.nova.luna.brain.BrainRequest
import com.nova.luna.brain.BrainRoleReadinessProvider
import com.nova.luna.brain.BrainRouterBridge
import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.isLocalBrainRole
import com.nova.luna.util.AssistantTextNormalizer

class ModelInstallBrainRouterBridge(
    private val runtimeReadinessChecker: LocalRuntimeReadinessChecker,
    private val catalog: BrainModelCatalog = BrainModelCatalog
) : BrainRouterBridge, BrainRoleReadinessProvider {
    override fun isReady(role: BrainModelRole): Boolean {
        val packId = packIdForRole(role) ?: return false
        return runCatching { runtimeReadinessChecker.inspect(packId).ready }
            .getOrDefault(false)
    }

    override fun selectLocalRoute(
        request: BrainRequest,
        allowOnlineHelper: Boolean
    ): BrainRouteDecision? {
        val stripped = AssistantTextNormalizer.stripWakeWords(request.rawText).trim()
        if (stripped.isBlank()) {
            return null
        }
        val normalized = normalize(stripped)

        val role = selectRoleCandidates(request, normalized)
            .firstOrNull { isReady(it) }
            ?: return null
        val entry = catalog.entryForRole(role) ?: return null

        return BrainRouteDecision(
            selectedRole = role,
            reason = buildReason(role, entry.displayName),
            requiresInternet = false,
            requiresScreenContext = false,
            fallbackAllowed = true,
            safetyNotes = buildSafetyNotes(role, entry.displayName, allowOnlineHelper)
        )
    }

    private fun selectRoleCandidates(request: BrainRequest, normalized: String): List<BrainModelRole> {
        val languageHint = request.preferences?.preferredLanguage
            ?.lowercase()
            ?.trim()
            .orEmpty()

        val multilingualRequest = containsNonLatinScript(request.rawText) ||
            languageHint in setOf("hi", "hinglish", "hindi") ||
            containsAny(normalized, listOf("translate", "samjhao", "batao", "explain this", "help me understand"))

        if (multilingualRequest) {
            return listOf(
                BrainModelRole.MULTILINGUAL_BACKUP,
                BrainModelRole.CORE_BRAIN,
                BrainModelRole.LITE_FALLBACK
            )
        }

        val complexRequest = isComplexRequest(normalized)
        if (complexRequest) {
            return listOf(
                BrainModelRole.CORE_BRAIN,
                BrainModelRole.LITE_FALLBACK
            )
        }

        val liteCandidate = isLiteFallbackCandidate(normalized)
        return if (liteCandidate) {
            listOf(BrainModelRole.LITE_FALLBACK)
        } else {
            emptyList()
        }
    }

    private fun isComplexRequest(normalized: String): Boolean {
        val wordCount = normalized.split(Regex("\\s+")).count { it.isNotBlank() }
        if (wordCount == 0) return false

        val phrases = listOf(
            "help me",
            "please help",
            "can you help",
            "could you help",
            "would you help",
            "please explain",
            "explain this",
            "summarize this",
            "rewrite this",
            "draft this",
            "compare these",
            "compare this",
            "what should i",
            "what do you think",
            "how should i",
            "how can i",
            "please batao",
            "please samjhao",
            "thoda help",
            "make this",
            "improve this",
            "check this"
        )

        if (phrases.any { containsPhrase(normalized, it) }) {
            return true
        }

        val signalWords = listOf(
            "help",
            "explain",
            "translate",
            "rewrite",
            "summarize",
            "draft",
            "compare",
            "plan",
            "tell me",
            "make",
            "improve",
            "check",
            "how",
            "what",
            "why"
        )

        if (normalized.contains("?") && wordCount >= 3) {
            return true
        }

        return wordCount >= 3 && containsAny(normalized, signalWords)
    }

    private fun isLiteFallbackCandidate(normalized: String): Boolean {
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        if (words.size > 6) return false

        val fallbackSignals = listOf(
            "simple",
            "basic",
            "quick",
            "short",
            "light",
            "lite",
            "fallback",
            "small",
            "brief"
        )

        return fallbackSignals.any { containsPhrase(normalized, it) } ||
            containsAny(normalized, listOf("help", "explain", "translate", "rewrite", "summarize", "plan", "draft")) ||
            normalized.contains("?")
    }

    private fun buildReason(role: BrainModelRole, displayName: String): String {
        return when (role) {
            BrainModelRole.CORE_BRAIN ->
                "$displayName is runtime-ready for complex on-device reasoning."

            BrainModelRole.MULTILINGUAL_BACKUP ->
                "$displayName is runtime-ready for multilingual on-device reasoning."

            BrainModelRole.LITE_FALLBACK ->
                "$displayName is runtime-ready for lightweight fallback reasoning."

            else -> "$displayName is runtime-ready."
        }
    }

    private fun buildSafetyNotes(
        role: BrainModelRole,
        displayName: String,
        allowOnlineHelper: Boolean
    ): List<String> {
        val notes = mutableListOf(
            "$displayName must only return candidate JSON or a draft plan.",
            "SafetyGate and BrainActionValidator still remain mandatory.",
            "No local model may call ActionExecutor directly."
        )

        if (role.isLocalBrainRole()) {
            notes += "Downloaded local models stay inside private app storage."
        }

        if (!allowOnlineHelper) {
            notes += "Online helper is disabled for this route."
        }

        catalog.entryForRole(role)?.notes?.let { notes += it }
        return notes.distinct()
    }

    private fun packIdForRole(role: BrainModelRole): ModelPackId? {
        return when (role) {
            BrainModelRole.CORE_BRAIN -> ModelPackId.CORE
            BrainModelRole.MULTILINGUAL_BACKUP -> ModelPackId.FULL
            BrainModelRole.LITE_FALLBACK -> ModelPackId.LITE
            else -> null
        }
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private fun containsNonLatinScript(rawText: String): Boolean {
        return rawText.any { character -> character.code > 127 }
    }

    private fun containsPhrase(normalized: String, phrase: String): Boolean {
        val target = normalize(phrase)
        if (target.isBlank()) return false
        return normalized.contains(target)
    }

    private fun containsAny(normalized: String, keywords: List<String>): Boolean {
        return keywords.any { containsPhrase(normalized, it) }
    }
}
