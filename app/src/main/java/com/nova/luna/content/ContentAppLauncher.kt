package com.nova.luna.content

import android.content.Context
import android.content.Intent

class ContentAppLauncher(private val context: Context) {

    fun launch(tool: CreationTool): Boolean {
        if (tool.packageName == null) return false
        
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(tool.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
