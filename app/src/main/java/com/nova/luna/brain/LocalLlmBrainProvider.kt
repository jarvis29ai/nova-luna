package com.nova.luna.brain

class LocalLlmBrainProvider(
    private val config: BrainRuntimeConfig = BrainRuntimeConfig.fromBuildConfig(),
    private val client: OllamaClient = HttpOllamaClient(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) : BrainProvider, BrainProviderDiagnostics {
    override fun analyze(request: BrainRequest): String {
        return diagnose(request).extractedJson
            ?: throw IllegalStateException("Local LLM response did not contain BrainAction JSON.")
    }

    override fun diagnose(request: BrainRequest): BrainProviderTrace {
        if (!config.useLocalLlm()) {
            throw IllegalStateException("Local LLM provider is disabled by config.")
        }

        val responseBody = client.generate(
            baseUrl = config.ollamaBaseUrl,
            model = config.ollamaModel,
            prompt = BrainSystemPrompt.build(request)
        )
        val extractedJson = extractBrainActionJson(responseBody)
        val parsedAction = extractedJson?.let(codec::decode)

        return BrainProviderTrace(
            providerName = this::class.java.simpleName,
            rawResponse = responseBody,
            extractedJson = extractedJson,
            parsedAction = parsedAction,
            error = if (parsedAction == null) "invalid_or_rejected_output" else null
        )
    }

    private fun extractBrainActionJson(responseBody: String): String? {
        val trimmed = responseBody.trim()
        if (looksLikeBrainActionJson(trimmed)) {
            return trimmed
        }

        normalizeCandidate(trimmed)?.let { return it }

        val extracted = responseFieldRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { unescapeJson(it) }
        return normalizeCandidate(extracted)
    }

    private fun normalizeCandidate(candidate: String?): String? {
        val trimmed = candidate?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        if (looksLikeBrainActionJson(trimmed)) {
            return trimmed
        }

        stripMarkdownFence(trimmed)?.let {
            if (looksLikeBrainActionJson(it)) {
                return it
            }
        }

        return null
    }

    private fun stripMarkdownFence(value: String): String? {
        val fenced = markdownFenceRegex.matchEntire(value) ?: return null
        val body = fenced.groupValues.getOrNull(1)?.trim().orEmpty()
        return body.takeIf { it.isNotBlank() }
    }

    private fun looksLikeBrainActionJson(value: String): Boolean {
        return value.startsWith("{") &&
            value.contains("\"intent\"") &&
            value.contains("\"reply\"") &&
            value.contains("\"actionType\"") &&
            value.contains("\"riskLevel\"")
    }

    private fun unescapeJson(value: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\') {
                builder.append(char)
                index += 1
                continue
            }

            if (index == value.lastIndex) {
                builder.append('\\')
                break
            }

            val next = value[index + 1]
            when (next) {
                '\\' -> builder.append('\\')
                '"' -> builder.append('"')
                '/' -> builder.append('/')
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> {
                    if (index + 5 >= value.length) {
                        return value
                    }

                    val hex = value.substring(index + 2, index + 6)
                    if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                        return value
                    }

                    builder.append(hex.toInt(16).toChar())
                    index += 4
                }
                else -> builder.append(next)
            }
            index += 2
        }
        return builder.toString()
    }

    private companion object {
        val responseFieldRegex = Regex(
            pattern = "\"response\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"",
            options = setOf(RegexOption.IGNORE_CASE)
        )

        val markdownFenceRegex = Regex(
            pattern = "^```(?:json)?\\s*([\\s\\S]*?)\\s*```$",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    }
}
