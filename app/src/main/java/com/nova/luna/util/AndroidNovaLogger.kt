package com.nova.luna.util

class AndroidNovaLogger : NovaLogger {
    override fun i(tag: String, message: String) {
        android.util.Log.i(tag, message)
    }

    override fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }

    override fun e(tag: String, message: String) {
        android.util.Log.e(tag, message)
    }

    override fun w(tag: String, message: String) {
        android.util.Log.w(tag, message)
    }
}