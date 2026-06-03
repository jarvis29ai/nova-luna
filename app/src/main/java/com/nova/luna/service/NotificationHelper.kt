package com.nova.luna.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nova.luna.MainActivity
import com.nova.luna.R

object NotificationHelper {
    const val CHANNEL_ID = "nova_luna_voice_channel"
    private const val CHANNEL_NAME = "Nova Luna Voice Service"
    private const val CHANNEL_DESCRIPTION = "Persistent voice assistant notification"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun buildServiceNotification(
        context: Context,
        statusText: String,
        listening: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            pendingFlags()
        )

        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, VoiceCommandService::class.java).apply {
                action = VoiceCommandService.ACTION_STOP_LISTENING
            },
            pendingFlags()
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.app_name_full))
            .setContentText(statusText)
            .setContentIntent(contentIntent)
            .setOngoing(listening)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.stop_listening),
                stopIntent
            )
            .build()
    }

    fun notifyStatus(
        context: Context,
        statusText: String,
        listening: Boolean
    ) {
        NotificationManagerCompat.from(context).notify(
            VoiceCommandService.NOTIFICATION_ID,
            buildServiceNotification(context, statusText, listening)
        )
    }

    private fun pendingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }
}

