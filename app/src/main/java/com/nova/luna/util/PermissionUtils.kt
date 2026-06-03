package com.nova.luna.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nova.luna.service.NovaAccessibilityService
import java.util.Locale

object PermissionUtils {
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationAccess(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        if (enabledServices.isBlank()) return false

        val expectedService = ComponentName(context, NovaAccessibilityService::class.java).flattenToString()
        return enabledServices.split(':').any { service ->
            service.equals(expectedService, ignoreCase = true)
        }
    }

    fun hasTouchExplorationAvailable(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        return manager?.isEnabled == true
    }

    fun biometricHardwareStatus(context: Context): String {
        val manager = androidx.biometric.BiometricManager.from(context)
        val result = manager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return when (result) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> "Available"
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware"
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Hardware unavailable"
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometric enrolled"
            else -> "Unavailable"
        }
    }

    fun buildStatusReport(context: Context): String {
        val parts = listOf(
            "Mic: ${status(hasRecordAudioPermission(context))}",
            "Notifications: ${status(hasPostNotificationsPermission(context) && hasNotificationAccess(context))}",
            "Accessibility: ${status(hasAccessibilityPermission(context))}",
            "Usage Access: ${status(hasUsageAccess(context))}",
            "Biometric: ${biometricHardwareStatus(context)}"
        )
        return parts.joinToString(separator = "\n")
    }

    private fun status(enabled: Boolean): String {
        return if (enabled) "Granted" else "Missing"
    }
}

