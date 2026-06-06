package com.nova.luna.communication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

class CommunicationPermissionChecker(private val context: Context) {

    fun checkPermission(platform: CommunicationPlatform): CommunicationPermissionStatus {
        return when (platform) {
            CommunicationPlatform.SMS -> {
                if (hasPermission(Manifest.permission.READ_SMS)) {
                    CommunicationPermissionStatus.GRANTED
                } else {
                    CommunicationPermissionStatus.BLOCKED_BY_SMS_PERMISSION
                }
            }
            CommunicationPlatform.WHATSAPP, CommunicationPlatform.TELEGRAM -> {
                if (isAccessibilityServiceEnabled() || isNotificationListenerEnabled()) {
                    CommunicationPermissionStatus.GRANTED
                } else {
                    CommunicationPermissionStatus.BLOCKED_BY_ACCESSIBILITY_NOT_READY
                }
            }
            CommunicationPlatform.GMAIL -> {
                val authenticator = CommunicationGmailAuthenticator(context)
                if (authenticator.getGoogleAccount() != null) {
                    CommunicationPermissionStatus.GRANTED
                } else {
                    CommunicationPermissionStatus.BLOCKED_BY_GMAIL_ACCESS
                }
            }
            CommunicationPlatform.UNKNOWN -> CommunicationPermissionStatus.SOURCE_UNAVAILABLE
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "${context.packageName}/com.nova.luna.service.NovaAccessibilityService"
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedComponentName) == true
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }
}
