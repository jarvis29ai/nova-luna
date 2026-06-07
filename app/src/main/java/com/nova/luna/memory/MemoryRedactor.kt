package com.nova.luna.memory

import com.nova.luna.screen.ScreenState
import com.nova.luna.screen.ScreenRiskSignal
import java.util.Locale

data class RedactedText(
    val text: String,
    val redactionCount: Int,
    val wasRedacted: Boolean
)

class MemoryRedactor {
    fun redact(value: String?): RedactedText {
        val text = value.orEmpty()
        if (text.isBlank()) {
            return RedactedText(text = "", redactionCount = 0, wasRedacted = false)
        }

        var redactionCount = 0
        var redacted = text
        redactionPatterns.forEach { pattern ->
            val matches = pattern.findAll(redacted).toList()
            if (matches.isNotEmpty()) {
                redactionCount += matches.size
                redacted = pattern.replace(redacted, "[REDACTED]")
            }
        }

        return if (redactionCount > 0 || containsSensitiveKeyword(text)) {
            RedactedText(text = "[REDACTED]", redactionCount = redactionCount.coerceAtLeast(1), wasRedacted = true)
        } else {
            RedactedText(text = text, redactionCount = 0, wasRedacted = false)
        }
    }

    fun redactCollection(values: List<String>): List<String> {
        return values.map { redact(it).text }.distinct()
    }

    fun redactMetadata(metadata: Map<String, String>): Map<String, String> {
        return metadata.mapValues { (_, value) -> redact(value).text }
    }

    fun redactScreenState(screenState: ScreenState): ScreenMemorySnapshot {
        val redactedVisibleText = screenState.visibleText.map { redact(it) }
        val redactedDescriptions = screenState.contentDescriptions.map { redact(it) }
        val summaryRedaction = redact(screenState.summarizedState)
        val visibleText = redactedVisibleText.map { it.text }
        val descriptions = redactedDescriptions.map { it.text }
        val optionStrings = buildList {
            addAll(visibleText)
            addAll(descriptions)
        }.filter { it.isNotBlank() }
        val redactionCount = redactedVisibleText.sumOf { it.redactionCount } +
            redactedDescriptions.sumOf { it.redactionCount } +
            summaryRedaction.redactionCount

        val screenSnapshot = ScreenMemorySnapshot(
            snapshotId = buildSnapshotId(screenState),
            packageName = screenState.packageName,
            appName = screenState.appName,
            summary = summaryRedaction.text,
            visibleOptions = optionStrings.take(10),
            selectedOption = null,
            riskSignals = screenState.riskSignals,
            redactionCount = redactionCount,
            updatedAtMillis = screenState.timestampMillis
        )

        return screenSnapshot
    }

    fun redactTextForMemory(value: String?): String {
        return redact(value).text
    }

    private fun buildSnapshotId(screenState: ScreenState): String {
        return buildString {
            append(screenState.packageName)
            append(':')
            append(screenState.timestampMillis)
            append(':')
            append(screenState.visibleText.take(3).joinToString(separator = "|"))
        }
    }

    private fun containsSensitiveKeyword(text: String): Boolean {
        val normalized = text.lowercase(Locale.US)
        return sensitiveKeywords.any { keyword ->
            normalized.contains(keyword)
        }
    }

    private companion object {
        val redactionPatterns = listOf(
            Regex("""(?i)\b(?:otp|one\s*time\s*password)\b"""),
            Regex("""(?i)\b(?:upi\s*pin|card\s*pin|pin)\b"""),
            Regex("""(?i)\b(?:cvv|cvc)\b"""),
            Regex("""(?i)\b(?:password|passcode)\b"""),
            Regex("""(?i)\b(?:captcha|biometric|fingerprint|face\s*unlock)\b"""),
            Regex("""(?i)\b(?:access\s*token|api\s*key|private\s*auth\s*code)\b"""),
            Regex("""(?i)\b(?:banking\s*secret|bank\s*secret)\b"""),
            Regex("""\b(?:\d[ -]*?){13,19}\b"""),
            Regex("""(?i)\b[\w._%+-]+@[\w.-]+\.[a-z]{2,}\b"""),
            Regex("""(?i)\b(?:\+?\d[\d\s-]{7,}\d)\b""")
        )

        val sensitiveKeywords = listOf(
            "otp",
            "password",
            "passcode",
            "cvv",
            "cvc",
            "upi pin",
            "card pin",
            "captcha",
            "biometric",
            "access token",
            "api key",
            "private auth code",
            "banking secret"
        )
    }
}
