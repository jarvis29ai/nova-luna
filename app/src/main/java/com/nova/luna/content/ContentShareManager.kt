package com.nova.luna.content

import android.content.Context
import android.content.Intent

class ContentShareManager(private val context: Context) {

    fun shareContent(content: String, target: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, content)
        
        if (target != null) {
            val pkg = when (target.lowercase()) {
                "whatsapp" -> "com.whatsapp"
                "gmail" -> "com.google.android.gm"
                "telegram" -> "org.telegram.messenger"
                else -> null
            }
            if (pkg != null) {
                intent.setPackage(pkg)
            }
        }

        return try {
            val chooser = Intent.createChooser(intent, "Share via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }
}
