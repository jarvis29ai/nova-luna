package com.nova.luna.util

import android.util.Log

object PerformanceTracker {
    private const val TAG = "PerformanceTracker"
    private val startTimes = mutableMapOf<String, Long>()

    fun start(event: String) {
        startTimes[event] = System.currentTimeMillis()
    }

    fun stop(event: String) {
        val start = startTimes[event] ?: return
        val duration = System.currentTimeMillis() - start
        Log.d(TAG, "Event: $event took $duration ms")
        startTimes.remove(event)
    }

    fun clear() {
        startTimes.clear()
    }
}
