package com.nova.luna.communication

class CommunicationVoiceResponses {

    fun getSummaryResponse(summary: CommunicationSummary): CommunicationFinalSummary {
        val voiceText = summary.overallSummary
        val importantSenders = summary.importantMessages.map { it.sender.name }.distinct().take(3)
        val senderText = if (importantSenders.isNotEmpty()) {
            "\nImportant from: ${importantSenders.joinToString(", ")}"
        } else ""
        
        val popupText = "Summarized today's messages.${senderText}\n\n" + 
                summary.platformSummaries.joinToString("\n") { "${it.platform.name}: ${it.messageCount} messages" }
        
        return CommunicationFinalSummary(
            popupText = popupText,
            voiceText = voiceText,
            status = CommunicationStatus.SUCCESS
        )
    }

    fun getCancelResponse(): CommunicationFinalSummary {
        return CommunicationFinalSummary(
            popupText = "Action cancelled. Draft discarded.",
            voiceText = "Okay, I've cancelled that and discarded the draft.",
            status = CommunicationStatus.CANCELLED
        )
    }

    fun getSaveDraftResponse(): CommunicationFinalSummary {
        return CommunicationFinalSummary(
            popupText = "Draft saved successfully.",
            voiceText = "I've saved your draft.",
            status = CommunicationStatus.SUCCESS
        )
    }

    fun getSearchResponse(result: MessageSearchResult): CommunicationFinalSummary {
        return if (result.matches.isEmpty()) {
            CommunicationFinalSummary(
                popupText = "No matching messages found.",
                voiceText = "I couldn't find any matching messages.",
                status = CommunicationStatus.SUCCESS
            )
        } else {
            CommunicationFinalSummary(
                popupText = "Found ${result.totalCount} matches.",
                voiceText = "I found ${result.totalCount} matching messages.",
                status = CommunicationStatus.SUCCESS
            )
        }
    }

    fun getDraftCreatedResponse(draft: Any): CommunicationFinalSummary {
        return CommunicationFinalSummary(
            popupText = "Draft created. Should I send it?",
            voiceText = "I've created a draft. Should I send it?",
            status = CommunicationStatus.NEEDS_CONFIRMATION
        )
    }

    fun getErrorResponse(reason: String): CommunicationFinalSummary {
        return CommunicationFinalSummary(
            popupText = "Error: $reason",
            voiceText = "Sorry, I encountered an error: $reason",
            status = CommunicationStatus.FAILED
        )
    }

    fun getBlockedResponse(status: CommunicationPermissionStatus): CommunicationFinalSummary {
        val reason = when (status) {
            CommunicationPermissionStatus.BLOCKED_BY_SMS_PERMISSION -> "SMS permission is missing."
            CommunicationPermissionStatus.BLOCKED_BY_ACCESSIBILITY_NOT_READY -> "Accessibility service or notification access is not enabled."
            CommunicationPermissionStatus.BLOCKED_BY_GMAIL_ACCESS -> "Gmail access is not configured."
            else -> "Access is blocked."
        }
        return CommunicationFinalSummary(
            popupText = reason,
            voiceText = "I cannot perform this action because $reason",
            status = CommunicationStatus.BLOCKED
        )
    }
}
