package com.nova.luna.brain

import com.nova.luna.memory.BrainMemorySnapshot
import com.nova.luna.memory.BrainSessionType
import com.nova.luna.memory.PendingConfirmation
import com.nova.luna.model.BrainModelCatalogEntry
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.screen.ScreenState

data class LocalBrainPromptContext(
    val userCommand: String,
    val normalizedCommand: String,
    val selectedRole: BrainModelRole,
    val selectedModelDisplayName: String,
    val selectedModelFamilies: List<String>,
    val preferredLanguage: String? = null,
    val activeSessionSummary: String? = null,
    val pendingConfirmationSummary: String? = null,
    val screenSummary: String? = null,
    val appSummary: String? = null,
    val allowedCapabilities: List<String> = emptyList(),
    val safetyNotes: List<String> = emptyList()
)

class LocalBrainRequestContextBuilder(
    private val allowedCapabilities: List<String> = listOf(
        "read_only",
        "draft",
        "plan",
        "ask_clarification",
        "candidate_json_only"
    )
) {
    fun build(
        request: BrainRequest,
        routeDecision: BrainRouteDecision,
        catalogEntry: BrainModelCatalogEntry
    ): LocalBrainPromptContext {
        val normalizedCommand = request.rawText.lowercase().trim()
        val memorySnapshot = request.memorySnapshot
        return LocalBrainPromptContext(
            userCommand = request.rawText.trim(),
            normalizedCommand = normalizedCommand,
            selectedRole = routeDecision.selectedRole,
            selectedModelDisplayName = catalogEntry.displayName,
            selectedModelFamilies = catalogEntry.modelFamilies,
            preferredLanguage = request.preferences?.preferredLanguage?.trim()?.takeIf { it.isNotBlank() },
            activeSessionSummary = buildSessionSummary(request.activeSessionType, memorySnapshot),
            pendingConfirmationSummary = buildPendingConfirmationSummary(request.pendingConfirmation, memorySnapshot),
            screenSummary = buildScreenSummary(request.screenState),
            appSummary = buildAppSummary(request.screenState),
            allowedCapabilities = allowedCapabilities,
            safetyNotes = routeDecision.safetyNotes
        )
    }

    private fun buildSessionSummary(
        activeSessionType: BrainSessionType?,
        memorySnapshot: BrainMemorySnapshot?
    ): String? {
        val sessionType = activeSessionType ?: memorySnapshot?.activeSessionType()
        val activeSession = sessionType?.let { memorySnapshot?.activeSession(it) }
        return buildList {
            if (sessionType != null) add("activeSessionType=${sessionType.wireValue}")
            if (activeSession?.normalizedGoal?.isNotBlank() == true) add("goal=${activeSession.normalizedGoal}")
            if (memorySnapshot?.activeSessionCount ?: 0 > 0) {
                add("activeSessionCount=${memorySnapshot?.activeSessionCount ?: 0}")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(separator = ", ")
    }

    private fun buildPendingConfirmationSummary(
        pendingConfirmation: PendingConfirmation?,
        memorySnapshot: BrainMemorySnapshot?
    ): String? {
        val activeConfirmation = pendingConfirmation ?: memorySnapshot?.activePendingConfirmation
        return activeConfirmation?.let {
            buildList {
                add("type=${it.type.wireValue}")
                add("summary=${it.userFacingSummary}")
                add("risk=${it.riskLevel.wireValue}")
            }.joinToString(separator = ", ")
        }
    }

    private fun buildScreenSummary(screenState: ScreenState?): String? {
        if (screenState == null) return null
        return buildList {
            add("package=${screenState.packageName}")
            screenState.appName?.takeIf { it.isNotBlank() }?.let { add("app=$it") }
            screenState.summarizedState.takeIf { it.isNotBlank() }?.let { add("summary=$it") }
        }.joinToString(separator = ", ")
    }

    private fun buildAppSummary(screenState: ScreenState?): String? {
        if (screenState == null) return null
        return buildList {
            add("package=${screenState.packageName}")
            screenState.appName?.takeIf { it.isNotBlank() }?.let { add("name=$it") }
            screenState.className?.takeIf { it.isNotBlank() }?.let { add("class=${it}") }
        }.joinToString(separator = ", ")
    }
}
