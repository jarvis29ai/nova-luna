package com.nova.luna.screen

import java.util.Locale

class DefaultScreenClassifier : ScreenClassifier {

    override fun classify(snapshot: ScreenSnapshot): ScreenSnapshot {
        val detectedType = determineScreenType(snapshot)
        val fullRiskSignals = detectRiskSignals(snapshot)

        return snapshot.copy(
            detectedScreenType = detectedType,
            riskSignals = fullRiskSignals
        )
    }

    private fun determineScreenType(snapshot: ScreenSnapshot): ScreenType {
        val pkg = snapshot.packageName.lowercase(Locale.US)
        val allText = snapshot.screenText.joinToString(" ").lowercase(Locale.US)
        
        // Risky screens take precedence
        if (isPaymentOrCheckout(allText)) return ScreenType.PAYMENT_OR_CHECKOUT
        if (isOtpScreen(allText)) return ScreenType.OTP_SCREEN
        if (isLoginOrAuth(allText)) return ScreenType.LOGIN_OR_AUTH
        if (isCaptchaScreen(allText)) return ScreenType.CAPTCHA_SCREEN
        if (isPermissionScreen(pkg, allText)) return ScreenType.PERMISSION_SCREEN
        
        // App-specific classification
        if (pkg.contains("youtube")) {
            return if (allText.contains("search youtube") || snapshot.elements.any { it.type == ScreenElementType.SEARCH_FIELD }) {
                ScreenType.YOUTUBE_SEARCH
            } else {
                ScreenType.YOUTUBE_HOME
            }
        }
        
        if (pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("searchbox")) {
            return ScreenType.BROWSER_SEARCH
        }
        
        if (pkg.contains("uber") || pkg.contains("ola")) {
            return if (allText.contains("where to") || allText.contains("enter destination")) {
                ScreenType.CAB_LOCATION_ENTRY
            } else if (allText.contains("confirm") || allText.contains("choose a ride")) {
                ScreenType.CAB_FARE_RESULTS
            } else {
                ScreenType.CAB_HOME
            }
        }
        
        if (pkg.contains("zomato") || pkg.contains("swiggy")) {
            if (allText.contains("restaurant") && allText.contains("menu")) return ScreenType.FOOD_RESTAURANT_RESULTS
            return if (allText.contains("search for restaurant") || snapshot.elements.any { it.type == ScreenElementType.SEARCH_FIELD }) {
                ScreenType.FOOD_SEARCH
            } else {
                ScreenType.FOOD_HOME
            }
        }
        
        if (pkg.contains("blinkit") || pkg.contains("instamart") || pkg.contains("zepto")) {
            return if (snapshot.elements.any { it.type == ScreenElementType.SEARCH_FIELD }) {
                ScreenType.GROCERY_SEARCH
            } else {
                ScreenType.GROCERY_HOME
            }
        }
        
        if (pkg.contains("messaging") || pkg.contains("whatsapp") || pkg.contains("telegram")) {
            if (snapshot.elements.any { it.type == ScreenElementType.TEXT_FIELD } && (allText.contains("message") || allText.contains("type"))) {
                return ScreenType.MESSAGE_DRAFT
            }
        }
        
        if (pkg.contains("dialer") || pkg.contains("phone")) {
            return ScreenType.CALL_DIALER
        }
        
        if (pkg.contains("settings")) {
            return ScreenType.SETTINGS_SCREEN
        }

        if (pkg.contains("camera")) {
            return ScreenType.CAMERA_SCREEN
        }

        if (snapshot.elements.any { it.type == ScreenElementType.SEARCH_FIELD }) {
            return ScreenType.SEARCH_SCREEN
        }
        
        if (pkg.contains("launcher") || pkg.contains("home")) {
            return ScreenType.HOME_SCREEN
        }

        return ScreenType.UNKNOWN
    }

    private fun detectRiskSignals(snapshot: ScreenSnapshot): List<String> {
        val signals = mutableSetOf<String>()
        val allText = snapshot.screenText.joinToString(" ").lowercase(Locale.US)
        
        val riskKeywords = mapOf(
            "payment" to listOf("pay ", "checkout", "place order", "make payment", "upi", "card details"),
            "booking" to listOf("book now", "confirm booking", "confirm ride"),
            "auth" to listOf("password", "sign in", "log in", "enter pin"),
            "otp" to listOf("otp", "one time password", "verification code"),
            "captcha" to listOf("captcha", "verify you are human"),
            "destructive" to listOf("delete account", "remove account", "factory reset", "erase all"),
            "permission" to listOf("allow", "deny", "permission")
        )
        
        for ((signal, keywords) in riskKeywords) {
            for (keyword in keywords) {
                if (allText.contains(keyword)) {
                    signals.add(signal)
                    break
                }
            }
        }
        
        if (snapshot.elements.any { it.isPassword }) {
            signals.add("auth")
        }
        
        return signals.toList()
    }

    private fun isPaymentOrCheckout(text: String): Boolean {
        return text.contains("checkout") || text.contains("place order") || text.contains("pay now") || text.contains("make payment") || text.contains("upi")
    }

    private fun isOtpScreen(text: String): Boolean {
        return text.contains("enter otp") || text.contains("verification code") || text.contains("one time password")
    }

    private fun isLoginOrAuth(text: String): Boolean {
        return text.contains("password") || text.contains("sign in") || text.contains("log in") || text.contains("enter pin")
    }

    private fun isCaptchaScreen(text: String): Boolean {
        return text.contains("captcha") || text.contains("verify you are human") || text.contains("i'm not a robot")
    }

    private fun isPermissionScreen(pkg: String, text: String): Boolean {
        return pkg.contains("permission") || (text.contains("allow") && text.contains("deny") && text.contains("permission"))
    }
}
