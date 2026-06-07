package com.nova.luna.memory

data class LocalUserPreferences(
    val preferredLanguage: String = "en",
    val preferredVoiceResponseStyle: String = "balanced",
    val preferredCabApp: String? = null,
    val preferredFoodApp: String? = null,
    val preferredGroceryApp: String? = null,
    val preferredShoppingApp: String? = null,
    val preferredMusicApp: String? = null,
    val preferredMediaApp: String? = null,
    val preferredContentTool: String? = null,
    val onlineHelperAllowed: Boolean = false,
    val askBeforeUsingOnlineAi: Boolean = true,
    val privateMode: Boolean = true,
    val confirmationStyle: String = "standard",
    val accessibilityGuidancePreference: String? = null,
    val preferredApps: Map<String, String> = emptyMap()
)
