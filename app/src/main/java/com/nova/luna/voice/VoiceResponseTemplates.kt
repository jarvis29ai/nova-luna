package com.nova.luna.voice

import com.nova.luna.model.ActionResultStatus

class VoiceResponseTemplates {
    fun getMessage(type: VoiceResponseType, context: Map<String, String> = emptyMap()): String {
        return when (type) {
            VoiceResponseType.LISTENING -> "I am listening."
            VoiceResponseType.THINKING -> "Checking this for you."
            VoiceResponseType.NEED_DETAIL -> context["query"] ?: "Tell me what to do."
            VoiceResponseType.CONFIRMATION -> context["message"] ?: "Should I continue?"
            VoiceResponseType.SUCCESS -> context["message"] ?: "Done."
            VoiceResponseType.FAILURE -> context["message"] ?: "I could not complete that."
            VoiceResponseType.BLOCKED -> context["message"] ?: "I blocked this for safety."
            VoiceResponseType.PERMISSION_REQUIRED -> context["message"] ?: "Please enable the required permission first."
            VoiceResponseType.CANCELLED -> "Cancelled."
            VoiceResponseType.SUMMARY -> context["message"] ?: ""
            VoiceResponseType.SAFE_STATUS -> context["message"] ?: ""
            VoiceResponseType.DEBUG_ONLY -> context["message"] ?: ""
        }
    }

    fun fromActionResultStatus(status: ActionResultStatus, message: String? = null): String {
        return when (status) {
            ActionResultStatus.SUCCESS -> message ?: "Done."
            ActionResultStatus.FAILED -> message ?: "I could not complete that."
            ActionResultStatus.BLOCKED -> message ?: "I blocked this for safety."
            ActionResultStatus.NEEDS_CONFIRMATION -> message ?: "This needs confirmation. Should I continue?"
            ActionResultStatus.NOT_FOUND -> message ?: "I could not find the button. Please show it on screen or try again."
            ActionResultStatus.TIMEOUT -> message ?: "The app did not respond. Please try again."
            ActionResultStatus.UNSUPPORTED -> message ?: "I cannot do that yet."
            ActionResultStatus.PERMISSION_REQUIRED -> message ?: "Permission is needed so I can proceed."
        }
    }
}
