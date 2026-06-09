package com.nova.luna.brain

import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.isFutureLocalBrainRole
import com.nova.luna.util.AssistantTextNormalizer

data class MultiModelRoleSelection(
    val selectedRole: BrainModelRole,
    val candidateRoles: List<BrainModelRole>,
    val reason: String,
    val safetyNotes: List<String>
)

class MultiModelRoleSelector(
    private val catalog: BrainModelCatalog = BrainModelCatalog,
    private val languageHintDetector: LanguageHintDetector = LanguageHintDetector(),
    private val complexityClassifier: RequestComplexityClassifier = RequestComplexityClassifier(),
    private val failureTracker: ModelRuntimeFailureTracker = ModelRuntimeFailureTracker()
) {
    fun select(
        request: BrainRequest,
        readinessProvider: BrainRoleReadinessProvider,
        allowOnlineHelper: Boolean = true
    ): MultiModelRoleSelection? {
        val stripped = AssistantTextNormalizer.stripWakeWords(request.rawText).trim()
        if (stripped.isBlank() && !containsNonLatinScript(request.rawText)) {
            return null
        }

        val normalized = normalize(stripped)
        val signal = complexityClassifier.classify(normalized)
        if (signal.simpleCommand) {
            return null
        }

        val candidateRoles = candidateRoles(request, normalized, signal)
        if (candidateRoles.isEmpty()) {
            return null
        }

        val selectedRole = candidateRoles.firstOrNull { role ->
            readinessProvider.isReady(role) && !failureTracker.isSuppressed(role)
        } ?: return null

        val selectedEntry = catalog.entryForRole(selectedRole)
            ?: return null
        val preferredRole = candidateRoles.first()
        val preferredEntry = catalog.entryForRole(preferredRole)

        return MultiModelRoleSelection(
            selectedRole = selectedRole,
            candidateRoles = candidateRoles,
            reason = buildReason(
                selectedEntry = selectedEntry,
                preferredEntry = preferredEntry,
                selectedRole = selectedRole,
                preferredRole = preferredRole
            ),
            safetyNotes = buildSafetyNotes(
                selectedEntry = selectedEntry,
                allowOnlineHelper = allowOnlineHelper
            )
        )
    }

    fun recordOutcome(
        role: BrainModelRole,
        available: Boolean,
        reason: String? = null
    ) {
        if (!role.isFutureLocalBrainRole()) {
            return
        }

        if (available) {
            failureTracker.recordSuccess(role)
        } else {
            failureTracker.recordFailure(role, reason)
        }
    }

    fun isSuppressed(role: BrainModelRole): Boolean {
        return failureTracker.isSuppressed(role)
    }

    fun snapshotFailures(): List<ModelRuntimeFailureSnapshot> {
        return failureTracker.snapshot()
    }

    private fun candidateRoles(
        request: BrainRequest,
        normalized: String,
        signal: RequestComplexitySignal
    ): List<BrainModelRole> {
        return when {
            languageHintDetector.isMultilingualRequest(request, normalized) -> listOf(
                BrainModelRole.MULTILINGUAL_BACKUP,
                BrainModelRole.CORE_BRAIN,
                BrainModelRole.LITE_FALLBACK
            )

            signal.liteFallbackCandidate -> listOf(
                BrainModelRole.LITE_FALLBACK,
                BrainModelRole.CORE_BRAIN,
                BrainModelRole.MULTILINGUAL_BACKUP
            )

            signal.complexRequest -> listOf(
                BrainModelRole.CORE_BRAIN,
                BrainModelRole.MULTILINGUAL_BACKUP,
                BrainModelRole.LITE_FALLBACK
            )

            else -> emptyList()
        }.filter { catalog.entryForRole(it) != null }
            .distinct()
    }

    private fun buildReason(
        selectedEntry: com.nova.luna.model.BrainModelCatalogEntry,
        preferredEntry: com.nova.luna.model.BrainModelCatalogEntry?,
        selectedRole: BrainModelRole,
        preferredRole: BrainModelRole
    ): String {
        return if (selectedRole == preferredRole) {
            "${selectedEntry.displayName} is runtime-ready for this request."
        } else {
            val preferredName = preferredEntry?.displayName ?: preferredRole.wireValue
            "${selectedEntry.displayName} is the next ready local fallback after $preferredName was unavailable or suppressed."
        }
    }

    private fun buildSafetyNotes(
        selectedEntry: com.nova.luna.model.BrainModelCatalogEntry,
        allowOnlineHelper: Boolean
    ): List<String> {
        val notes = mutableListOf(
            "${selectedEntry.displayName} must only return candidate JSON or a draft plan.",
            "SafetyGate and BrainActionValidator still remain mandatory.",
            "No local model may call ActionExecutor directly.",
            "Downloaded local models stay inside private app storage."
        )

        if (!allowOnlineHelper) {
            notes += "Online helper is disabled for this route."
        }

        notes += selectedEntry.notes
        return notes.distinct()
    }

    private fun normalize(value: String): String {
        return AssistantTextNormalizer.normalize(value)
    }

    private fun containsNonLatinScript(rawText: String): Boolean {
        return rawText.any { character -> character.code > 127 }
    }
}
