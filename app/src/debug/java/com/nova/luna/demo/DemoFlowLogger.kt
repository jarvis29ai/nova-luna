package com.nova.luna.demo

import android.util.Log

object DemoFlowLogger {
    private const val TAG = "DemoFlow"

    fun log(event: DemoFlowEvent, data: Map<String, Any?> = emptyMap()) {
        val message = data.entries.joinToString(separator = ", ") { "${it.key}=${it.value}" }
        Log.i(TAG, "[$event] $message")
    }

    fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
