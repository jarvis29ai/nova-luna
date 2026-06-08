package com.nova.luna.voice

import java.util.regex.Pattern

class VoiceResponseSanitizer {
    private val otpPattern = Pattern.compile("\\b\\d{4,8}\\b")
    private val cardPattern = Pattern.compile("\\d{4}[- ]\\d{4}[- ]\\d{4}[- ]\\d{4}")
    private val emailPattern = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b")

    fun sanitize(text: String, allowSensitive: Boolean = false): String {
        if (allowSensitive) return text

        var sanitized = text
        
        // Mask Card Numbers (More specific, should run first)
        sanitized = cardPattern.matcher(sanitized).replaceAll("a card number")

        // Mask OTPs
        val otpMatcher = otpPattern.matcher(sanitized)
        if (otpMatcher.find()) {
            sanitized = otpMatcher.replaceAll("a code")
        }

        // Mask Emails
        val emailMatcher = emailPattern.matcher(sanitized)
        if (emailMatcher.find()) {
            sanitized = emailMatcher.replaceAll("an email address")
        }

        return sanitized
    }
}
