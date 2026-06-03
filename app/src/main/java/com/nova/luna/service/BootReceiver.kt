package com.nova.luna.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.nova.luna.data.PreferencesManager
import com.nova.luna.util.PermissionUtils
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        runBlocking {
            val prefs = PreferencesManager(context)
            val enabled = prefs.isAutoStartOnBootEnabled()
            if (!enabled) {
                Log.i(TAG, "Auto-start on boot is disabled.")
                return@runBlocking
            }

            if (!PermissionUtils.hasRecordAudioPermission(context)) {
                Log.w(TAG, "Boot auto-start skipped because RECORD_AUDIO is not granted.")
                return@runBlocking
            }

            val serviceIntent = Intent(context, VoiceCommandService::class.java).apply {
                action = VoiceCommandService.ACTION_START_LISTENING
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    companion object {
        private const val TAG = "NovaLunaBootReceiver"
    }
}

