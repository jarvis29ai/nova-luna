package com.nova.luna.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import com.nova.luna.screen.ScreenState
import com.nova.luna.screen.ScreenStateReader
import com.nova.luna.screen.ScreenStateVerifier
import com.nova.luna.screen.ScreenVerificationResult
import com.nova.luna.util.AccessibilityNodeUtils
import java.util.ArrayDeque

open class NovaAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: NovaAccessibilityService? = null
            private set

        fun setTestInstance(testInstance: NovaAccessibilityService?) {
            instance = testInstance
        }

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
    private val screenStateReader = ScreenStateReader()
    private val screenStateVerifier = ScreenStateVerifier()

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

    fun captureScreenState(maxNodes: Int = ScreenStateReader.DEFAULT_MAX_NODES): ScreenState? {
        return screenStateReader.captureScreenState(this, maxNodes)
    }

    fun describeActiveScreen(maxNodes: Int = ScreenStateReader.DEFAULT_MAX_NODES): String? {
        return captureScreenState(maxNodes)?.summarizedState
    }

    fun verifyScreenTransition(
        before: ScreenState?,
        after: ScreenState?,
        commandIntent: CommandIntent,
        commandResult: CommandResult
    ): ScreenVerificationResult {
        return screenStateVerifier.verify(before, after, commandIntent, commandResult)
    }

    open fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME).also { success ->
            if (!success) Log.w(TAG, "goHome failed.")
        }
    }

    open fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK).also { success ->
            if (!success) Log.w(TAG, "goBack failed.")
        }
    }

    open fun openRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS).also { success ->
            if (!success) Log.w(TAG, "openRecents failed.")
        }
    }

    open fun openNotifications(): Boolean {
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

        return performClick(target)
    }

    fun clickByContentDescription(query: String): Boolean {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "clickByContentDescription failed: rootInActiveWindow is null.")
            return false
        }

        val target = AccessibilityNodeUtils.findClickableNodeByDescription(root, query)
        if (target == null) {
            Log.w(TAG, "clickByContentDescription failed: no clickable node for \"$query\".")
            return false
        }

        return performClick(target)
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!success) {
            Log.w(TAG, "performClick failed: ACTION_CLICK returned false.")
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
            Log.w(TAG, "typeText failed: ACTION_SET_TEXT returned false. Trying focus + paste.")
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val pasteArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, pasteArgs)
        }
        return success
    }

    fun waitForText(text: String, timeoutMs: Long = 5000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val snapshot = captureScreenState()
            if (snapshot != null && snapshot.visibleText.any { it.contains(text, ignoreCase = true) }) {
                return true
            }
            try { Thread.sleep(500) } catch (e: InterruptedException) { break }
        }
        return false
    }

    fun waitForApp(packageName: String, timeoutMs: Long = 5000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val root = rootInActiveWindow
            if (root != null && root.packageName?.toString() == packageName) {
                root.recycle()
                return true
            }
            root?.recycle()
            try { Thread.sleep(500) } catch (e: InterruptedException) { break }
        }
        return false
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
