package com.nova.luna.brain

import android.app.ActivityManager
import android.content.Context

interface RamInfoProvider {
    fun getAvailableRamMb(): Int
    fun getTotalRamMb(): Int
}

class AndroidRamInfoProvider(private val context: Context) : RamInfoProvider {
    override fun getAvailableRamMb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.availMem / (1024 * 1024)).toInt()
    }

    override fun getTotalRamMb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.totalMem / (1024 * 1024)).toInt()
    }
}

class FakeRamInfoProvider(
    var _availableRamMb: Int = 4096,
    var _totalRamMb: Int = 8192
) : RamInfoProvider {
    override fun getAvailableRamMb(): Int = _availableRamMb
    override fun getTotalRamMb(): Int = _totalRamMb
}
