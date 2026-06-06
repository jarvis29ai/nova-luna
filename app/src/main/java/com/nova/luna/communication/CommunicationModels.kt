package com.nova.luna.communication

import java.time.LocalDateTime

enum class CommunicationCommandType {
    SUMMARIZE_ALL_TODAY,
    SUMMARIZE_ONE_PLATFORM,
    SUMMARIZE_SINGLE_LONG_MESSAGE,
    FIND_MESSAGE,
    DRAFT_REPLY,
    SEND_REPLY,
    DRAFT_EMAIL,
    SEND_EMAIL,
    EDIT_DRAFT,
    SAVE_DRAFT,
    CANCEL,
    UNKNOWN
}

enum class CommunicationFlowState {
    IDLE,
    PARSING_REQUEST,
    CHECKING_COMMAND_TYPE,
    CHECKING_PERMISSIONS,
    PERMISSION_BLOCKED,
    OPENING_ALLOWED_SOURCES,
    READING_GMAIL,
    READING_SMS,
    READING_WHATSAPP,
    READING_TELEGRAM,
    COLLECTING_TODAYS_MESSAGES,
    CLASSIFYING_MESSAGES,
    RANKING_IMPORTANCE,
    CREATING_PLATFORM_SUMMARY,
    CREATING_SENDER_SUMMARY,
    SHOWING_IMPORTANT_MESSAGES_FIRST,
    IDENTIFYING_PLATFORM,
    READING_SELECTED_PLATFORM,
    IDENTIFYING_SENDER_AND_PLATFORM,
    READING_SELECTED_MESSAGE,
    SUMMARIZING_LONG_MESSAGE,
    EXTRACTING_SENDER_INTENT,
    EXTRACTING_SEARCH_QUERY,
    SEARCHING_ALLOWED_PLATFORMS,
    COLLECTING_MATCHING_MESSAGES,
    SHOWING_SEARCH_MATCHES,
    NO_MATCH_FOUND,
    UNDERSTANDING_REPLY_LANGUAGE,
    DETECTING_TONE_REQUIRED,
    CREATING_FORMAL_REPLY,
    CREATING_INFORMAL_REPLY,
    REWRITING_IN_USER_STYLE,
    CREATING_DRAFT,
    SHOWING_DRAFT_TO_USER,
    WAITING_FOR_SEND_CONFIRMATION,
    MODIFYING_DRAFT,
    SAVING_DRAFT,
    SENDING_MESSAGE_OR_EMAIL,
    COMPLETED,
    FAILED,
    CANCELLED,
    MANUAL_ACTION_REQUIRED
}

enum class CommunicationPlatform {
    GMAIL,
    SMS,
    WHATSAPP,
    TELEGRAM,
    UNKNOWN
}

enum class MessageImportance {
    CRITICAL,
    IMPORTANT,
    NORMAL,
    PROMOTIONAL,
    SPAM,
    UNKNOWN
}

enum class MessageCategory {
    WORK,
    PERSONAL,
    TRANSACTIONAL,
    DELIVERY,
    FINANCE,
    OTP_SECURITY,
    PROMOTIONAL,
    SPAM,
    UNKNOWN
}

enum class DraftTone {
    FORMAL,
    INFORMAL,
    PROFESSIONAL,
    POLITE,
    FRIENDLY,
    CASUAL,
    SHORT,
    DETAILED,
    USER_STYLE
}

enum class CommunicationStatus {
    SUCCESS,
    PARTIAL,
    BLOCKED,
    FAILED,
    CANCELLED,
    NEEDS_USER_INPUT,
    NEEDS_CONFIRMATION,
    MANUAL_ACTION_REQUIRED
}

enum class CommunicationPermissionStatus {
    GRANTED,
    DENIED,
    BLOCKED_BY_SMS_PERMISSION,
    BLOCKED_BY_NOTIFICATION_ACCESS,
    BLOCKED_BY_ACCESSIBILITY_NOT_READY,
    BLOCKED_BY_GMAIL_ACCESS,
    BLOCKED_BY_USAGE_ACCESS,
    SOURCE_UNAVAILABLE,
    MANUAL_ACTION_REQUIRED
}

data class CommunicationSender(
    val name: String,
    val identifier: String? = null,
    val isContact: Boolean = false
)

data class CommunicationMessage(
    val id: String,
    val platform: CommunicationPlatform,
    val sender: CommunicationSender,
    val timestamp: LocalDateTime,
    val content: String,
    val subject: String? = null,
    val isSensitive: Boolean = false,
    val category: MessageCategory = MessageCategory.UNKNOWN,
    val importance: MessageImportance = MessageImportance.UNKNOWN
)

data class CommunicationRequest(
    val rawText: String,
    val commandType: CommunicationCommandType = CommunicationCommandType.UNKNOWN,
    val targetPlatform: CommunicationPlatform? = null,
    val senderName: String? = null,
    val searchQuery: String? = null,
    val dateFilter: String? = null,
    val tone: DraftTone? = null,
    val language: String? = null,
    val draftInstruction: String? = null
)

data class CommunicationSession(
    val id: String,
    var state: CommunicationFlowState = CommunicationFlowState.IDLE,
    var request: CommunicationRequest,
    val messages: MutableList<CommunicationMessage> = mutableListOf(),
    var currentDraft: Any? = null, // Can be ReplyDraft or EmailDraft
    var status: CommunicationStatus = CommunicationStatus.NEEDS_USER_INPUT
)

data class CommunicationSummary(
    val platformSummaries: List<PlatformSummary>,
    val importantMessages: List<CommunicationMessage>,
    val overallSummary: String
)

data class PlatformSummary(
    val platform: CommunicationPlatform,
    val messageCount: Int,
    val senderSummaries: List<SenderSummary>,
    val summaryText: String
)

data class SenderSummary(
    val sender: CommunicationSender,
    val messageCount: Int,
    val latestSummary: String
)

data class MessageSearchQuery(
    val query: String,
    val platform: CommunicationPlatform? = null,
    val sender: String? = null,
    val dateFrom: LocalDateTime? = null,
    val dateTo: LocalDateTime? = null
)

data class MessageSearchResult(
    val matches: List<MessageMatch>,
    val totalCount: Int
)

data class MessageMatch(
    val message: CommunicationMessage,
    val snippet: String,
    val score: Float
)

data class ReplyDraft(
    val originalMessage: CommunicationMessage,
    val content: String,
    val tone: DraftTone,
    val language: String
)

data class EmailDraft(
    val recipient: String?,
    val subject: String?,
    val body: String,
    val tone: DraftTone
)

data class CommunicationFinalSummary(
    val popupText: String,
    val voiceText: String,
    val status: CommunicationStatus,
    val metadata: Map<String, Any> = emptyMap()
)

data class CommunicationSafetyWarning(
    val message: String,
    val riskLevel: String // Mapping to BrainRiskLevel if needed
)
