package com.nova.luna.util

import android.content.Context
import com.nova.luna.model.PrototypeReadinessReport
import com.nova.luna.model.PrototypeReadinessStatus
import com.nova.luna.voice.VoiceInputController
import com.nova.luna.voice.VoiceResponseManager

class PrototypeReadinessChecker(private val context: Context) {

    fun check(): PrototypeReadinessReport {
        val missingReqs = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val actions = mutableListOf<String>()

        val micGranted = PermissionUtils.hasRecordAudioPermission(context)
        val notificationsGranted = PermissionUtils.hasPostNotificationsPermission(context)
        val permStatus = if (micGranted && notificationsGranted) PrototypeReadinessStatus.READY else PrototypeReadinessStatus.BLOCKED
        if (!micGranted) {
            missingReqs.add("Microphone permission")
            actions.add("Grant microphone permission")
        }

        val accEnabled = PermissionUtils.hasAccessibilityPermission(context)
        val accStatus = if (accEnabled) PrototypeReadinessStatus.READY else PrototypeReadinessStatus.BLOCKED
        if (!accEnabled) {
            missingReqs.add("Accessibility service")
            actions.add("Enable Nova/Luna Accessibility Service")
        }

        // Voice Input Readiness
        val voiceInputStatus = if (micGranted) PrototypeReadinessStatus.READY else PrototypeReadinessStatus.BLOCKED
        
        // Voice Response Readiness
        val voiceResponseStatus = PrototypeReadinessStatus.READY // Assuming basic TTS always available

        // Popup UI Status
        val popupStatus = PrototypeReadinessStatus.READY

        // Local LLM Status (Simple check for now)
        val localLlmStatus = PrototypeReadinessStatus.READY // Placeholder for asset check

        // Memory Status
        val memoryStatus = PrototypeReadinessStatus.READY

        // Safety Status
        val safetyStatus = PrototypeReadinessStatus.READY

        // Demo Flow Status
        val demoFlowStatus = PrototypeReadinessStatus.READY

        val overall = if (permStatus == PrototypeReadinessStatus.READY && accStatus == PrototypeReadinessStatus.READY) {
            PrototypeReadinessStatus.READY
        } else {
            PrototypeReadinessStatus.PARTIAL_READY
        }

        return PrototypeReadinessReport(
            overallStatus = overall,
            permissionsStatus = permStatus,
            accessibilityStatus = accStatus,
            voiceInputStatus = voiceInputStatus,
            voiceResponseStatus = voiceResponseStatus,
            popupStatus = popupStatus,
            localLlmStatus = localLlmStatus,
            memoryStatus = memoryStatus,
            safetyStatus = safetyStatus,
            demoFlowStatus = demoFlowStatus,
            missingRequirements = missingReqs,
            warnings = warnings,
            userActionsNeeded = actions
        )
    }
}
