package com.nova.luna.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class VoicePermissionManager(private val context: Context) {
    
    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // This usually needs to be called from an Activity to use ActivityCompat.shouldShowRequestPermissionRationale
    // but we can provide a helper that just checks the status.
}
