package com.nova.luna.phone

import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult

data class PhoneContactRequest(
    val commandType: PhoneContactCommandType,
    val contactName: String? = null,
    val phoneNumber: String? = null,
    val senderName: String? = null,
    val sourceApp: String? = null,
    val label: String? = null,
    val isDirectCall: Boolean = false,
    val rawText: String
)

enum class PhoneContactCommandType {
    CALL_SAVED_CONTACT,
    CALL_UNKNOWN_PERSON,
    CALL_NUMBER_FROM_MESSAGE,
    CREATE_NEW_CONTACT,
    UPDATE_CONTACT,
    CANCEL,
    UNKNOWN
}

enum class PhoneContactFlowState {
    IDLE,
    PARSING_REQUEST,
    CHECKING_COMMAND_TYPE,
    CHECKING_PERMISSIONS,
    PERMISSION_BLOCKED,
    SEARCHING_PHONE_CONTACTS,
    CONTACT_FOUND_EXACT,
    CONTACT_FOUND_MULTIPLE,
    CONTACT_NOT_FOUND,
    ASKING_CONTACT_SELECTION,
    CONFIRMING_SELECTED_CONTACT,
    SEARCHING_CALL_LOGS,
    SEARCHING_SMS,
    SEARCHING_WHATSAPP,
    SEARCHING_TELEGRAM,
    SEARCHING_TRUECALLER_IF_ALLOWED,
    NUMBER_FOUND_ONE,
    NUMBER_FOUND_MULTIPLE,
    NUMBER_NOT_FOUND,
    ASKING_NUMBER_SELECTION,
    CONFIRMING_UNKNOWN_NUMBER_CALL,
    IDENTIFYING_MESSAGE_SENDER,
    EXTRACTING_NUMBER_FROM_MESSAGE,
    ASKING_NUMBER_FROM_MESSAGE_CONFIRMATION,
    EXTRACTING_NAME_AND_NUMBER,
    ASKING_MISSING_CONTACT_NUMBER,
    CHECKING_DUPLICATE_CONTACT,
    ASKING_SAVE_CONTACT_CONFIRMATION,
    ASKING_UPDATE_OR_CREATE,
    CREATING_CONTACT,
    UPDATING_CONTACT,
    STARTING_PHONE_CALL,
    COMPLETED,
    FAILED,
    CANCELLED,
    MANUAL_ACTION_REQUIRED
}

enum class PhoneContactPermissionStatus {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    NOT_REQUESTED
}

enum class PhoneContactSearchSource {
    PHONE_CONTACTS,
    RECENT_CALL_LOGS,
    SMS,
    WHATSAPP,
    TELEGRAM,
    TRUECALLER,
    MANUAL_USER_INPUT
}

data class PhoneContactSearchResult(
    val source: PhoneContactSearchSource,
    val matches: List<PhoneContactMatch> = emptyList()
)

data class PhoneContactMatch(
    val id: String,
    val displayName: String,
    val numbers: List<PhoneNumberCandidate> = emptyList()
)

data class PhoneNumberCandidate(
    val number: String,
    val label: String? = null, // e.g., "mobile", "work"
    val source: PhoneContactSearchSource
)

data class MessageNumberCandidate(
    val number: String,
    val sender: String,
    val source: String, // SMS, WhatsApp, Telegram
    val timestamp: Long
)

data class ContactCreateRequest(
    val name: String,
    val number: String,
    val label: String? = null
)

data class ContactUpdateRequest(
    val contactId: String,
    val newNumber: String,
    val label: String? = null
)

data class PhoneCallTarget(
    val name: String?,
    val number: String,
    val isEmergency: Boolean = false
)

enum class PhoneCallResult {
    CALL_STARTED,
    DIALER_OPENED,
    BLOCKED_BY_PERMISSION,
    BLOCKED_BY_SAFETY,
    CANCELLED,
    FAILED
}

enum class ContactWriteResult {
    CONTACT_CREATED,
    CONTACT_UPDATED,
    DUPLICATE_FOUND,
    BLOCKED_BY_CONTACT_PERMISSION,
    CANCELLED,
    FAILED
}

data class PhoneContactFinalSummary(
    val status: PhoneContactStatus,
    val message: String,
    val voiceMessage: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class PhoneContactStatus {
    SUCCESS,
    PARTIAL,
    BLOCKED,
    FAILED,
    CANCELLED,
    NEEDS_USER_INPUT,
    NEEDS_CONFIRMATION,
    MANUAL_ACTION_REQUIRED
}

data class PhoneContactSafetyWarning(
    val type: String,
    val message: String
)

data class PhoneContactSession(
    val id: String,
    var state: PhoneContactFlowState = PhoneContactFlowState.IDLE,
    var request: PhoneContactRequest? = null,
    var searchResults: List<PhoneContactMatch> = emptyList(),
    var selectedMatch: PhoneContactMatch? = null,
    var selectedNumber: PhoneNumberCandidate? = null,
    var numberCandidates: List<PhoneNumberCandidate> = emptyList(),
    var messageCandidates: List<MessageNumberCandidate> = emptyList(),
    var lastSafetyWarning: PhoneContactSafetyWarning? = null
)

fun PhoneContactFinalSummary.toCommandResult(commandIntent: CommandIntent): CommandResult {
    return CommandResult(
        success = status == PhoneContactStatus.SUCCESS || status == PhoneContactStatus.PARTIAL,
        message = message,
        intentType = commandIntent.intentType,
        actionType = commandIntent.actionType,
        entities = commandIntent.entities + (voiceMessage?.let { mapOf("voice_response" to it) } ?: emptyMap())
    )
}
