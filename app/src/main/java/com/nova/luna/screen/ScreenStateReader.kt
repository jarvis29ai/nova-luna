package com.nova.luna.screen

import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.service.NovaAccessibilityService
import com.nova.luna.util.AccessibilityNodeUtils
import com.nova.luna.util.AccessibilityReadiness

open class ScreenStateReader(
    private val analyzer: ScreenStateAnalyzer = ScreenStateAnalyzer()
) {
    open fun captureScreenState(
        service: NovaAccessibilityService? = NovaAccessibilityService.instance,
        maxNodes: Int = DEFAULT_MAX_NODES
    ): ScreenState? {
        val accessibilityService = service ?: return null
        val root = accessibilityService.rootInActiveWindow ?: return null

        return try {
            buildScreenState(accessibilityService, root, maxNodes)
        } finally {
            root.recycle()
        }
    }

    open fun buildScreenState(
        service: NovaAccessibilityService,
        root: AccessibilityNodeInfo,
        maxNodes: Int = DEFAULT_MAX_NODES
    ): ScreenState {
        val tree = AccessibilityNodeUtils.collectScreenTree(root, maxNodes, DEFAULT_MAX_DEPTH)
        val packageName = root.packageName?.toString()?.trim()
            .takeIf { !it.isNullOrBlank() }
            ?: service.packageName?.trim().orEmpty().ifBlank { "unknown" }
        val appName = resolveAppName(service, packageName)
        val className = root.className?.toString()?.trim()?.takeIf { it.isNotBlank() }

        return analyzer.analyze(
            packageName = packageName,
            appName = appName,
            className = className,
            timestampMillis = System.currentTimeMillis(),
            isAccessibilityReady = AccessibilityReadiness.isBound(),
            nodes = tree.nodes,
            rawNodeCount = tree.rawNodeCount,
            truncated = tree.truncated
        )
    }

    private fun resolveAppName(service: NovaAccessibilityService, packageName: String): String? {
        if (packageName.isBlank()) return null

        return runCatching {
            val info = service.packageManager.getApplicationInfo(packageName, 0)
            service.packageManager.getApplicationLabel(info)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_MAX_NODES = 250
        const val DEFAULT_MAX_DEPTH = 30
    }
}
