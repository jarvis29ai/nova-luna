package com.nova.luna.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PhoneContactPermissionChecker(private val context: Context) {

    fun hasReadContactsPermission(): Boolean {
        return hasPermission(Manifest.permission.READ_CONTACTS)
    }

    fun hasWriteContactsPermission(): Boolean {
        return hasPermission(Manifest.permission.WRITE_CONTACTS)
    }

    fun hasCallPhonePermission(): Boolean {
        return hasPermission(Manifest.permission.CALL_PHONE)
    }

    fun hasReadCallLogPermission(): Boolean {
        return hasPermission(Manifest.permission.READ_CALL_LOG)
    }

    fun hasReadSmsPermission(): Boolean {
        return hasPermission(Manifest.permission.READ_SMS)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getRequiredPermissions(commandType: PhoneContactCommandType): List<String> {
        val permissions = mutableListOf<String>()
        when (commandType) {
            PhoneContactCommandType.CALL_SAVED_CONTACT -> {
                permissions.add(Manifest.permission.READ_CONTACTS)
            }
            PhoneContactCommandType.CALL_UNKNOWN_PERSON -> {
                permissions.add(Manifest.permission.READ_CONTACTS)
                permissions.add(Manifest.permission.READ_CALL_LOG)
            }
            PhoneContactCommandType.CALL_NUMBER_FROM_MESSAGE -> {
                permissions.add(Manifest.permission.READ_SMS)
            }
            PhoneContactCommandType.CREATE_NEW_CONTACT -> {
                permissions.add(Manifest.permission.WRITE_CONTACTS)
            }
            PhoneContactCommandType.UPDATE_CONTACT -> {
                permissions.add(Manifest.permission.WRITE_CONTACTS)
                permissions.add(Manifest.permission.READ_CONTACTS)
            }
            else -> {}
        }
        return permissions
    }
}
