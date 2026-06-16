package com.nova.luna.brain

import com.nova.luna.model.BrainAction

data class PhoneLocalLlmOutputParseResult(
    val rawOutput: String,
    val extractedJson: String? = null,
    val candidateAction: BrainAction? = null,
    val status: PhoneLocalLlmStatus,
    val reason: String
) {
    val accepted: Boolean
        get() = status == PhoneLocalLlmStatus.READY && candidateAction != null
}

class PhoneLocalLlmOutputParser(
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec(),
    private val validator: BrainActionValidator = BrainActionValidator()
) {
    fun parse(output: String): PhoneLocalLlmOutputParseResult {
        val trimmed = output.trim()
        if (trimmed.isBlank()) {
            return failure(
                rawOutput = output,
                status = PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED,
                reason = "Model output was empty."
            )
        }

        qualityFailure(trimmed)?.let { reason ->
            return failure(
                rawOutput = output,
                status = PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED,
                reason = reason
            )
        }

        val extractedJson = extractStrictJson(trimmed)
            ?: return failure(
                rawOutput = output,
                status = PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED,
                reason = "Model output was not strict JSON."
            )

        val candidateAction = codec.decode(extractedJson)
            ?: return failure(
                rawOutput = output,
                extractedJson = extractedJson,
                status = PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED,
                reason = "Model output did not decode into BrainAction JSON."
            )

        val effectiveReply = candidateAction.reply.ifBlank { candidateAction.assistantReply }
        if (candidateAction.intent.isBlank() || effectiveReply.isBlank()) {
            return failure(
                rawOutput = output,
                extractedJson = extractedJson,
                status = PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED,
                reason = "Model output contained a blank intent or reply."
            )
        }

        if (!validator.isAcceptable(candidateAction)) {
            return failure(
                rawOutput = output,
                extractedJson = extractedJson,
                candidateAction = candidateAction,
                status = PhoneLocalLlmStatus.VALIDATION_REJECTED,
                reason = "BrainActionValidator rejected the local LLM candidate."
            )
        }

        return PhoneLocalLlmOutputParseResult(
            rawOutput = output,
            extractedJson = extractedJson,
            candidateAction = candidateAction,
            status = PhoneLocalLlmStatus.READY,
            reason = "Strict BrainAction JSON parsed successfully."
        )
    }

    private fun qualityFailure(text: String): String? {
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) {
            return "Model output did not contain any visible content."
        }

        val alnumCount = compact.count { it.isLetterOrDigit() }
        if (alnumCount == 0) {
            return "Model output contained only punctuation."
        }

        if (looksLikeRepeatedPunctuation(compact)) {
            return "Model output was repeated punctuation."
        }

        if (looksLikeRepeatedToken(text)) {
            return "Model output was repeated token garbage."
        }

        return null
    }

    private fun looksLikeRepeatedPunctuation(compact: String): Boolean {
        if (compact.length < 6) return false
        val first = compact.first()
        if (!first.isLetterOrDigit()) {
            return compact.all { it == first }
        }
        return false
    }

    private fun looksLikeRepeatedToken(text: String): Boolean {
        val tokens = text
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.size < 4) return false
        val normalized = tokens.map { it.lowercase() }
        return normalized.distinct().size == 1
    }

    private fun extractStrictJson(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }

        val fenceMatch = markdownFenceRegex.matchEntire(trimmed) ?: return null
        val jsonCandidate = fenceMatch.groupValues.getOrNull(1)?.trim().orEmpty()
        return jsonCandidate.takeIf { it.startsWith("{") && it.endsWith("}") }
    }

    private fun failure(
        rawOutput: String,
        status: PhoneLocalLlmStatus,
        reason: String,
        extractedJson: String? = null,
        candidateAction: BrainAction? = null
    ): PhoneLocalLlmOutputParseResult {
        return PhoneLocalLlmOutputParseResult(
            rawOutput = rawOutput,
            extractedJson = extractedJson,
            candidateAction = candidateAction,
            status = status,
            reason = reason
        )
    }

    private companion object {
        val markdownFenceRegex = Regex(
            pattern = "^```(?:json)?\\s*([\\s\\S]*?)\\s*```$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    }
}
