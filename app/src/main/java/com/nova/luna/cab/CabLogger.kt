package com.nova.luna.cab

import android.util.Log

internal object CabLogger {
    private const val TAG = "NovaLunaCab"

    fun d(event: String, fields: Map<String, Any?> = emptyMap()) {
        Log.d(TAG, format(event, fields))
    }

    fun i(event: String, fields: Map<String, Any?> = emptyMap()) {
        Log.i(TAG, format(event, fields))
    }

    fun w(event: String, fields: Map<String, Any?> = emptyMap()) {
        Log.w(TAG, format(event, fields))
    }

    fun e(event: String, fields: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        Log.e(TAG, format(event, fields), throwable)
    }

    private fun format(event: String, fields: Map<String, Any?>): String {
        if (fields.isEmpty()) {
            return event
        }

        val details = fields.entries.joinToString(separator = ", ") { (key, value) ->
            "$key=${value ?: "null"}"
        }
        return "$event | $details"
    }
}
