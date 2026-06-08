package com.nova.luna.memory

import com.nova.luna.model.CommandResult
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType

class PersonalMemoryManager(
    private val store: PersonalMemoryStore,
    private val detector: MemoryIntentDetector = MemoryIntentDetector(),
    private val classifier: MemorySensitivityClassifier = MemorySensitivityClassifier()
) {
    private var pendingMemoryItem: PersonalMemoryItem? = null

    fun handleMemoryCommand(command: String): MemoryOperationResult {
        val decision = detector.detect(command) ?: return MemoryOperationResult(
            status = MemoryPermissionStatus.NOT_FOUND,
            action = MemoryAction.IGNORE,
            technicalReason = "No memory intent detected"
        )

        val sensitivity = classifier.classify(decision.type, decision.key, decision.value)
        
        if (sensitivity == MemorySensitivity.SENSITIVE_BLOCKED) {
            return MemoryOperationResult(
                status = MemoryPermissionStatus.BLOCKED_SENSITIVE,
                action = decision.action,
                userMessage = "I cannot save sensitive information like passwords or OTPs for safety reasons."
            )
        }

        return when (decision.action) {
            MemoryAction.SAVE, MemoryAction.UPDATE -> handleSaveUpdate(decision, sensitivity, command)
            MemoryAction.DELETE -> handleDelete(decision)
            MemoryAction.VIEW -> handleView(decision)
            MemoryAction.CLEAR_ALL -> handleClearAll(decision)
            else -> MemoryOperationResult(MemoryPermissionStatus.DENIED, MemoryAction.IGNORE)
        }
    }

    private fun handleSaveUpdate(decision: MemoryDecision, sensitivity: MemorySensitivity, command: String): MemoryOperationResult {
        val item = PersonalMemoryItem(
            type = decision.type,
            key = decision.key,
            value = decision.value,
            sensitivity = sensitivity,
            sourceCommand = command,
            userConfirmed = !decision.needsConfirmation && sensitivity != MemorySensitivity.HIGH
        )

        if (decision.needsConfirmation || sensitivity == MemorySensitivity.HIGH) {
            pendingMemoryItem = item
            return MemoryOperationResult(
                status = MemoryPermissionStatus.NEEDS_CONFIRMATION,
                action = decision.action,
                memoryItem = item,
                userMessage = decision.userMessage ?: "Should I remember this preference?"
            )
        }

        return store.save(item)
    }

    private fun handleDelete(decision: MemoryDecision): MemoryOperationResult {
        return store.deleteByTypeAndKey(decision.type, decision.key)
    }

    private fun handleView(decision: MemoryDecision): MemoryOperationResult {
        val items = if (decision.type == MemoryType.UNKNOWN) {
            store.list()
        } else {
            store.list(type = decision.type)
        }
        
        if (items.isEmpty()) {
            return MemoryOperationResult(
                status = MemoryPermissionStatus.ALLOWED,
                action = MemoryAction.VIEW,
                userMessage = "I don't have any preferences saved for that yet."
            )
        }

        val summary = items.joinToString("\n") { "${it.type}: ${it.key} = ${it.value}" }
        return MemoryOperationResult(
            status = MemoryPermissionStatus.ALLOWED,
            action = MemoryAction.VIEW,
            items = items,
            userMessage = "Here's what I remember:\n$summary"
        )
    }

    private fun handleClearAll(decision: MemoryDecision): MemoryOperationResult {
        if (decision.needsConfirmation) {
            return MemoryOperationResult(
                status = MemoryPermissionStatus.NEEDS_CONFIRMATION,
                action = MemoryAction.CLEAR_ALL,
                userMessage = decision.userMessage ?: "Clear all saved memory?"
            )
        }
        return store.clearAll()
    }

    fun confirmPendingMemorySave(): MemoryOperationResult {
        val item = pendingMemoryItem ?: return MemoryOperationResult(
            status = MemoryPermissionStatus.NOT_FOUND,
            action = MemoryAction.SAVE,
            technicalReason = "No pending memory item"
        )
        
        val result = store.save(item.copy(userConfirmed = true))
        pendingMemoryItem = null
        return result
    }

    fun cancelPendingMemorySave() {
        pendingMemoryItem = null
    }

    fun getRelevantMemoryForDomain(domain: String): String {
        val type = when (domain.uppercase()) {
            "MUSIC" -> MemoryType.MUSIC_PREFERENCE
            "CAB" -> MemoryType.CAB_PREFERENCE
            "FOOD" -> MemoryType.FOOD_PREFERENCE
            "GROCERY" -> MemoryType.GROCERY_PREFERENCE
            "SHOPPING" -> MemoryType.SHOPPING_PREFERENCE
            else -> null
        }
        
        val items = mutableListOf<PersonalMemoryItem>()
        type?.let { items.addAll(store.list(type = it)) }
        items.addAll(store.list(type = MemoryType.PREFERRED_APP))
        items.addAll(store.list(type = MemoryType.LANGUAGE_PREFERENCE))
        
        if (domain.uppercase() == "CAB") {
            items.addAll(store.list(type = MemoryType.HOME_LABEL))
            items.addAll(store.list(type = MemoryType.WORK_LABEL))
        }

        return items.filter { it.isEnabled }
            .joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun getStore(): PersonalMemoryStore = store
}
