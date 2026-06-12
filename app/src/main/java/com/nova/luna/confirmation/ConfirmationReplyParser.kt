package com.nova.luna.confirmation

import java.util.Locale

class ConfirmationReplyParser {

    fun parseReply(text: String): ConfirmationAction {
        val normalized = text.lowercase(Locale.US).trim()
        
        val confirmTerms = listOf("yes", "confirm", "proceed", "go ahead", "book it", "order it", "send it", "haan", "ha", "okay", "ok", "kar do", "theek hai")
        val cancelTerms = listOf("no", "cancel", "stop", "don't", "mat karo", "nahi", "cancel karo", "ruk jao")
        
        if (confirmTerms.any { normalized.contains(it) }) {
            return ConfirmationAction.CONFIRM
        }
        
        if (cancelTerms.any { normalized.contains(it) }) {
            return ConfirmationAction.CANCEL
        }
        
        return ConfirmationAction.UNKNOWN
    }
}

enum class ConfirmationAction {
    CONFIRM,
    CANCEL,
    UNKNOWN
}
