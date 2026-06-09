package com.nova.luna.util

import android.content.Context
import android.os.Build
import com.nova.luna.memory.PersonalMemoryStore

class SafeDiagnosticsReporter(
    private val context: Context,
    private val readinessChecker: PrototypeReadinessChecker,
    private val healthMonitor: RuntimeHealthMonitor,
    private val memoryStore: PersonalMemoryStore,
    private val brainDownloadReportProvider: (() -> String)? = null
) {
    fun generateReport(): String {
        val readiness = readinessChecker.check()
        return buildString {
            appendLine("=== Nova/Luna Diagnostic Report ===")
            appendLine("App Version: 1.0.0-Prototype")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine()
            appendLine("--- Readiness Status ---")
            appendLine("Overall: ${readiness.overallStatus}")
            appendLine("Permissions: ${readiness.permissionsStatus}")
            appendLine("Accessibility: ${readiness.accessibilityStatus}")
            appendLine("Local LLM: ${readiness.localLlmStatus}")
            appendLine()
            appendLine("--- Health Status ---")
            appendLine(healthMonitor.getStatusSummary())
            appendLine()
            appendLine("--- Memory Status ---")
            appendLine("Items Count: ${memoryStore.list().size}")
            appendLine()
            appendLine("--- AI Brain Downloads ---")
            val brainSection = runCatching { brainDownloadReportProvider?.invoke()?.trim() }
                .getOrNull()
            appendLine(brainSection?.takeIf { it.isNotBlank() } ?: "Unavailable")
            appendLine()
            appendLine("--- Missing Requirements ---")
            if (readiness.missingRequirements.isEmpty()) appendLine("None")
            readiness.missingRequirements.forEach { appendLine("- $it") }
            appendLine()
            appendLine("--- Required Actions ---")
            if (readiness.userActionsNeeded.isEmpty()) appendLine("None")
            readiness.userActionsNeeded.forEach { appendLine("- $it") }
            appendLine("===================================")
        }
    }
}
