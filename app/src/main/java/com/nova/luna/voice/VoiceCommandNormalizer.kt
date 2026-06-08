package com.nova.luna.voice

import java.util.Locale

class VoiceCommandNormalizer {
    
    private val wakeWords = listOf(
        "luna",
        "nova",
        "hey luna",
        "hey nova",
        "okay luna",
        "okay nova",
        "ok luna",
        "ok nova",
        "हेलो लूना", // Hello Luna in Hindi
        "नमस्ते लूना", // Namaste Luna in Hindi
        "सुनो लूना" // Listen Luna in Hindi
    )

    fun normalize(rawTranscript: String): String {
        var cleaned = rawTranscript.trim()
            .replace(Regex("\\s+"), " ") // Remove repeated spaces
            .lowercase(Locale.getDefault())

        // Check for wake words at the beginning
        for (wakeWord in wakeWords) {
            if (cleaned.startsWith(wakeWord)) {
                cleaned = cleaned.removePrefix(wakeWord).trim()
                // Also handle cases like "Luna," or "Luna "
                cleaned = cleaned.removePrefix(",").removePrefix(".").trim()
                break
            }
        }

        return cleaned
    }

    fun isWakeWordDetected(rawTranscript: String): Boolean {
        val cleaned = rawTranscript.trim().lowercase(Locale.getDefault())
        return wakeWords.any { cleaned.startsWith(it) }
    }

    fun isValidCommand(cleanedCommand: String): Boolean {
        return cleanedCommand.isNotBlank() && cleanedCommand.length > 1
    }
}
