package com.nova.luna.util

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager

class RuntimeHealthMonitor(private val context: Context) {

    fun isMemoryOk(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return !memoryInfo.lowMemory && (memoryInfo.availMem > 200 * 1024 * 1024) // 200MB free
    }

    fun getBatteryLevel(): Int {
        val batteryStatus = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    fun isLowBattery(): Boolean {
        val level = getBatteryLevel()
        return level in 0..15
    }

    fun getStatusSummary(): String {
        return buildString {
            append("Memory: ${if (isMemoryOk()) "OK" else "Low"}")
            append(", Battery: ${getBatteryLevel()}%")
            if (isLowBattery()) append(" (Low Battery)")
        }
    }
}
