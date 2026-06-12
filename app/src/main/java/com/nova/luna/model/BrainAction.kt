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
    val assistantReply: String = reply,
    val reason: String = nextQuestion ?: "",
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
        val finalReply = if (this.reply.isNotBlank()) this.reply else assistantReply
        return copy(
            reply = finalReply,
            schemaVersion = schemaVersion,
            source = source,
            rawCommand = rawCommand,
            normalizedCommand = normalizedCommand,
            confidence = confidence,
            language = language,
            assistantReply = finalReply,
            reason = reason.ifBlank { this.nextQuestion ?: "" },
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
