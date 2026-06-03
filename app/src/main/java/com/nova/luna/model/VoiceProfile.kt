package com.nova.luna.model

enum class VoiceProfile(
    val displayName: String,
    val pitch: Float,
    val speechRate: Float
) {
    NOVA("Nova", 0.88f, 0.96f),
    LUNA("Luna", 1.12f, 1.0f);

    companion object {
        fun fromStoredValue(value: String?): VoiceProfile {
            return values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NOVA
        }
    }
}
