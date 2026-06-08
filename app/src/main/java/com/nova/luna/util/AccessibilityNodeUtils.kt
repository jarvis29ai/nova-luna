package com.nova.luna.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.screen.ScreenElementType
import com.nova.luna.screen.ScreenNode
import com.nova.luna.screen.ScreenRiskSignal
import com.nova.luna.screen.ScreenTreeSnapshot
import java.util.Locale

object AccessibilityNodeUtils {
    private const val DEFAULT_MAX_SCREEN_NODES = 250
    private const val DEFAULT_MAX_SCREEN_DEPTH = 30

    fun findNodeByTextOrDescription(
        root: AccessibilityNodeInfo?,
        query: String
    ): AccessibilityNodeInfo? {
        if (root == null || query.isBlank()) return null

        val normalizedQuery = FuzzyMatcher.normalize(query)
        return findNodeByTextOrDescription(root, normalizedQuery, visited = mutableSetOf(), depth = 0)
    }

    fun findNodeByDescription(
        root: AccessibilityNodeInfo?,
        query: String
    ): AccessibilityNodeInfo? {
        if (root == null || query.isBlank()) return null

        val normalizedQuery = FuzzyMatcher.normalize(query)
        return findNodeByDescription(root, normalizedQuery, visited = mutableSetOf(), depth = 0)
    }

    fun findClickableNode(root: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        val node = findNodeByTextOrDescription(root, query) ?: return null
        return node.findClickableAncestor()
    }

    fun findClickableNodeByDescription(root: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        val node = findNodeByDescription(root, query) ?: return null
        return node.findClickableAncestor()
    }

    fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return findScrollableNode(root, visited = mutableSetOf(), depth = 0)
    }

    fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return findEditableNode(root, visited = mutableSetOf(), depth = 0)
    }

    fun collectClickableNodeLabels(root: AccessibilityNodeInfo?, labels: MutableSet<String> = mutableSetOf()): List<String> {
        if (root == null) return labels.toList()

        if (root.isClickable) {
            val text = root.text?.toString()?.trim()
            val description = root.contentDescription?.toString()?.trim()
            if (!text.isNullOrBlank()) labels.add(text)
            if (!description.isNullOrBlank()) labels.add(description)
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            collectClickableNodeLabels(child, labels)
        }

        return labels.toList()
    }

    fun collectScreenTree(
        root: AccessibilityNodeInfo?,
        maxNodes: Int = DEFAULT_MAX_SCREEN_NODES,
        maxDepth: Int = DEFAULT_MAX_SCREEN_DEPTH
    ): ScreenTreeSnapshot {
        if (root == null || maxNodes <= 0 || maxDepth < 0) {
            return ScreenTreeSnapshot(emptyList(), 0, truncated = false)
        }

        val nodes = mutableListOf<ScreenNode>()
        val visited = mutableSetOf<Int>()
        var rawNodeCount = 0
        var truncated = false

        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || truncated || depth > maxDepth) return

            val identity = System.identityHashCode(node)
            if (!visited.add(identity)) return

            rawNodeCount += 1
            if (nodes.size >= maxNodes) {
                truncated = true
                return
            }

            nodes.add(node.toScreenNode(depth))

            for (index in 0 until node.childCount) {
                if (truncated) break
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    runCatching { child.recycle() }
                }
            }
        }

        visit(root, 0)
        return ScreenTreeSnapshot(nodes = nodes, rawNodeCount = rawNodeCount, truncated = truncated)
    }

    private fun AccessibilityNodeInfo.findClickableAncestor(): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = this
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun findNodeByTextOrDescription(
        root: AccessibilityNodeInfo,
        query: String,
        visited: MutableSet<Int>,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > DEFAULT_MAX_SCREEN_DEPTH) return null

        val identity = System.identityHashCode(root)
        if (!visited.add(identity)) return null

        val text = root.text?.toString().orEmpty()
        val description = root.contentDescription?.toString().orEmpty()
        val normalizedQuery = query

        if (matches(text, normalizedQuery) || matches(description, normalizedQuery)) {
            return root
        }

        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findNodeByTextOrDescription(child, query, visited, depth + 1)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun findNodeByDescription(
        root: AccessibilityNodeInfo,
        query: String,
        visited: MutableSet<Int>,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > DEFAULT_MAX_SCREEN_DEPTH) return null

        val identity = System.identityHashCode(root)
        if (!visited.add(identity)) return null

        val description = root.contentDescription?.toString().orEmpty()
        val normalizedQuery = query

        if (matches(description, normalizedQuery)) {
            return root
        }

        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findNodeByDescription(child, query, visited, depth + 1)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun findScrollableNode(
        root: AccessibilityNodeInfo?,
        visited: MutableSet<Int>,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (root == null || depth > DEFAULT_MAX_SCREEN_DEPTH) return null

        val identity = System.identityHashCode(root)
        if (!visited.add(identity)) return null

        if (root.isScrollable) return root

        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findScrollableNode(child, visited, depth + 1)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun findEditableNode(
        root: AccessibilityNodeInfo?,
        visited: MutableSet<Int>,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (root == null || depth > DEFAULT_MAX_SCREEN_DEPTH) return null

        val identity = System.identityHashCode(root)
        if (!visited.add(identity)) return null

        if (root.isEditable || isEditText(root)) return root

        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findEditableNode(child, visited, depth + 1)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun AccessibilityNodeInfo.toScreenNode(depth: Int): ScreenNode {
        val rawText = text?.toString()?.trim()
        val rawDescription = contentDescription?.toString()?.trim()
        val classNameText = className?.toString()?.trim()
        val packageNameText = packageName?.toString()?.trim()
        val viewIdText = viewIdResourceName?.toString()?.trim()
        val riskLabels = inferRiskLabels(
            text = rawText.orEmpty(),
            contentDescription = rawDescription.orEmpty(),
            className = classNameText.orEmpty(),
            viewIdResourceName = viewIdText.orEmpty()
        )
        val bounds = Rect().also { getBoundsInScreen(it) }
        val safeText = sanitizeNodeText(rawText, riskLabels)
        val safeDescription = sanitizeNodeDescription(rawDescription, riskLabels)

        return ScreenNode(
            text = safeText,
            contentDescription = safeDescription,
            className = classNameText?.takeIf { it.isNotBlank() },
            packageName = packageNameText?.takeIf { it.isNotBlank() },
            viewIdResourceName = viewIdText?.takeIf { it.isNotBlank() },
            bounds = Rect(bounds),
            isClickable = isClickable,
            isEditable = isEditable,
            isScrollable = isScrollable,
            isEnabled = isEnabled,
            isFocused = isFocused,
            isSelected = isSelected,
            depth = depth,
            childCount = childCount,
            semanticRole = inferSemanticRole(
                text = rawText.orEmpty(),
                contentDescription = rawDescription.orEmpty(),
                className = classNameText.orEmpty(),
                riskLabels = riskLabels,
                isClickable = isClickable,
                isEditable = isEditable,
                isScrollable = isScrollable
            ),
            riskLabels = riskLabels
        )
    }

    private fun matches(source: String, query: String): Boolean {
        val normalizedSource = FuzzyMatcher.normalize(source)
        return normalizedSource.contains(query)
    }

    private fun sanitizeNodeText(rawText: String?, riskLabels: List<ScreenRiskSignal>): String? {
        val value = rawText?.trim().orEmpty()
        if (value.isBlank()) return null
        if (containsSensitiveRisk(riskLabels)) return null
        return value.take(180)
    }

    private fun sanitizeNodeDescription(rawDescription: String?, riskLabels: List<ScreenRiskSignal>): String? {
        val value = rawDescription?.trim().orEmpty()
        if (value.isBlank()) return null
        return if (containsSensitiveRisk(riskLabels)) {
            value.take(180)
        } else {
            value.take(180)
        }
    }

    private fun containsSensitiveRisk(riskLabels: List<ScreenRiskSignal>): Boolean {
        return riskLabels.any {
            it == ScreenRiskSignal.OTP ||
                it == ScreenRiskSignal.PASSWORD ||
                it == ScreenRiskSignal.PIN ||
                it == ScreenRiskSignal.CVV ||
                it == ScreenRiskSignal.CAPTCHA ||
                it == ScreenRiskSignal.BIOMETRIC
        }
    }

    private fun inferSemanticRole(
        text: String,
        contentDescription: String,
        className: String,
        riskLabels: List<ScreenRiskSignal>,
        isClickable: Boolean,
        isEditable: Boolean,
        isScrollable: Boolean
    ): ScreenElementType {
        val combined = buildString {
            append(text)
            append(' ')
            append(contentDescription)
            append(' ')
            append(className)
        }.lowercase(Locale.US)

        return when {
            isEditable && riskLabels.contains(ScreenRiskSignal.PASSWORD) -> ScreenElementType.PASSWORD_FIELD
            isEditable && riskLabels.contains(ScreenRiskSignal.OTP) -> ScreenElementType.OTP_FIELD
            isEditable && riskLabels.contains(ScreenRiskSignal.PAYMENT) -> ScreenElementType.PAYMENT_FIELD
            isEditable && combined.contains("search") -> ScreenElementType.SEARCH_FIELD
            isEditable -> ScreenElementType.INPUT
            isScrollable && (combined.contains("list") || combined.contains("recycler")) -> ScreenElementType.LIST
            isScrollable && combined.contains("card") -> ScreenElementType.CARD
            isScrollable -> ScreenElementType.LIST
            isClickable && (combined.contains("button") || combined.contains("submit") || combined.contains("continue") || combined.contains("done")) -> ScreenElementType.BUTTON
            combined.contains("checkbox") -> ScreenElementType.CHECKBOX
            combined.contains("switch") -> ScreenElementType.SWITCH
            combined.contains("tab") -> ScreenElementType.TAB
            combined.contains("toolbar") -> ScreenElementType.TOOLBAR
            combined.contains("menu") -> ScreenElementType.MENU_ITEM
            combined.contains("link") -> ScreenElementType.LINK
            combined.contains("slider") -> ScreenElementType.SLIDER
            combined.contains("dialog") || combined.contains("alert") -> ScreenElementType.DIALOG
            combined.contains("progress") || combined.contains("loading") -> ScreenElementType.PROGRESS
            combined.contains("image") -> ScreenElementType.IMAGE
            isClickable -> ScreenElementType.BUTTON
            else -> ScreenElementType.TEXT
        }
    }

    private fun inferRiskLabels(
        text: String,
        contentDescription: String,
        className: String,
        viewIdResourceName: String
    ): List<ScreenRiskSignal> {
        val combined = buildString {
            append(text)
            append(' ')
            append(contentDescription)
            append(' ')
            append(className)
            append(' ')
            append(viewIdResourceName)
        }.lowercase(Locale.US)

        val labels = mutableListOf<ScreenRiskSignal>()
        if (containsAny(combined, listOf("login", "log in", "sign in", "signin", "continue with", "email", "phone"))) {
            labels.add(ScreenRiskSignal.LOGIN)
        }
        if (containsAny(combined, listOf("payment", "pay now", "pay", "checkout", "checkout now", "place order", "complete payment", "card", "wallet", "upi", "billing", "amount"))) {
            labels.add(ScreenRiskSignal.PAYMENT)
        }
        if (containsAny(combined, listOf("otp", "one time password", "verification code", "verification otp"))) {
            labels.add(ScreenRiskSignal.OTP)
        }
        if (containsAny(combined, listOf("password", "passcode", "pin", "cvv", "cvc"))) {
            labels.add(ScreenRiskSignal.PASSWORD)
        }
        if (containsAny(combined, listOf("pin", "upi pin", "card pin"))) {
            labels.add(ScreenRiskSignal.PIN)
        }
        if (containsAny(combined, listOf("cvv", "cvc"))) {
            labels.add(ScreenRiskSignal.CVV)
        }
        if (containsAny(combined, listOf("captcha", "not a robot", "i'm not a robot", "human verification", "robot verification"))) {
            labels.add(ScreenRiskSignal.CAPTCHA)
        }
        if (containsAny(combined, listOf("biometric", "fingerprint", "face unlock", "face id", "face recognition"))) {
            labels.add(ScreenRiskSignal.BIOMETRIC)
        }
        if (containsAny(combined, listOf("permission", "allow", "deny", "grant", "while using the app", "only this time", "not now"))) {
            labels.add(ScreenRiskSignal.PERMISSION)
        }
        if (containsAny(combined, listOf("loading", "please wait", "processing", "progress", "checking", "syncing", "fetching"))) {
            labels.add(ScreenRiskSignal.LOADING)
        }
        if (containsAny(combined, listOf("error", "failed", "failure", "something went wrong", "try again", "unable", "invalid", "not working"))) {
            labels.add(ScreenRiskSignal.ERROR)
        }
        if (isEditableField(text, className) && labels.isNotEmpty()) {
            labels.add(ScreenRiskSignal.SENSITIVE_FIELD)
        }

        return labels.distinct()
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun isEditableField(text: String, className: String): Boolean {
        val normalizedClass = className.lowercase(Locale.US)
        val normalizedText = text.lowercase(Locale.US)
        return normalizedClass.contains("edittext") ||
            normalizedClass.contains("textfield") ||
            normalizedClass.contains("input") ||
            normalizedText.contains("enter ") ||
            normalizedText.contains("type ") ||
            normalizedText.contains("write ")
    }

    private fun isEditText(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty().lowercase(Locale.US)
        return className.contains("edittext") || className.contains("textfield")
    }
}
