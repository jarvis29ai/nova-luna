package com.nova.luna.communication

class CommunicationEmailDraftModel {

    fun createDraft(instruction: String, tone: DraftTone): EmailDraft {
        val draftBody = when (tone) {
            DraftTone.FORMAL, DraftTone.PROFESSIONAL -> "Dear [Recipient],\n\nI hope this email finds you well. I am writing to you regarding [Subject].\n\nBest regards,\n[User]"
            else -> "Hi,\n\nI wanted to follow up on [Subject].\n\nThanks,\n[User]"
        }

        return EmailDraft(
            recipient = null,
            subject = "Subject Placeholder",
            body = draftBody,
            tone = tone
        )
    }

    fun editDraft(draft: EmailDraft, instruction: String): EmailDraft {
        return draft.copy(body = "${draft.body}\n\n(Edited: $instruction)")
    }
}
