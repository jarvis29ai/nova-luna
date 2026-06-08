package com.nova.luna.model

import java.util.Locale
import java.util.UUID

data class CommandIntent(
    val id: String = UUID.randomUUID().toString(),
    val rawText: String,
    val normalizedText: String = rawText.lowercase(Locale.US).trim(),
    val intentType: IntentType = IntentType.UNKNOWN,
    val actionType: ActionType = ActionType.UNKNOWN,
    val entities: Map<String, String> = emptyMap(),
    val targetApp: String? = entities["packageName"] ?: entities["appName"],
    val targetLabel: String? = entities["text"] ?: entities["query"] ?: entities["label"],
    val inputText: String? = entities["text"] ?: entities["inputText"],
    val scrollDirection: String? = entities["direction"],
    val riskLevel: BrainRiskLevel = BrainRiskLevel.SAFE,
    val requiresConfirmation: Boolean = false,
    val sourceDomain: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 2,
    val metadata: Map<String, String> = emptyMap()
)

