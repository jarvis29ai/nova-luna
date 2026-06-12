package com.nova.luna.phone

import android.content.Context
import android.content.pm.PackageManager

class AppResolver(private val context: Context) {

    private val commonApps = mapOf(
        "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera", "com.sec.android.app.camera"),
        "settings" to listOf("com.android.settings"),
        "youtube" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx"),
        "phone" to listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
        "dialer" to listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
        "messages" to listOf("com.google.android.apps.messaging", "com.android.messaging", "com.samsung.android.messaging"),
        "whatsapp" to listOf("com.whatsapp"),
        "maps" to listOf("com.google.android.apps.maps")
    )

    fun resolvePackage(appName: String): String? {
        val pm = context.packageManager
        val nameLower = appName.lowercase()
        
        // 1. Check if it's already a package name installed on the device
        if (isPackageInstalled(appName, pm)) {
            return appName
        }

        // 2. Check common map
        commonApps[nameLower]?.forEach { pkg ->
            if (isPackageInstalled(pkg, pm)) {
                return pkg
            }
        }

        // 3. Search by label
        return findPackageByLabel(appName, pm)
    }

    private fun isPackageInstalled(packageName: String, pm: PackageManager): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun findPackageByLabel(label: String, pm: PackageManager): String? {
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
        
        for (info in resolvedInfos) {
            val appLabel = info.loadLabel(pm).toString().lowercase()
            if (appLabel == label.lowercase()) {
                return info.activityInfo.packageName
            }
        }
        
        // Try partial match if exact match fails
        for (info in resolvedInfos) {
            val appLabel = info.loadLabel(pm).toString().lowercase()
            if (appLabel.contains(label.lowercase())) {
                return info.activityInfo.packageName
            }
        }
        
        return null
    }
}
