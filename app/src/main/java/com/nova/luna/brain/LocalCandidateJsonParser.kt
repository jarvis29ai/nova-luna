package com.nova.luna.brain

import com.nova.luna.model.BrainAction

enum class LocalCandidateParseStatus {
    READY,
    INVALID_JSON,
    UNSUPPORTED_SCHEMA,
    EMPTY_OUTPUT
}

data class LocalCandidateParseResult(
    val rawOutput: String,
    val extractedJson: String? = null,
    val candidateAction: BrainAction? = null,
    val status: LocalCandidateParseStatus,
    val reason: String
) {
    val accepted: Boolean
        get() = status == LocalCandidateParseStatus.READY && candidateAction != null
}

class LocalCandidateJsonParser(
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) {
    fun parse(rawOutput: String): LocalCandidateParseResult {
        val trimmed = rawOutput.trim()
        if (trimmed.isBlank()) {
            return failure(
                rawOutput = rawOutput,
                status = LocalCandidateParseStatus.EMPTY_OUTPUT,
                reason = "Model output was empty."
            )
        }

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return failure(
                rawOutput = rawOutput,
                status = LocalCandidateParseStatus.INVALID_JSON,
                reason = "Local model output must be strict JSON only."
            )
        }

        val parsedObject = runCatching { com.nova.luna.modelinstall.SimpleJson.parseObject(trimmed) }
            .getOrElse {
                return failure(
                    rawOutput = rawOutput,
                    extractedJson = trimmed,
                    status = LocalCandidateParseStatus.INVALID_JSON,
                    reason = "Local model output was not valid JSON."
                )
            }

        val allowedKeys = setOf(
            "intent",
            "reply",
            "actionType",
            "riskLevel",
            "requiresConfirmation",
            "finalActionAllowed",
            "params",
            "nextQuestion"
        )
        val unknownKeys = parsedObject.keys.filterNot { it in allowedKeys }
        if (unknownKeys.isNotEmpty()) {
            return failure(
                rawOutput = rawOutput,
                extractedJson = trimmed,
                status = LocalCandidateParseStatus.UNSUPPORTED_SCHEMA,
                reason = "Local model output contained unsupported keys: ${unknownKeys.joinToString(", ")}"
            )
        }

        val candidateAction = codec.decode(trimmed)
            ?: return failure(
                rawOutput = rawOutput,
                extractedJson = trimmed,
                status = LocalCandidateParseStatus.INVALID_JSON,
                reason = "Local model output did not decode into BrainAction JSON."
            )

        if (candidateAction.intent.isBlank() || candidateAction.reply.isBlank()) {
            return failure(
                rawOutput = rawOutput,
                extractedJson = trimmed,
                candidateAction = candidateAction,
                status = LocalCandidateParseStatus.INVALID_JSON,
                reason = "Local model output contained a blank intent or reply."
            )
        }

        return LocalCandidateParseResult(
            rawOutput = rawOutput,
            extractedJson = trimmed,
            candidateAction = candidateAction,
            status = LocalCandidateParseStatus.READY,
            reason = "Strict candidate JSON parsed successfully."
        )
    }

    private fun failure(
        rawOutput: String,
        status: LocalCandidateParseStatus,
        reason: String,
        extractedJson: String? = null,
        candidateAction: BrainAction? = null
    ): LocalCandidateParseResult {
        return LocalCandidateParseResult(
            rawOutput = rawOutput,
            extractedJson = extractedJson,
            candidateAction = candidateAction,
            status = status,
            reason = reason
        )
    }
}
