package com.nova.luna.shopping

class ShoppingSafetyDetector {
    fun isSensitiveScreen(screenTitle: String?, packageName: String?): Boolean {
        val lowerTitle = screenTitle?.lowercase() ?: ""
        return lowerTitle.contains("payment") || 
               lowerTitle.contains("upi") || 
               lowerTitle.contains("otp") || 
               lowerTitle.contains("cvv") || 
               lowerTitle.contains("password") || 
               lowerTitle.contains("login")
    }
}
