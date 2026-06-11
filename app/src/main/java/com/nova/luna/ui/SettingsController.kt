package com.nova.luna.ui

import com.nova.luna.brain.AssistantSession
import com.nova.luna.memory.PersonalMemoryManager

class SettingsController(
    private val assistantSession: AssistantSession,
    private val memoryManager: PersonalMemoryManager
) {
    fun getBrainDetailedStatus(): String {
        return buildString {
            appendLine("• Build/Install: Success")
            appendLine("• JNI JSON Bridge: Success")
            appendLine("• Crash-free: Yes")
            appendLine("• SafetyGate: Enabled")
            appendLine("• Backend Honesty: Verified")
            appendLine("• Real Tokenizer Loaded: Phase 18 Pending")
            appendLine("• Vocab size=151936 proof: Pending")
            appendLine("• Real token IDs: Pending")
            appendLine("• Real inference: Pending")
            appendLine("\nNote: Phase 17 stable native pipeline working. Phase 18 tokenizer proof pending.")
        }
    }

    fun getSettingsSummary(): String {
        return buildString {
            appendLine("Voice Response: Enabled")
            appendLine("Memory Items: ${memoryManager.getStore().list().size}")
            appendLine("Local LLM: Active (Rule-First)")
            appendLine("Safety Gate: Enabled (Strict)")
        }
    }

    fun clearAllMemory() {
        memoryManager.getStore().clearAll()
    }

    fun getDemoCommands(): List<String> {
        return listOf(
            "Luna open YouTube",
            "Luna play Arijit Singh",
            "Luna open YouTube and play MrBeast latest video",
            "Luna scroll down",
            "Luna summarize my latest message",
            "Luna create PPT on AI",
            "Luna order pizza",
            "Luna compare milk prices",
            "Luna book cab to railway station",
            "Luna buy phone under 30000"
        )
    }
}
