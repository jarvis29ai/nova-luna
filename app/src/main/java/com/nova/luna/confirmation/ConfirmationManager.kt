package com.nova.luna.confirmation

import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ConfirmationManager {
    private val activeConfirmations = ConcurrentHashMap<String, ConfirmationRequest>()
    private val timeoutMs = 60000L // 1 minute

    fun createConfirmation(request: ConfirmationRequest) {
        activeConfirmations[request.confirmationId] = request
    }

    fun getPendingConfirmation(confirmationId: String): ConfirmationRequest? {
        val request = activeConfirmations[confirmationId] ?: return null
        if (System.currentTimeMillis() - request.createdAt > timeoutMs) {
            activeConfirmations.remove(confirmationId)
            return null
        }
        return request
    }

    fun confirm(confirmationId: String): ConfirmationResult {
        if (!activeConfirmations.containsKey(confirmationId)) {
            return ConfirmationResult(ConfirmationStatus.BLOCKED, "Invalid or expired confirmation ID.")
        }
        activeConfirmations.remove(confirmationId)
        return ConfirmationResult(ConfirmationStatus.CONFIRMED, "Action confirmed.")
    }

    fun cancel(confirmationId: String): ConfirmationResult {
        activeConfirmations.remove(confirmationId)
        return ConfirmationResult(ConfirmationStatus.CANCELLED, "Action cancelled.")
    }
    
    fun clear() {
        activeConfirmations.clear()
    }
}
