package com.nova.luna.communication

class CommunicationImportanceRanker {

    fun rank(message: CommunicationMessage): MessageImportance {
        val content = message.content.lowercase()
        
        return when {
            isCritical(content) -> MessageImportance.CRITICAL
            isImportant(content) -> MessageImportance.IMPORTANT
            isPromotional(content) -> MessageImportance.PROMOTIONAL
            isSpam(content) -> MessageImportance.SPAM
            else -> MessageImportance.NORMAL
        }
    }

    fun isSensitive(message: CommunicationMessage): Boolean {
        val content = message.content.lowercase()
        return content.contains("otp") || 
               content.contains("verification code") || 
               content.contains("password") || 
               content.contains("pin") ||
               content.contains("cvv") ||
               content.contains("aadhaar") ||
               content.contains("bank account")
    }

    private fun isCritical(text: String): Boolean {
        return text.contains("urgent") || text.contains("asap") || text.contains("emergency") || 
               text.contains("otp") || text.contains("verification code")
    }

    private fun isImportant(text: String): Boolean {
        return text.contains("interview") || text.contains("meeting") || text.contains("invoice") || 
               text.contains("payment due") || text.contains("deadline") || text.contains("offer letter")
    }

    private fun isPromotional(text: String): Boolean {
        return text.contains("sale") || text.contains("offer") || text.contains("discount") || 
               text.contains("win") || text.contains("lottery")
    }

    private fun isSpam(text: String): Boolean {
        return text.contains("unsubscribe") || text.contains("click here")
    }
}
