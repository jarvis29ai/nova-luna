package com.nova.luna.communication

import android.content.Context
import android.content.pm.PackageManager

class CommunicationSourceRegistry(private val context: Context) {

    fun getAvailableSources(): List<SourceInfo> {
        return listOf(
            SourceInfo(CommunicationPlatform.GMAIL, "Gmail", "com.google.android.gm"),
            SourceInfo(CommunicationPlatform.SMS, "SMS", null),
            SourceInfo(CommunicationPlatform.WHATSAPP, "WhatsApp", "com.whatsapp"),
            SourceInfo(CommunicationPlatform.TELEGRAM, "Telegram", "org.telegram.messenger")
        ).map { info ->
            info.copy(isInstalled = info.packageName?.let { isAppInstalled(it) } ?: true)
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    data class SourceInfo(
        val platform: CommunicationPlatform,
        val displayName: String,
        val packageName: String?,
        val isInstalled: Boolean = false,
        var isPermissionGranted: Boolean = false,
        var isReadSupported: Boolean = true,
        var isSearchSupported: Boolean = true,
        var isDraftSendSupported: Boolean = true,
        var unavailableReason: String? = null
    )
}
