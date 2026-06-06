package com.nova.luna.content

class ContentCreationVoiceResponses {

    fun getMissingDetailQuestion(missing: String): String {
        return when (missing) {
            "topic" -> "What topic should I create this on?"
            "purpose" -> "Who is the audience: students, investors, customers, or your team?"
            "style" -> "What style should I use: professional, modern, simple, or creative?"
            "slide count" -> "How many slides should the presentation have?"
            "duration" -> "How long should the video be?"
            "page count" -> "How many pages should the document have?"
            else -> "Can you tell me more about the $missing?"
        }
    }

    fun getInitialProcessingResponse(outputType: ContentOutputType, profile: ContentRequirementProfile): String {
        return "I will make a ${profile.style.name.lowercase()} $outputType on ${profile.topic}."
    }

    fun getDraftReadyResponse(outputType: ContentOutputType): String {
        return "The first draft for your $outputType is ready. Please review it."
    }

    fun getAppLaunchResponse(toolName: String): String {
        return "I'm opening $toolName to help with the creation."
    }

    fun getFinalResponse(status: ContentCreationStatus, toolName: String?): String {
        return when (status) {
            ContentCreationStatus.SUCCESS -> "I've finalized the content ${if (toolName != null) "using $toolName" else ""}. It's saved on your phone."
            ContentCreationStatus.MANUAL_ACTION_REQUIRED -> "I've prepared the prompt and opened the app. You can now finish it there."
            ContentCreationStatus.CANCELLED -> "Okay, I've cancelled the content creation."
            else -> "I encountered an issue while creating the content."
        }
    }
}
