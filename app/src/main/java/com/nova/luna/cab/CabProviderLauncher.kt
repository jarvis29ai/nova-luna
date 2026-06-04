package com.nova.luna.cab

import android.content.Intent

fun interface CabProviderLauncher {
    fun launch(intent: Intent): Boolean
}
