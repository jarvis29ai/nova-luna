package com.nova.luna.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.util.AccessibilityNodeUtils
import java.util.ArrayDeque

class NovaAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: NovaAccessibilityService? = null
            private set

        private const val TAG = "NovaAccessibilitySvc"
    }

    data class NotificationSnapshot(
        val packageName: String,
        val text: String,
        val timestampMillis: Long
    )

    private val recentNotifications = ArrayDeque<String>()
    private val capturedNotifications = mutableListOf<NotificationSnapshot>()
    private val notificationLock = Any()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        Log.i(TAG, "Accessibility service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val summary = buildNotificationSummary(event)
            if (summary.isNotBlank()) {
                synchronized(notificationLock) {
                    if (recentNotifications.size >= 5) {
                        recentNotifications.removeFirst()
                    }
                    recentNotifications.addLast(summary)

                    capturedNotifications.add(
                        NotificationSnapshot(
                            packageName = event.packageName?.toString() ?: "unknown",
                            text = summary,
                            timestampMillis = System.currentTimeMillis()
                        )
                    )
                    if (capturedNotifications.size > 50) {
                        capturedNotifications.removeAt(0)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun getCapturedNotifications(platform: String? = null): List<NotificationSnapshot> {
        return synchronized(notificationLock) {
            if (platform == null) {
                capturedNotifications.toList()
            } else {
                val pkgPart = when (platform.lowercase()) {
                    "whatsapp" -> "whatsapp"
                    "telegram" -> "telegram"
                    else -> platform.lowercase()
                }
                capturedNotifications.filter { it.packageName.contains(pkgPart, ignoreCase = true) }
            }
        }
    }

    fun latestNotificationSummary(): String? {
        return synchronized(notificationLock) {
            recentNotifications.lastOrNull()
        }
    }

    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME).also { success ->
            if (!success) Log.w(TAG, "goHome failed.")
        }
    }

    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK).also { success ->
            if (!success) Log.w(TAG, "goBack failed.")
        }
    }

    fun openRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS).also { success ->
            if (!success) Log.w(TAG, "openRecents failed.")
        }
    }

    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS).also { success ->
            if (!success) Log.w(TAG, "openNotifications failed.")
        }
    }

    fun clickByTextOrDescription(query: String): Boolean {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "clickByTextOrDescription failed: rootInActiveWindow is null.")
            return false
        }

        val target = AccessibilityNodeUtils.findClickableNode(root, query)
        if (target == null) {
            Log.w(TAG, "clickByTextOrDescription failed: no clickable node for \"$query\".")
            return false
        }

        val success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!success) {
            Log.w(TAG, "clickByTextOrDescription failed: ACTION_CLICK returned false for \"$query\".")
        }
        return success
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "scrollForward failed: rootInActiveWindow is null.")
            return false
        }

        val target = AccessibilityNodeUtils.findScrollableNode(root) ?: root
        val success = target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        if (!success) {
            Log.w(TAG, "scrollForward failed: no scrollable node handled the action.")
        }
        return success
    }

    fun scrollBackward(): Boolean {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "scrollBackward failed: rootInActiveWindow is null.")
            return false
        }

        val target = AccessibilityNodeUtils.findScrollableNode(root) ?: root
        val success = target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        if (!success) {
            Log.w(TAG, "scrollBackward failed: no scrollable node handled the action.")
        }
        return success
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "typeText failed: rootInActiveWindow is null.")
            return false
        }

        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = focusedNode ?: AccessibilityNodeUtils.findEditableNode(root)
        if (target == null) {
            Log.w(TAG, "typeText failed: no editable node was found.")
            return false
        }

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        val success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!success) {
            Log.w(TAG, "typeText failed: ACTION_SET_TEXT returned false.")
        }
        return success
    }

    private fun buildNotificationSummary(event: AccessibilityEvent): String {
        val parts = buildList {
            event.text?.forEach { text ->
                val value = text.toString().trim()
                if (value.isNotBlank()) add(value)
            }
            event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            event.packageName?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { add("from $it") }
        }
        return parts.joinToString(separator = " ").trim()
    }
}
