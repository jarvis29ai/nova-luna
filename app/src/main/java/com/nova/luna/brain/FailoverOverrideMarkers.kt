package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole

/**
 * Single source of truth for Phase 35 debug/test-only failover override marker file names.
 * Both FailoverDebugReceiver (writer, debug source set) and ModelInstallBrainRouterBridge (reader) must use this.
 */
object FailoverOverrideMarkers {
    private val ROLE_TO_MARKER: Map<BrainModelRole, String> = mapOf(
        BrainModelRole.CORE_BRAIN to "force_core_off",
        BrainModelRole.MULTILINGUAL_BACKUP to "force_full_off",
        BrainModelRole.LITE_FALLBACK to "force_lite_off"
    )

    fun markerFileName(role: BrainModelRole): String? = ROLE_TO_MARKER[role]

    fun allMarkerFileNames(): List<String> = ROLE_TO_MARKER.values.toList()
}
