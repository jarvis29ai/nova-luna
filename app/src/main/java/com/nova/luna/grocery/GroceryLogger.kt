package com.nova.luna.grocery

import android.util.Log
import java.util.Locale

internal object GroceryLogger {
    private const val TAG = "NovaLunaGrocery"

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
        if (fields.isEmpty()) return event

        val details = fields.entries.joinToString(separator = ", ") { (key, value) ->
            "$key=${sanitize(key, value)}"
        }
        return "$event | $details"
    }

    private fun sanitize(key: String, value: Any?): String {
        if (value == null) return "null"

        val sensitiveKey = listOf(
            "otp",
            "pin",
            "cvv",
            "password",
            "payment",
            "upi",
            "card",
            "address",
            "phone"
        ).any { key.lowercase(Locale.US).contains(it) }

        if (sensitiveKey) return "[redacted]"

        val text = value.toString()
        val sensitiveValue = listOf(
            "otp",
            "pin",
            "cvv",
            "password",
            "upi",
            "card",
            "payment"
        ).any { text.lowercase(Locale.US).contains(it) }

        return if (sensitiveValue) "[redacted]" else text
    }
}
