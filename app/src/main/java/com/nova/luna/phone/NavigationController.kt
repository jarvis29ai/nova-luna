package com.nova.luna.phone

import android.accessibilityservice.AccessibilityService
import com.nova.luna.service.NovaAccessibilityService

class NavigationController {

    enum class NavigationStatus {
        SUCCESS,
        ACCESSIBILITY_NOT_READY,
        FAILED,
        UNSUPPORTED
    }

    fun goHome(): NavigationStatus = performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    fun goBack(): NavigationStatus = performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    fun openRecents(): NavigationStatus = performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    fun openNotifications(): NavigationStatus = performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    private fun performGlobalAction(action: Int): NavigationStatus {
        val service = NovaAccessibilityService.instance ?: return NavigationStatus.ACCESSIBILITY_NOT_READY
        return if (service.performGlobalAction(action)) {
            NavigationStatus.SUCCESS
        } else {
            NavigationStatus.FAILED
        }
    }
}
