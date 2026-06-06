package com.nova.luna.music

import android.util.Log

/**
 * Logger for the music domain.
 */
object MusicLogger {
    private const val TAG = "MusicAssistant"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }
}
