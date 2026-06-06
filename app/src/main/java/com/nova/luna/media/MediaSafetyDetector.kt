package com.nova.luna.media

import com.nova.luna.service.NovaAccessibilityService

class MediaSafetyDetector {
    fun isSafeToProceed(): Boolean {
        val root = NovaAccessibilityService.instance?.rootInActiveWindow ?: return true
        
        val textToBlock = listOf(
            "payment", "subscribe", "buy", "rent", "purchase",
            "login", "sign in", "otp", "password", "captcha",
            "credit card", "debit card", "upi", "cvv"
        )
        
        // Traverse and check for blocking keywords
        return !containsBlockingText(root, textToBlock)
    }

    private fun containsBlockingText(node: android.view.accessibility.AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val text = (node.text?.toString() ?: "") + (node.contentDescription?.toString() ?: "")
        if (keywords.any { text.contains(it, ignoreCase = true) }) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && containsBlockingText(child, keywords)) return true
        }
        return false
    }
}
