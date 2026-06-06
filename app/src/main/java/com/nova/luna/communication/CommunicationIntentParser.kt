package com.nova.luna.communication

class CommunicationIntentParser {

    fun parse(text: String): CommunicationRequest {
        val normalized = text.lowercase().trim()
        
        return when {
            isSummarizeAllToday(normalized) -> CommunicationRequest(
                rawText = text,
                commandType = CommunicationCommandType.SUMMARIZE_ALL_TODAY
            )
            isSummarizePlatform(normalized) -> CommunicationRequest(
                rawText = text,
                commandType = CommunicationCommandType.SUMMARIZE_ONE_PLATFORM,
                targetPlatform = detectPlatform(normalized)
            )
            isSummarizeSingleMessage(normalized) -> CommunicationRequest(
                rawText = text,
                commandType = CommunicationCommandType.SUMMARIZE_SINGLE_LONG_MESSAGE,
                senderName = extractSenderName(normalized),
                targetPlatform = detectPlatform(normalized)
            )
            isSearchCommand(normalized) -> CommunicationRequest(
                rawText = text,
                commandType = CommunicationCommandType.FIND_MESSAGE,
                searchQuery = extractSearchQuery(normalized),
                targetPlatform = detectPlatform(normalized)
            )
            isDraftEmail(normalized) -> CommunicationRequest(
                rawText = text,
                commandType = CommunicationCommandType.DRAFT_EMAIL,
                tone = detectTone(normalized),
                draftInstruction = text,
                targetPlatform = CommunicationPlatform.GMAIL
            )
            isDraftReply(normalized) -> CommunicationRequest(
                rawText = text,
                commandType = CommunicationCommandType.DRAFT_REPLY,
                senderName = extractSenderName(normalized),
                tone = detectTone(normalized),
                draftInstruction = text,
                targetPlatform = detectPlatform(normalized)
            )
            isSendConfirmation(normalized) -> CommunicationRequest(
                rawText = text,
                commandType = CommunicationCommandType.SEND_REPLY
            )
            isCancelIntent(normalized) -> CommunicationRequest(
                rawText = text,
                commandType = CommunicationCommandType.CANCEL
            )
            else -> CommunicationRequest(rawText = text, commandType = CommunicationCommandType.UNKNOWN)
        }
    }

    private fun isSummarizeAllToday(text: String): Boolean {
        return text.contains("summarize today") || 
               text.contains("tell me all today") || 
               text.contains("what did i miss today") ||
               text.contains("summarize my messages") ||
               text.contains("read my important messages") ||
               (text.contains("summarize") && text.contains("all") && text.contains("messages"))
    }

    private fun isSummarizePlatform(text: String): Boolean {
        return (text.contains("summarize") || text.contains("read")) && 
               (text.contains("whatsapp") || text.contains("gmail") || text.contains("sms") || text.contains("telegram"))
    }

    private fun isSummarizeSingleMessage(text: String): Boolean {
        return text.contains("summarize") && (text.contains("long message") || text.contains("from"))
    }

    private fun isSearchCommand(text: String): Boolean {
        return text.contains("find") || text.contains("search") || text.contains("messages about")
    }

    private fun isDraftReply(text: String): Boolean {
        return text.contains("reply to") || text.contains("draft reply") || text.contains("write a reply")
    }

    private fun isDraftEmail(text: String): Boolean {
        return text.contains("email") && (text.contains("draft") || text.contains("write") || text.contains("send"))
    }

    private fun isSendConfirmation(text: String): Boolean {
        return text == "send it" || text == "yes send" || text == "confirm" || text == "send" || text == "yes send it" || text == "please send it"
    }

    private fun isCancelIntent(text: String): Boolean {
        return text == "cancel" || text == "don't send" || text == "stop" || text == "discard" || text == "nevermind"
    }

    private fun detectPlatform(text: String): CommunicationPlatform {
        return when {
            text.contains("whatsapp") -> CommunicationPlatform.WHATSAPP
            text.contains("gmail") -> CommunicationPlatform.GMAIL
            text.contains("sms") || text.contains("message") -> CommunicationPlatform.SMS
            text.contains("telegram") -> CommunicationPlatform.TELEGRAM
            else -> CommunicationPlatform.UNKNOWN
        }
    }

    private fun detectTone(text: String): DraftTone {
        return when {
            text.contains("formal") || text.contains("professional") -> DraftTone.FORMAL
            text.contains("informal") || text.contains("casual") -> DraftTone.INFORMAL
            text.contains("polite") -> DraftTone.POLITE
            text.contains("friendly") -> DraftTone.FRIENDLY
            text.contains("short") -> DraftTone.SHORT
            else -> DraftTone.USER_STYLE
        }
    }

    private fun extractSenderName(text: String): String? {
        val keywords = listOf("from ", "to ", "message from ", "reply to ")
        for (keyword in keywords) {
            val index = text.indexOf(keyword)
            if (index != -1) {
                val sub = text.substring(index + keyword.length).trim()
                return sub.split(" ").firstOrNull()
            }
        }
        return null
    }

    private fun extractSearchQuery(text: String): String? {
        val keywords = listOf("about ", "for ", "find ", "search ")
        for (keyword in keywords) {
            val index = text.indexOf(keyword)
            if (index != -1) {
                return text.substring(index + keyword.length).trim()
            }
        }
        return null
    }
}
