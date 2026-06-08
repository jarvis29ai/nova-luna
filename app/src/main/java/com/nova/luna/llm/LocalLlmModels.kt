package com.nova.luna.llm

import com.nova.luna.brain.AssistantContext
import com.nova.luna.brain.UnifiedDomain
import com.nova.luna.model.ActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.CommandIntent

enum class LocalLlmModelId {
    GEMMA_3N_CORE,
    QWEN_3_SMALL_MULTILINGUAL,
    GEMMA_3_270M_FALLBACK,
    PHI_4_MINI_FALLBACK,
    UNKNOWN
}

enum class LocalLlmRole {
    CORE_REASONING,
    MULTILINGUAL_BACKUP,
    LIGHT_FALLBACK,
    SCREEN_UNDERSTANDING,
    CONTENT_ASSIST,
    DISABLED
}

enum class LocalLlmStatus {
    NOT_CONFIGURED,
    ASSET_MISSING,
    ENGINE_UNAVAILABLE,
    READY,
    LOADING,
    RUNNING,
    FAILED,
    DISABLED_BY_SAFETY,
    LOW_MEMORY_DISABLED
}

data class LocalLlmRequest(
    val commandText: String,
    val normalizedCommand: String = commandText.lowercase().trim(),
    val currentDomainGuess: UnifiedDomain = UnifiedDomain.UNKNOWN,
    val screenSnapshotSummary: String? = null,
    val assistantContext: AssistantContext? = null,
    val allowedActionTypes: Set<ActionType> = emptySet(),
    val forbiddenActionTypes: Set<ActionType> = emptySet(),
    val riskPolicy: String? = null,
    val languageHint: String? = null,
    val maxTokens: Int = 512,
    val timeoutMs: Long = 10_000,
    val modelId: LocalLlmModelId = LocalLlmModelId.GEMMA_3N_CORE,
    val timestamp: Long = System.currentTimeMillis()
)

data class LocalLlmResult(
    val status: LocalLlmStatus,
    val modelId: LocalLlmModelId,
    val modelDisplayName: String,
    val rawOutput: String? = null,
    val parsedCandidateAction: CommandIntent? = null,
    val parsedDomain: UnifiedDomain = UnifiedDomain.UNKNOWN,
    val confidence: Float = 0.0f,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val userMessage: String? = null,
    val safetyNotes: String? = null,
    val technicalReason: String? = null,
    val latencyMs: Long = 0,
    val tokenCountEstimate: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LocalLlmError {
    MODEL_NOT_READY,
    ASSET_NOT_FOUND,
    ENGINE_NOT_AVAILABLE,
    TIMEOUT,
    INVALID_JSON,
    UNSAFE_OUTPUT,
    EMPTY_OUTPUT,
    LOW_MEMORY,
    UNKNOWN_ERROR
}
