package com.nova.luna.shopping

import android.view.accessibility.AccessibilityEvent
import com.nova.luna.util.AccessibilityNodeUtils

class ShoppingAccessibilityService {
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Implementation for reading shopping app screens
    }

    fun findPriceInNode(rootNode: android.view.accessibility.AccessibilityNodeInfo?): String? {
        // Helper to find price on screen
        return null
    }
}
