package com.nova.luna.memory

class MemorySensitivityClassifier {
    fun classify(type: MemoryType, key: String, value: String): MemorySensitivity {
        // BLOCKED: Passwords, OTPs, PINs, CVVs, Card Numbers
        if (isSensitiveBlocked(key, value)) {
            return MemorySensitivity.SENSITIVE_BLOCKED
        }

        return when (type) {
            MemoryType.PREFERRED_APP,
            MemoryType.LANGUAGE_PREFERENCE,
            MemoryType.VOICE_STYLE,
            MemoryType.MUSIC_PREFERENCE,
            MemoryType.CONTENT_CREATION_PREFERENCE -> MemorySensitivity.LOW

            MemoryType.BUDGET_PREFERENCE,
            MemoryType.SHOPPING_PREFERENCE,
            MemoryType.GROCERY_PREFERENCE,
            MemoryType.FOOD_PREFERENCE,
            MemoryType.FOOD_RESTRICTION,
            MemoryType.CAB_PREFERENCE,
            MemoryType.COMMUNICATION_PREFERENCE -> MemorySensitivity.MEDIUM

            MemoryType.HOME_LABEL,
            MemoryType.WORK_LABEL,
            MemoryType.USER_NOTE -> MemorySensitivity.HIGH

            else -> MemorySensitivity.MEDIUM
        }
    }

    private fun isSensitiveBlocked(key: String, value: String): Boolean {
        val sensitiveKeywords = listOf(
            "password", "pwd", "otp", "pin", "cvv", "card number", "credit card", 
            "debit card", "upi pin", "bank account", "secret", "token", "api key"
        )
        
        val k = key.lowercase()
        val v = value.lowercase()

        if (sensitiveKeywords.any { k.contains(it) || v.contains(it) }) return true

        // Basic regex for common sensitive patterns
        val otpPattern = "\\b\\d{4,8}\\b".toRegex() // 4-8 digit numbers often used as OTP
        val cardNumberPattern = "\\b(?:\\d[ -]*?){13,16}\\b".toRegex()

        // If it's a small number like 300 (budget), it's fine. 
        // If it's 6 digits and the context is "my otp is", it should be blocked.
        // We rely on keywords more than just numbers to avoid false positives for budgets.
        
        if (k.contains("otp") && otpPattern.containsMatchIn(v)) return true
        if (cardNumberPattern.containsMatchIn(v.replace(" ", ""))) return true

        return false
    }
}
