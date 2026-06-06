package com.nova.luna.communication

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nova.luna.service.NovaAccessibilityService

class CommunicationSendExecutor(private val context: Context) {

    fun sendReply(draft: ReplyDraft): CommunicationStatus {
        if (NovaAccessibilityService.instance == null) {
            return CommunicationStatus.BLOCKED // Need accessibility for safe composer handoff
        }
        val success = openComposer(draft.originalMessage.platform, draft.content)
        return if (success) CommunicationStatus.SUCCESS else CommunicationStatus.FAILED
    }

    fun sendEmail(draft: EmailDraft): CommunicationStatus {
        if (NovaAccessibilityService.instance == null) {
            return CommunicationStatus.BLOCKED
        }
        
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
            putExtra(Intent.EXTRA_TEXT, draft.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                CommunicationStatus.SUCCESS
            } else {
                CommunicationStatus.FAILED
            }
        } catch (e: Exception) {
            CommunicationStatus.FAILED
        }
    }

    private fun openComposer(platform: CommunicationPlatform, draftContent: String): Boolean {
        val intent = when (platform) {
            CommunicationPlatform.SMS -> Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:")
                putExtra("sms_body", draftContent)
            }
            CommunicationPlatform.WHATSAPP -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, draftContent)
            }
            CommunicationPlatform.TELEGRAM -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("org.telegram.messenger")
                putExtra(Intent.EXTRA_TEXT, draftContent)
            }
            CommunicationPlatform.GMAIL -> Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_TEXT, draftContent)
            }
            else -> return false
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
