package com.nova.luna.grocery

import android.content.Intent

fun interface GroceryProviderLauncher {
    fun launch(intent: Intent): Boolean
}
