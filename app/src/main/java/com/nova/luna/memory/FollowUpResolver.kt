package com.nova.luna.memory

data class FollowUpResolution(
    val kind: FollowUpKind,
    val rawText: String,
    val normalizedText: String,
    val sessionType: BrainSessionType? = null,
    val selectedIndex: Int? = null,
    val matchedKeyword: String? = null,
    val confidence: Double = 0.5
) {
    val isSessionContinuation: Boolean
        get() = sessionType != null && kind != FollowUpKind.NONE
}

enum class FollowUpKind {
    NONE,
    CONFIRMATION,
    DENIAL,
    CONTINUE,
    CANCEL,
    CHEAPEST,
    BEST,
    FIRST_OPTION,
    SECOND_OPTION,
    THIRD_OPTION,
    ADD_ITEM,
    REMOVE_ITEM,
    REPLACE_ITEM,
    COMPARE,
    SEARCH_AGAIN,
    REORDER,
    SEND,
    BOOK,
    RETRY
}

class FollowUpResolver {
    fun resolve(
        rawText: String,
        snapshot: BrainMemorySnapshot,
        preferredSessionType: BrainSessionType? = null
    ): FollowUpResolution? {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) {
            return null
        }

        val sessionType = preferredSessionType ?: snapshot.activeSessionType()
        val directConfirmation = resolveGenericConfirmation(normalized)
        if (directConfirmation != FollowUpKind.NONE) {
            return FollowUpResolution(
                kind = directConfirmation,
                rawText = rawText,
                normalizedText = normalized,
                sessionType = sessionType,
                confidence = 0.95
            )
        }

        if (sessionType == null) {
            return null
        }

        val sessionSpecific = resolveForSession(sessionType, normalized)
        return if (sessionSpecific.kind == FollowUpKind.NONE) {
            null
        } else {
            sessionSpecific.copy(
                rawText = rawText,
                normalizedText = normalized,
                sessionType = sessionType
            )
        }
    }

    private fun resolveForSession(sessionType: BrainSessionType, normalized: String): FollowUpResolution {
        if (normalized in cancelPhrases) {
            return FollowUpResolution(
                kind = FollowUpKind.CANCEL,
                rawText = "",
                normalizedText = normalized,
                sessionType = sessionType,
                confidence = 0.95
            )
        }

        if (normalized in continuePhrases) {
            return FollowUpResolution(
                kind = FollowUpKind.CONTINUE,
                rawText = "",
                normalizedText = normalized,
                sessionType = sessionType,
                confidence = 0.92
            )
        }

        when (sessionType) {
            BrainSessionType.GROCERY,
            BrainSessionType.SHOPPING -> {
                when {
                    normalized.contains("cheapest") -> return resolution(sessionType, FollowUpKind.CHEAPEST, normalized, "cheapest", 0.95)
                    normalized.contains("best") -> return resolution(sessionType, FollowUpKind.BEST, normalized, "best", 0.9)
                    normalized.contains("first one") || normalized == "first" || normalized == "one" -> return resolution(sessionType, FollowUpKind.FIRST_OPTION, normalized, "first one", 0.85, 1)
                    normalized.contains("second one") || normalized == "second" -> return resolution(sessionType, FollowUpKind.SECOND_OPTION, normalized, "second one", 0.8, 2)
                    normalized.contains("third one") || normalized == "third" -> return resolution(sessionType, FollowUpKind.THIRD_OPTION, normalized, "third one", 0.8, 3)
                    normalized.contains("compare") -> return resolution(sessionType, FollowUpKind.COMPARE, normalized, "compare", 0.85)
                    normalized.contains("add") -> return resolution(sessionType, FollowUpKind.ADD_ITEM, normalized, "add", 0.75)
                    normalized.contains("remove") -> return resolution(sessionType, FollowUpKind.REMOVE_ITEM, normalized, "remove", 0.75)
                    normalized.contains("replace") -> return resolution(sessionType, FollowUpKind.REPLACE_ITEM, normalized, "replace", 0.75)
                    normalized.contains("search again") || normalized.contains("again") -> return resolution(sessionType, FollowUpKind.SEARCH_AGAIN, normalized, "again", 0.75)
                    normalized.contains("reorder") -> return resolution(sessionType, FollowUpKind.REORDER, normalized, "reorder", 0.75)
                    normalized.contains("book") -> return resolution(sessionType, FollowUpKind.BOOK, normalized, "book", 0.8)
                    normalized.contains("send") -> return resolution(sessionType, FollowUpKind.SEND, normalized, "send", 0.8)
                }
            }

            BrainSessionType.CAB,
            BrainSessionType.FOOD,
            BrainSessionType.CONTENT,
            BrainSessionType.COMMUNICATION,
            BrainSessionType.PHONE -> {
                when {
                    normalized.contains("book") -> return resolution(sessionType, FollowUpKind.BOOK, normalized, "book", 0.8)
                    normalized.contains("send") -> return resolution(sessionType, FollowUpKind.SEND, normalized, "send", 0.8)
                    normalized.contains("retry") -> return resolution(sessionType, FollowUpKind.RETRY, normalized, "retry", 0.8)
                }
            }

            BrainSessionType.MUSIC,
            BrainSessionType.MEDIA -> {
                when {
                    normalized.contains("next") || normalized.contains("skip") -> return resolution(sessionType, FollowUpKind.CONTINUE, normalized, "next", 0.85)
                    normalized.contains("previous") -> return resolution(sessionType, FollowUpKind.RETRY, normalized, "previous", 0.75)
                }
            }

            BrainSessionType.SCREEN,
            BrainSessionType.BASIC_CONTROL,
            BrainSessionType.ONLINE_HELPER,
            BrainSessionType.LOCAL_LLM,
            BrainSessionType.UNKNOWN -> {
                when {
                    normalized.contains("continue") -> return resolution(sessionType, FollowUpKind.CONTINUE, normalized, "continue", 0.8)
                    normalized.contains("retry") -> return resolution(sessionType, FollowUpKind.RETRY, normalized, "retry", 0.8)
                }
            }
        }

        return FollowUpResolution(
            kind = FollowUpKind.NONE,
            rawText = "",
            normalizedText = normalized,
            sessionType = sessionType,
            confidence = 0.1
        )
    }

    private fun resolveGenericConfirmation(normalized: String): FollowUpKind {
        return when {
            normalized in affirmativePhrases -> FollowUpKind.CONFIRMATION
            normalized in denialPhrases -> FollowUpKind.DENIAL
            else -> FollowUpKind.NONE
        }
    }

    private fun resolution(
        sessionType: BrainSessionType,
        kind: FollowUpKind,
        normalized: String,
        matchedKeyword: String,
        confidence: Double,
        selectedIndex: Int? = null
    ): FollowUpResolution {
        return FollowUpResolution(
            kind = kind,
            rawText = "",
            normalizedText = normalized,
            sessionType = sessionType,
            selectedIndex = selectedIndex,
            matchedKeyword = matchedKeyword,
            confidence = confidence
        )
    }

    private fun normalize(value: String): String {
        return value.lowercase().trim().replace(Regex("\\s+"), " ")
    }

    private companion object {
        val affirmativePhrases = setOf(
            "yes",
            "yeah",
            "yep",
            "sure",
            "ok",
            "okay",
            "proceed",
            "please proceed",
            "yes please"
        )

        val denialPhrases = setOf(
            "no",
            "nope",
            "cancel",
            "not now",
            "not yet"
        )

        val cancelPhrases = setOf(
            "cancel",
            "stop",
            "nevermind",
            "never mind",
            "forget it"
        )

        val continuePhrases = setOf(
            "continue",
            "go on",
            "proceed",
            "next",
            "resume"
        )
    }
}
