package com.nova.luna.media

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.service.NovaAccessibilityService
import com.nova.luna.util.AccessibilityNodeUtils

class MediaAccessibilityService {
    private val TAG = "MediaAccessibilitySvc"

    fun getCurrentApp(): String? {
        val root = NovaAccessibilityService.instance?.rootInActiveWindow ?: return null
        return root.packageName?.toString()
    }

    fun search(query: String): Boolean {
        val instance = NovaAccessibilityService.instance ?: return false
        val root = instance.rootInActiveWindow ?: return false
        
        val searchBox = findSearchBox(root)
        if (searchBox != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
            }
            searchBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            searchBox.performAction(AccessibilityNodeInfo.ACTION_CLICK) // Sometimes needed to trigger search
            // Also try to find and click search button
            findSearchButton(root)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return false
    }

    fun readVisibleResults(): List<MediaVisibleItem> {
        val root = NovaAccessibilityService.instance?.rootInActiveWindow ?: return emptyList()
        val items = mutableListOf<MediaVisibleItem>()
        
        // This is a simplified version. A real implementation would traverse the tree
        // and look for specific patterns in YouTube/Instagram/etc.
        val clickableNodes = findClickableNodes(root)
        clickableNodes.take(5).forEachIndexed { index, node ->
            items.add(MediaVisibleItem(
                title = node.text?.toString() ?: node.contentDescription?.toString() ?: "Item ${index + 1}",
                creator = null, // Hard to extract without app-specific logic
                index = index,
                nodeId = null
            ))
        }
        return items
    }

    fun selectItem(index: Int): Boolean {
        val root = NovaAccessibilityService.instance?.rootInActiveWindow ?: return false
        val clickableNodes = findClickableNodes(root)
        if (index in clickableNodes.indices) {
            return clickableNodes[index].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return false
    }

    fun performPlaybackAction(action: MediaPlaybackControl): Boolean {
        val instance = NovaAccessibilityService.instance ?: return false
        return when (action) {
            MediaPlaybackControl.PLAY, MediaPlaybackControl.RESUME -> instance.clickByTextOrDescription("Play") || instance.clickByTextOrDescription("Resume")
            MediaPlaybackControl.PAUSE -> instance.clickByTextOrDescription("Pause")
            MediaPlaybackControl.FORWARD -> instance.clickByTextOrDescription("Forward") || instance.clickByTextOrDescription("Fast forward")
            MediaPlaybackControl.BACKWARD -> instance.clickByTextOrDescription("Rewind") || instance.clickByTextOrDescription("Back")
            MediaPlaybackControl.FULL_SCREEN -> instance.clickByTextOrDescription("Full screen")
            MediaPlaybackControl.EXIT_FULL_SCREEN -> instance.clickByTextOrDescription("Exit full screen")
            MediaPlaybackControl.NEXT -> instance.clickByTextOrDescription("Next")
            MediaPlaybackControl.PREVIOUS -> instance.clickByTextOrDescription("Previous")
            else -> false
        }
    }

    fun scroll(direction: MediaScrollDirection): Boolean {
        val instance = NovaAccessibilityService.instance ?: return false
        return when (direction) {
            MediaScrollDirection.DOWN -> instance.scrollForward()
            MediaScrollDirection.UP -> instance.scrollBackward()
            else -> false
        }
    }

    private fun findSearchBox(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return AccessibilityNodeUtils.findEditableNode(root) 
            ?: AccessibilityNodeUtils.findNodeByTextOrDescription(root, "Search")
    }

    private fun findSearchButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return AccessibilityNodeUtils.findClickableNode(root, "Search")
    }

    private fun findClickableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun traverse(node: AccessibilityNodeInfo) {
            if (node.isClickable && (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank())) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { traverse(it) }
            }
        }
        traverse(root)
        return result
    }
}
