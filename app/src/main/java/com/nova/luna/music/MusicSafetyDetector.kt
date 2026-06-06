package com.nova.luna.music

/**
 * Detects sensitive or unsafe content and actions in the music domain.
 */
class MusicSafetyDetector {

    fun isExplicit(result: MusicSearchResult): Boolean {
        return result.isExplicit
    }

    fun needsManualAction(screenContent: String): Boolean {
        val normalized = screenContent.lowercase()
        return normalized.contains("login") || 
               normalized.contains("sign in") || 
               normalized.contains("otp") || 
               normalized.contains("password") || 
               normalized.contains("captcha") || 
               normalized.contains("subscribe") || 
               normalized.contains("premium") || 
               normalized.contains("payment") || 
               normalized.contains("pay ") || 
               normalized.contains("credit card") || 
               normalized.contains("card details") || 
               normalized.contains("upi pin")
    }

    fun isExplicitWarning(screenContent: String): Boolean {
        val normalized = screenContent.lowercase()
        return normalized.contains("explicit") || 
               normalized.contains("parental advisory") || 
               normalized.contains("age restricted")
    }
}
