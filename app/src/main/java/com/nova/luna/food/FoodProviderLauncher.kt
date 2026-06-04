package com.nova.luna.food

import android.content.Intent

fun interface FoodProviderLauncher {
    fun launch(intent: Intent): Boolean
}
