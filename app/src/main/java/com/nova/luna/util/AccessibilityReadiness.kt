package com.nova.luna.util

object AccessibilityReadiness {
    const val BLOCKED_BY_ACCESSIBILITY_NOT_READY = "blocked_by_accessibility_not_ready"
    
    fun isBound(): Boolean {
        return com.nova.luna.service.NovaAccessibilityService.instance != null
    }
    
    fun blockedMessage(): String {
        return "Accessibility service is not ready. Please enable it in settings to proceed."
    }
}