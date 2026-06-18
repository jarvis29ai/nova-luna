package com.nova.luna.util

class NoOpNovaLogger : NovaLogger {
    override fun i(tag: String, message: String) { }
    override fun d(tag: String, message: String) { }
    override fun e(tag: String, message: String) { }
    override fun w(tag: String, message: String) { }
}