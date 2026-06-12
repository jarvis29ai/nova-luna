package com.nova.luna.screen

import android.view.accessibility.AccessibilityNodeInfo

interface AccessibilityScreenReader {
    fun readScreen(): ScreenSnapshot?
}
