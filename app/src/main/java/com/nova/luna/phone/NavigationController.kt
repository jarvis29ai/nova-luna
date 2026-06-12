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

    fun goHome(): NavigationStatus = wrapServiceCall { it.goHome() }
    fun goBack(): NavigationStatus = wrapServiceCall { it.goBack() }
    fun openRecents(): NavigationStatus = wrapServiceCall { it.openRecents() }
    fun openNotifications(): NavigationStatus = wrapServiceCall { it.openNotifications() }

    private fun wrapServiceCall(call: (NovaAccessibilityService) -> Boolean): NavigationStatus {
        val service = NovaAccessibilityService.instance ?: return NavigationStatus.ACCESSIBILITY_NOT_READY
        return if (call(service)) {
            NavigationStatus.SUCCESS
        } else {
            NavigationStatus.FAILED
        }
    }
}
