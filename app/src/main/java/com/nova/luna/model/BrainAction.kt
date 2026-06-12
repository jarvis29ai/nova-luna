package com.nova.luna.model

data class BrainAction(
    val intent: String,
    val reply: String = "",
    val actionType: BrainActionType,
    val riskLevel: BrainRiskLevel,
    val requiresConfirmation: Boolean,
    val params: Map<String, String> = emptyMap(),
    val nextQuestion: String? = null,
    val finalActionAllowed: Boolean = !requiresConfirmation,

    val schemaVersion: Int = 1,
    val source: BrainActionSource = BrainActionSource.RULE_FALLBACK,
    val rawCommand: String = "",
    val normalizedCommand: String = "",
    val confidence: Double = 1.0,
    val language: String = "unknown",
    val assistantReply: String = "",
    val reason: String = "",
    val errors: List<String> = emptyList()
) {
    fun withPhase23Metadata(
        schemaVersion: Int = this.schemaVersion,
        source: BrainActionSource = this.source,
        rawCommand: String = this.rawCommand,
        normalizedCommand: String = this.normalizedCommand,
        confidence: Double = this.confidence,
        language: String = this.language,
        assistantReply: String = this.assistantReply,
        reason: String = this.reason,
        errors: List<String> = this.errors
    ): BrainAction {
        return copy(
            schemaVersion = schemaVersion,
            source = source,
            rawCommand = rawCommand,
            normalizedCommand = normalizedCommand,
            confidence = confidence,
            language = language,
            assistantReply = assistantReply,
            reason = reason,
            errors = errors
        )
    }
}

enum class BrainActionSource {
    MODEL,
    RULE_FALLBACK,
    MODEL_WITH_RULE_REPAIR,
    ERROR
}
