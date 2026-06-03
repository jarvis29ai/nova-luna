package com.nova.luna.model

import java.util.Locale

data class CommandIntent(
    val rawText: String,
    val normalizedText: String = rawText.lowercase(Locale.US).trim(),
    val intentType: IntentType = IntentType.UNKNOWN,
    val actionType: ActionType = ActionType.UNKNOWN,
    val entities: Map<String, String> = emptyMap()
)

