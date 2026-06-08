package com.nova.luna.demo

import com.nova.luna.brain.UnifiedDomain
import com.nova.luna.ui.AssistantPopupState

enum class DemoFlowId {
    OPEN_APP,
    PLAY_MUSIC,
    YOUTUBE_SEARCH,
    SCROLL_SELECT_MEDIA,
    READ_SUMMARIZE_MESSAGE,
    CONTENT_PROMPT,
    FOOD_ORDER,
    GROCERY_COMPARE,
    CAB_BOOKING,
    SHOPPING_COMPARE
}

enum class DemoFlowStatus {
    NOT_TESTED,
    PASS,
    PARTIAL_PASS,
    FAIL,
    BLOCKED_BY_PERMISSION,
    BLOCKED_BY_MISSING_APP,
    BLOCKED_BY_EXTERNAL_APP_UI,
    NEEDS_MANUAL_REVIEW
}

enum class AssistantPopupEvent {
    MIC_TAPPED,
    CANCEL_TAPPED,
    CONTINUE_TAPPED,
    CLOSE_TAPPED,
    TEXT_COMMAND_SUBMITTED,
    CONFIRMATION_VOICE_ACCEPTED,
    CONFIRMATION_VOICE_REJECTED
}

enum class DemoFlowEvent {
    COMMAND_RECEIVED,
    TRANSCRIPT_CLEANED,
    DOMAIN_SELECTED,
    ACTION_CANDIDATE_CREATED,
    SAFETY_DECISION,
    CONFIRMATION_REQUIRED,
    ACTION_EXECUTION_STARTED,
    ACTION_RESULT,
    POPUP_STATE_CHANGED,
    VOICE_RESPONSE_REQUESTED,
    FLOW_COMPLETED
}

data class DemoFlowResult(
    val flowId: DemoFlowId,
    val commandUsed: String,
    val expectedDomain: UnifiedDomain,
    val actualDomain: UnifiedDomain,
    val expectedOutcome: String,
    val actualOutcome: String,
    val popupStatesSeen: List<AssistantPopupState> = emptyList(),
    val voiceResponsesHeard: List<String> = emptyList(),
    val safetyConfirmationShown: Boolean = false,
    val passStatus: DemoFlowStatus = DemoFlowStatus.NOT_TESTED,
    val failureReason: String? = null,
    val retryCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
