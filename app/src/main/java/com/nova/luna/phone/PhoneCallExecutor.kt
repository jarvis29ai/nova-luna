package com.nova.luna.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils

class PhoneCallExecutor(private val context: Context, private val permissionChecker: PhoneContactPermissionChecker) {

    fun executeCall(target: PhoneCallTarget, forceDialer: Boolean = false): PhoneCallResult {
        if (isEmergencyNumber(target.number)) {
            return openDialer(target.number)
        }

        return if (!forceDialer && permissionChecker.hasCallPhonePermission()) {
            startDirectCall(target.number)
        } else {
            openDialer(target.number)
        }
    }

    private fun startDirectCall(number: String): PhoneCallResult {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            PhoneCallResult.CALL_STARTED
        } catch (e: Exception) {
            openDialer(number)
        }
    }

    private fun openDialer(number: String): PhoneCallResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            PhoneCallResult.DIALER_OPENED
        } catch (e: Exception) {
            PhoneCallResult.FAILED
        }
    }

    private fun isEmergencyNumber(number: String): Boolean {
        // Basic emergency number detection
        val emergencyNumbers = listOf("100", "101", "102", "108", "112", "911")
        val cleanNumber = number.filter { it.isDigit() }
        return emergencyNumbers.any { cleanNumber == it || cleanNumber.endsWith(it) && cleanNumber.length <= 5 } || 
               PhoneNumberUtils.isEmergencyNumber(number)
    }
}
