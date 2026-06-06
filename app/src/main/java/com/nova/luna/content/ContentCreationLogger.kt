package com.nova.luna.content

import android.util.Log

class ContentCreationLogger {
    private val TAG = "ContentCreationModel"

    fun logState(state: ContentFlowState) {
        Log.d(TAG, "State: $state")
    }

    fun logAction(action: String) {
        Log.i(TAG, "Action: $action")
    }

    fun logError(message: String, e: Throwable? = null) {
        Log.e(TAG, "Error: $message", e)
    }

    fun logStatus(status: ContentCreationStatus) {
        Log.d(TAG, "Status: $status")
    }
}
