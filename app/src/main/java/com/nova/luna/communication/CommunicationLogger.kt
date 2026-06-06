package com.nova.luna.communication

import android.util.Log

class CommunicationLogger {
    fun logState(state: CommunicationFlowState) {
        Log.d("CommunicationModel", "State changed to: $state")
    }

    fun logError(message: String, throwable: Throwable? = null) {
        Log.e("CommunicationModel", "Error: $message", throwable)
    }

    fun logAction(action: String) {
        Log.i("CommunicationModel", "Action: $action")
    }
}
