package com.nova.luna.content

class ContentReviewManager {

    fun processFeedback(feedback: String, currentBrief: ContentBrief): ContentBrief {
        // In a real scenario, this would use AI to refine the brief.
        // For now, we append the feedback to the design direction or objective.
        return currentBrief.copy(
            designDirection = currentBrief.designDirection + " (User feedback: $feedback)"
        )
    }

    fun isApproval(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("approve") || normalized.contains("finalize") || normalized.contains("looks good") || normalized == "yes"
    }

    fun isCancel(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("cancel") || normalized.contains("stop") || normalized == "no"
    }
}
