package com.nova.luna.phone

class PhoneContactVoiceResponses {

    fun getResponse(state: PhoneContactFlowState, data: Map<String, Any?> = emptyMap()): PhoneContactFinalSummary {
        return when (state) {
            PhoneContactFlowState.PERMISSION_BLOCKED -> PhoneContactFinalSummary(
                PhoneContactStatus.BLOCKED,
                "I need permission to access your contacts or phone.",
                "I need permission to access your contacts or phone. Please enable it in settings."
            )
            PhoneContactFlowState.ASKING_CONTACT_SELECTION -> {
                val matches = data["matches"] as? List<PhoneContactMatch> ?: emptyList()
                val names = matches.joinToString(" and ") { it.displayName }
                PhoneContactFinalSummary(
                    PhoneContactStatus.NEEDS_USER_INPUT,
                    "I found multiple matches: $names. Which one should I call?",
                    "I found multiple matches: $names. Which one should I call?"
                )
            }
            PhoneContactFlowState.ASKING_NUMBER_SELECTION -> {
                val contact = data["contact"] as? PhoneContactMatch
                val name = contact?.displayName ?: "the contact"
                PhoneContactFinalSummary(
                    PhoneContactStatus.NEEDS_USER_INPUT,
                    "$name has multiple numbers. Which one should I use?",
                    "$name has multiple numbers. Which one should I use?"
                )
            }
            PhoneContactFlowState.CONFIRMING_UNKNOWN_NUMBER_CALL, 
            PhoneContactFlowState.ASKING_NUMBER_FROM_MESSAGE_CONFIRMATION -> {
                val number = data["number"] as? String ?: ""
                PhoneContactFinalSummary(
                    PhoneContactStatus.NEEDS_CONFIRMATION,
                    "Should I call $number?",
                    "Should I call $number?"
                )
            }
            PhoneContactFlowState.NUMBER_NOT_FOUND -> PhoneContactFinalSummary(
                PhoneContactStatus.FAILED,
                "I could not find the number.",
                "I could not find the number."
            )
            PhoneContactFlowState.ASKING_MISSING_CONTACT_NUMBER -> PhoneContactFinalSummary(
                PhoneContactStatus.NEEDS_USER_INPUT,
                "What number should I save for this contact?",
                "What number should I save for this contact?"
            )
            PhoneContactFlowState.ASKING_SAVE_CONTACT_CONFIRMATION -> {
                val name = data["name"] as? String ?: ""
                PhoneContactFinalSummary(
                    PhoneContactStatus.NEEDS_CONFIRMATION,
                    "Should I save $name as a new contact?",
                    "Should I save $name as a new contact?"
                )
            }
            PhoneContactFlowState.ASKING_UPDATE_OR_CREATE -> PhoneContactFinalSummary(
                PhoneContactStatus.NEEDS_CONFIRMATION,
                "A contact with this name already exists. Should I update it or create a new one?",
                "A contact with this name already exists. Should I update it or create a new one?"
            )
            PhoneContactFlowState.COMPLETED -> {
                val message = data["message"] as? String ?: "Done."
                PhoneContactFinalSummary(PhoneContactStatus.SUCCESS, message, message)
            }
            PhoneContactFlowState.CANCELLED -> PhoneContactFinalSummary(
                PhoneContactStatus.CANCELLED,
                "Cancelled.",
                "Cancelled."
            )
            PhoneContactFlowState.FAILED -> PhoneContactFinalSummary(
                PhoneContactStatus.FAILED,
                "Something went wrong.",
                "Something went wrong."
            )
            else -> PhoneContactFinalSummary(
                PhoneContactStatus.FAILED,
                "I'm not sure how to handle that.",
                "I'm not sure how to handle that."
            )
        }
    }
}
