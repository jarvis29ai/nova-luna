package com.nova.luna.util

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

object AccessibilityNodeUtils {
    fun findNodeByTextOrDescription(
        root: AccessibilityNodeInfo?,
        query: String
    ): AccessibilityNodeInfo? {
        if (root == null || query.isBlank()) return null

        val normalizedQuery = FuzzyMatcher.normalize(query)
        val text = root.text?.toString().orEmpty()
        val description = root.contentDescription?.toString().orEmpty()

        if (matches(text, normalizedQuery) || matches(description, normalizedQuery)) {
            return root
        }

        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findNodeByTextOrDescription(child, query)
            if (match != null) {
                return match
            }
        }
        return null
    }

    fun findClickableNode(root: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        val node = findNodeByTextOrDescription(root, query) ?: return null
        return node.findClickableAncestor()
    }

    fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isScrollable) return root

        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findScrollableNode(child)
            if (match != null) {
                return match
            }
        }
        return null
    }

    fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isEditable || isEditText(root)) return root

        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val match = findEditableNode(child)
            if (match != null) {
                return match
            }
        }
        return null
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

    private fun AccessibilityNodeInfo.findClickableAncestor(): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = this
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun matches(source: String, query: String): Boolean {
        val normalizedSource = FuzzyMatcher.normalize(source)
        return normalizedSource.contains(query)
    }

    private fun isEditText(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty().lowercase(Locale.US)
        return className.contains("edittext") || className.contains("textfield")
    }
}
