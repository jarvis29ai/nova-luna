package com.nova.luna.screen

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.service.NovaAccessibilityService

class AndroidAccessibilityScreenReader(
    private val serviceProvider: () -> NovaAccessibilityService?
) : AccessibilityScreenReader {

    companion object {
        private const val TAG = "ScreenReader"
        private const val MAX_DEPTH = 15
        private const val MAX_NODES = 200
    }

    override fun readScreen(): ScreenSnapshot? {
        val service = serviceProvider()
        if (service == null) {
            Log.w(TAG, "Accessibility service is not available")
            return null
        }

        val root = service.rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "Root in active window is null")
            return null
        }

        val elements = mutableListOf<ScreenElement>()
        val screenText = mutableListOf<String>()
        val packageName = root.packageName?.toString() ?: "unknown"
        val className = root.className?.toString()

        traverse(root, elements, screenText, 0, 0, "root")

        val timestamp = System.currentTimeMillis()
        val scrollable = elements.filter { it.isScrollable }
        val editable = elements.filter { it.isEditable || it.type == ScreenElementType.TEXT_FIELD || it.type == ScreenElementType.SEARCH_FIELD }
        val clickable = elements.filter { it.isClickable }
        val focused = elements.firstOrNull { it.isFocused }

        // Naive risk signal detection for now, Classifier handles it properly later
        val allText = screenText.joinToString(" ").lowercase()
        val riskSignals = listOf("pay", "checkout", "password", "otp", "captcha", "delete", "remove account").filter {
            allText.contains(it)
        }

        return ScreenSnapshot(
            packageName = packageName,
            appName = resolveAppName(packageName),
            className = className,
            timestamp = timestamp,
            screenText = screenText.distinct(),
            elements = elements,
            focusedElement = focused,
            scrollableContainers = scrollable,
            editableFields = editable,
            clickableElements = clickable,
            detectedScreenType = ScreenType.UNKNOWN, // Classifier will set this
            riskSignals = riskSignals
        )
    }

    private fun traverse(
        node: AccessibilityNodeInfo?,
        elements: MutableList<ScreenElement>,
        textAccumulator: MutableList<String>,
        depth: Int,
        index: Int,
        path: String
    ) {
        if (node == null || depth > MAX_DEPTH || elements.size > MAX_NODES) return

        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val cName = node.className?.toString()
        val id = node.viewIdResourceName
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val isEnabled = node.isEnabled
        val isFocused = node.isFocused
        val isScrollable = node.isScrollable
        val isPassword = node.isPassword

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (text != null && text.isNotBlank()) textAccumulator.add(text)
        if (contentDesc != null && contentDesc.isNotBlank()) textAccumulator.add(contentDesc)

        val type = determineType(node, text, contentDesc, cName, isClickable, isEditable)

        // Only add meaningful elements
        if (text != null || contentDesc != null || isClickable || isEditable || isScrollable) {
            elements.add(
                ScreenElement(
                    id = id,
                    text = text,
                    contentDescription = contentDesc,
                    className = cName,
                    bounds = bounds,
                    isClickable = isClickable,
                    isEditable = isEditable,
                    isEnabled = isEnabled,
                    isFocused = isFocused,
                    isScrollable = isScrollable,
                    isPassword = isPassword,
                    type = type,
                    confidence = 1.0f,
                    path = path
                )
            )
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            traverse(node.getChild(i), elements, textAccumulator, depth + 1, i, "$path/$i")
        }
    }

    private fun determineType(
        node: AccessibilityNodeInfo,
        text: String?,
        contentDesc: String?,
        cName: String?,
        isClickable: Boolean,
        isEditable: Boolean
    ): ScreenElementType {
        val lowerText = text?.lowercase() ?: ""
        val lowerDesc = contentDesc?.lowercase() ?: ""
        val lowerClass = cName?.lowercase() ?: ""
        val lowerId = node.viewIdResourceName?.lowercase() ?: ""

        if (isEditable || lowerClass.contains("edittext")) {
            return if (lowerText.contains("search") || lowerDesc.contains("search") || lowerId.contains("search")) {
                ScreenElementType.SEARCH_FIELD
            } else if (node.isPassword) {
                ScreenElementType.PASSWORD_FIELD
            } else {
                ScreenElementType.TEXT_FIELD
            }
        }

        if (isClickable || lowerClass.contains("button")) {
            return ScreenElementType.BUTTON
        }

        if (lowerClass.contains("checkbox")) return ScreenElementType.CHECKBOX
        if (lowerClass.contains("switch")) return ScreenElementType.SWITCH
        if (lowerClass.contains("image") || lowerClass.contains("icon")) return ScreenElementType.IMAGE
        if (lowerClass.contains("listview") || lowerClass.contains("recyclerview")) return ScreenElementType.LIST
        if (node.isScrollable) return ScreenElementType.SCROLL_CONTAINER

        if (text != null || contentDesc != null) return ScreenElementType.TEXT

        return ScreenElementType.UNKNOWN
    }

    private fun resolveAppName(packageName: String): String? {
        return when {
            packageName.contains("youtube") -> "YouTube"
            packageName.contains("chrome") -> "Chrome"
            packageName.contains("google.android.googlequicksearchbox") -> "Google Search"
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("telegram") -> "Telegram"
            packageName.contains("settings") -> "Settings"
            packageName.contains("dialer") -> "Phone"
            packageName.contains("messaging") -> "Messages"
            packageName.contains("uber") -> "Uber"
            packageName.contains("zomato") -> "Zomato"
            packageName.contains("swiggy") -> "Swiggy"
            packageName.contains("blinkit") -> "Swiggy"
            else -> null
        }
    }
}
