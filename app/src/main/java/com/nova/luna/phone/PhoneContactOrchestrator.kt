package com.nova.luna.phone

import android.content.Context
import java.util.UUID

class PhoneContactOrchestrator(
    private val context: Context,
    private val parser: PhoneContactIntentParser = PhoneContactIntentParser(),
    private val permissionChecker: PhoneContactPermissionChecker = PhoneContactPermissionChecker(context),
    private val contactRepo: PhoneContactRepository = PhoneContactRepository(context),
    private val callLogRepo: PhoneCallLogRepository = PhoneCallLogRepository(context),
    private val messageRepo: PhoneMessageSearchRepository = PhoneMessageSearchRepository(context),
    private val executor: PhoneCallExecutor = PhoneCallExecutor(context, permissionChecker),
    private val writer: PhoneContactWriter = PhoneContactWriter(context),
    private val voiceResponses: PhoneContactVoiceResponses = PhoneContactVoiceResponses()
) {
    private var activeSession: PhoneContactSession? = null

    fun start(request: PhoneContactRequest): PhoneContactFinalSummary {
        val session = PhoneContactSession(id = UUID.randomUUID().toString(), request = request)
        activeSession = session
        return process(session)
    }

    fun handleUserInput(text: String): PhoneContactFinalSummary {
        val session = activeSession ?: return voiceResponses.getResponse(PhoneContactFlowState.IDLE)
        
        return when (session.state) {
            PhoneContactFlowState.ASKING_CONTACT_SELECTION -> {
                val index = parser.parseSelection(text)
                if (index != null && index < session.searchResults.size) {
                    session.selectedMatch = session.searchResults[index]
                    session.state = PhoneContactFlowState.CONTACT_FOUND_EXACT
                    process(session)
                } else {
                    voiceResponses.getResponse(session.state, mapOf("matches" to session.searchResults))
                }
            }
            PhoneContactFlowState.ASKING_NUMBER_SELECTION -> {
                val index = parser.parseSelection(text)
                val numbers = session.selectedMatch?.numbers ?: session.numberCandidates
                if (index != null && index < numbers.size) {
                    session.selectedNumber = numbers[index]
                    session.state = PhoneContactFlowState.STARTING_PHONE_CALL
                    process(session)
                } else {
                    voiceResponses.getResponse(session.state, mapOf("contact" to session.selectedMatch))
                }
            }
            PhoneContactFlowState.CONFIRMING_UNKNOWN_NUMBER_CALL,
            PhoneContactFlowState.ASKING_SAVE_CONTACT_CONFIRMATION,
            PhoneContactFlowState.ASKING_NUMBER_FROM_MESSAGE_CONFIRMATION -> {
                val confirmed = parser.isConfirmation(text)
                when (confirmed) {
                    true -> {
                        if (session.state == PhoneContactFlowState.CONFIRMING_UNKNOWN_NUMBER_CALL || 
                            session.state == PhoneContactFlowState.ASKING_NUMBER_FROM_MESSAGE_CONFIRMATION) {
                            session.state = PhoneContactFlowState.STARTING_PHONE_CALL
                        } else if (session.state == PhoneContactFlowState.ASKING_SAVE_CONTACT_CONFIRMATION) {
                            session.state = PhoneContactFlowState.CREATING_CONTACT
                        }
                        process(session)
                    }
                    false -> {
                        session.state = PhoneContactFlowState.CANCELLED
                        process(session)
                    }
                    null -> voiceResponses.getResponse(session.state, mapOf("number" to session.selectedNumber?.number, "name" to session.request?.contactName))
                }
            }
            PhoneContactFlowState.ASKING_MISSING_CONTACT_NUMBER -> {
                val number = parser.parse(text).phoneNumber
                if (number != null) {
                    session.request = session.request?.copy(phoneNumber = number)
                    session.state = PhoneContactFlowState.CHECKING_DUPLICATE_CONTACT
                    process(session)
                } else {
                    voiceResponses.getResponse(session.state)
                }
            }
            PhoneContactFlowState.ASKING_UPDATE_OR_CREATE -> {
                when {
                    text.contains("update") -> {
                        session.state = PhoneContactFlowState.UPDATING_CONTACT
                        process(session)
                    }
                    text.contains("create") || text.contains("new") -> {
                        session.state = PhoneContactFlowState.CREATING_CONTACT
                        process(session)
                    }
                    parser.isConfirmation(text) == false -> {
                        session.state = PhoneContactFlowState.CANCELLED
                        process(session)
                    }
                    else -> voiceResponses.getResponse(session.state)
                }
            }
            else -> voiceResponses.getResponse(PhoneContactFlowState.IDLE)
        }
    }

    private fun process(session: PhoneContactSession): PhoneContactFinalSummary {
        return when (session.state) {
            PhoneContactFlowState.IDLE -> {
                session.state = PhoneContactFlowState.CHECKING_COMMAND_TYPE
                process(session)
            }
            PhoneContactFlowState.CHECKING_COMMAND_TYPE -> {
                when (session.request?.commandType) {
                    PhoneContactCommandType.CALL_SAVED_CONTACT -> {
                        session.state = PhoneContactFlowState.CHECKING_PERMISSIONS
                        process(session)
                    }
                    PhoneContactCommandType.CALL_UNKNOWN_PERSON -> {
                        if (session.request?.phoneNumber != null) {
                            session.selectedNumber = PhoneNumberCandidate(session.request?.phoneNumber!!, "Direct dial", PhoneContactSearchSource.MANUAL_USER_INPUT)
                            session.state = PhoneContactFlowState.CONFIRMING_UNKNOWN_NUMBER_CALL
                            voiceResponses.getResponse(session.state, mapOf("number" to session.selectedNumber?.number))
                        } else {
                            session.state = PhoneContactFlowState.SEARCHING_CALL_LOGS
                            process(session)
                        }
                    }
                    PhoneContactCommandType.CALL_NUMBER_FROM_MESSAGE -> {
                        session.state = PhoneContactFlowState.IDENTIFYING_MESSAGE_SENDER
                        process(session)
                    }
                    PhoneContactCommandType.CREATE_NEW_CONTACT, PhoneContactCommandType.UPDATE_CONTACT -> {
                        session.state = PhoneContactFlowState.EXTRACTING_NAME_AND_NUMBER
                        process(session)
                    }
                    PhoneContactCommandType.CANCEL -> {
                        session.state = PhoneContactFlowState.CANCELLED
                        process(session)
                    }
                    else -> voiceResponses.getResponse(PhoneContactFlowState.FAILED)
                }
            }
            PhoneContactFlowState.CHECKING_PERMISSIONS -> {
                if (permissionChecker.hasReadContactsPermission()) {
                    session.state = PhoneContactFlowState.SEARCHING_PHONE_CONTACTS
                    process(session)
                } else {
                    session.state = PhoneContactFlowState.PERMISSION_BLOCKED
                    voiceResponses.getResponse(session.state)
                }
            }
            PhoneContactFlowState.SEARCHING_PHONE_CONTACTS -> {
                val name = session.request?.contactName ?: ""
                val matches = contactRepo.searchContacts(name)
                session.searchResults = matches
                when {
                    matches.isEmpty() -> {
                        session.state = PhoneContactFlowState.CONTACT_NOT_FOUND
                        process(session)
                    }
                    matches.size == 1 -> {
                        session.selectedMatch = matches[0]
                        session.state = PhoneContactFlowState.CONTACT_FOUND_EXACT
                        process(session)
                    }
                    else -> {
                        session.state = PhoneContactFlowState.ASKING_CONTACT_SELECTION
                        voiceResponses.getResponse(session.state, mapOf("matches" to matches))
                    }
                }
            }
            PhoneContactFlowState.CONTACT_FOUND_EXACT -> {
                val numbers = session.selectedMatch?.numbers ?: emptyList()
                when {
                    numbers.isEmpty() -> {
                        session.state = PhoneContactFlowState.CONTACT_NOT_FOUND
                        process(session)
                    }
                    numbers.size == 1 -> {
                        session.selectedNumber = numbers[0]
                        session.state = PhoneContactFlowState.STARTING_PHONE_CALL
                        process(session)
                    }
                    else -> {
                        // Check if label matches
                        val label = session.request?.label
                        val matchedNumber = numbers.find { it.label == label }
                        if (matchedNumber != null) {
                            session.selectedNumber = matchedNumber
                            session.state = PhoneContactFlowState.STARTING_PHONE_CALL
                            process(session)
                        } else {
                            session.state = PhoneContactFlowState.ASKING_NUMBER_SELECTION
                            voiceResponses.getResponse(session.state, mapOf("contact" to session.selectedMatch))
                        }
                    }
                }
            }
            PhoneContactFlowState.CONTACT_NOT_FOUND -> {
                session.state = PhoneContactFlowState.SEARCHING_CALL_LOGS
                process(session)
            }
            PhoneContactFlowState.SEARCHING_CALL_LOGS -> {
                if (permissionChecker.hasReadCallLogPermission()) {
                    val candidates = callLogRepo.searchRecentCalls(session.request?.contactName ?: session.request?.phoneNumber)
                    if (candidates.isNotEmpty()) {
                        session.numberCandidates = candidates
                        session.state = PhoneContactFlowState.NUMBER_FOUND_ONE
                        process(session)
                    } else {
                        session.state = PhoneContactFlowState.SEARCHING_SMS
                        process(session)
                    }
                } else {
                    session.state = PhoneContactFlowState.SEARCHING_SMS
                    process(session)
                }
            }
            PhoneContactFlowState.SEARCHING_SMS -> {
                if (permissionChecker.hasReadSmsPermission()) {
                    val candidates = messageRepo.searchSms(session.request?.contactName ?: session.request?.senderName)
                    if (candidates.isNotEmpty()) {
                        session.selectedNumber = PhoneNumberCandidate(candidates[0].number, "from SMS", PhoneContactSearchSource.SMS)
                        session.state = PhoneContactFlowState.NUMBER_FOUND_ONE
                        process(session)
                    } else {
                        session.state = PhoneContactFlowState.NUMBER_NOT_FOUND
                        process(session)
                    }
                } else {
                    session.state = PhoneContactFlowState.NUMBER_NOT_FOUND
                    process(session)
                }
            }
            PhoneContactFlowState.NUMBER_FOUND_ONE -> {
                session.selectedNumber = session.selectedNumber ?: session.numberCandidates.firstOrNull()
                session.state = PhoneContactFlowState.CONFIRMING_UNKNOWN_NUMBER_CALL
                voiceResponses.getResponse(session.state, mapOf("number" to session.selectedNumber?.number))
            }
            PhoneContactFlowState.STARTING_PHONE_CALL -> {
                val number = session.selectedNumber?.number ?: ""
                val result = executor.executeCall(PhoneCallTarget(session.selectedMatch?.displayName, number))
                session.state = PhoneContactFlowState.COMPLETED
                val message = if (result == PhoneCallResult.CALL_STARTED) "Calling ${session.selectedMatch?.displayName ?: number}." else "Opening dialer for $number."
                voiceResponses.getResponse(session.state, mapOf("message" to message))
            }
            PhoneContactFlowState.IDENTIFYING_MESSAGE_SENDER -> {
                session.state = PhoneContactFlowState.EXTRACTING_NUMBER_FROM_MESSAGE
                process(session)
            }
            PhoneContactFlowState.EXTRACTING_NUMBER_FROM_MESSAGE -> {
                if (permissionChecker.hasReadSmsPermission()) {
                    val sender = session.request?.senderName
                    val candidates = messageRepo.searchSms(sender)
                    if (candidates.isNotEmpty()) {
                        session.selectedNumber = PhoneNumberCandidate(candidates[0].number, "from ${candidates[0].source}", PhoneContactSearchSource.SMS)
                        session.state = PhoneContactFlowState.ASKING_NUMBER_FROM_MESSAGE_CONFIRMATION
                        voiceResponses.getResponse(session.state, mapOf("number" to session.selectedNumber?.number))
                    } else {
                        session.state = PhoneContactFlowState.NUMBER_NOT_FOUND
                        voiceResponses.getResponse(session.state)
                    }
                } else {
                    session.state = PhoneContactFlowState.PERMISSION_BLOCKED
                    voiceResponses.getResponse(session.state)
                }
            }
            PhoneContactFlowState.EXTRACTING_NAME_AND_NUMBER -> {
                if (session.request?.phoneNumber == null) {
                    session.state = PhoneContactFlowState.ASKING_MISSING_CONTACT_NUMBER
                    voiceResponses.getResponse(session.state)
                } else {
                    session.state = PhoneContactFlowState.CHECKING_DUPLICATE_CONTACT
                    process(session)
                }
            }
            PhoneContactFlowState.CHECKING_DUPLICATE_CONTACT -> {
                val matches = contactRepo.searchContacts(session.request?.contactName ?: "")
                if (matches.isNotEmpty()) {
                    session.searchResults = matches
                    session.state = PhoneContactFlowState.ASKING_UPDATE_OR_CREATE
                    voiceResponses.getResponse(session.state)
                } else {
                    session.state = PhoneContactFlowState.ASKING_SAVE_CONTACT_CONFIRMATION
                    voiceResponses.getResponse(session.state, mapOf("name" to session.request?.contactName))
                }
            }
            PhoneContactFlowState.CREATING_CONTACT -> {
                if (permissionChecker.hasWriteContactsPermission()) {
                    val res = writer.createContact(session.request?.contactName ?: "Unknown", session.request?.phoneNumber ?: "", session.request?.label)
                    session.state = PhoneContactFlowState.COMPLETED
                    voiceResponses.getResponse(session.state, mapOf("message" to "Contact saved successfully."))
                } else {
                    session.state = PhoneContactFlowState.PERMISSION_BLOCKED
                    voiceResponses.getResponse(session.state)
                }
            }
            PhoneContactFlowState.UPDATING_CONTACT -> {
                if (permissionChecker.hasWriteContactsPermission()) {
                    val contactId = session.searchResults.firstOrNull()?.id ?: ""
                    val res = writer.updateContact(contactId, session.request?.phoneNumber ?: "", session.request?.label)
                    session.state = PhoneContactFlowState.COMPLETED
                    voiceResponses.getResponse(session.state, mapOf("message" to "Contact updated successfully."))
                } else {
                    session.state = PhoneContactFlowState.PERMISSION_BLOCKED
                    voiceResponses.getResponse(session.state)
                }
            }
            PhoneContactFlowState.CANCELLED -> {
                activeSession = null
                voiceResponses.getResponse(PhoneContactFlowState.CANCELLED)
            }
            PhoneContactFlowState.PERMISSION_BLOCKED -> {
                voiceResponses.getResponse(PhoneContactFlowState.PERMISSION_BLOCKED)
            }
            PhoneContactFlowState.NUMBER_NOT_FOUND -> {
                voiceResponses.getResponse(PhoneContactFlowState.NUMBER_NOT_FOUND)
            }
            else -> voiceResponses.getResponse(PhoneContactFlowState.FAILED)
        }
    }

    fun isActive(): Boolean {
        return activeSession != null && activeSession?.state != PhoneContactFlowState.COMPLETED && 
               activeSession?.state != PhoneContactFlowState.FAILED && activeSession?.state != PhoneContactFlowState.CANCELLED
    }
}
