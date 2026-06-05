package com.nova.luna.phone

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract

class PhoneContactRepository(private val context: Context) {

    fun searchContacts(name: String): List<PhoneContactMatch> {
        val resolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        val matches = mutableListOf<PhoneContactMatch>()
        
        resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex)
                val numbers = getPhoneNumbers(id)
                matches.add(PhoneContactMatch(id, displayName, numbers))
            }
        }
        
        return matches
    }

    private fun getPhoneNumbers(contactId: String): List<PhoneNumberCandidate> {
        val resolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId)

        val numbers = mutableListOf<PhoneNumberCandidate>()
        
        resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex)
                val type = cursor.getInt(typeIndex)
                val label = getLabelForType(type)
                numbers.add(PhoneNumberCandidate(number, label, PhoneContactSearchSource.PHONE_CONTACTS))
            }
        }
        
        return numbers
    }

    private fun getLabelForType(type: Int): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "other"
            else -> "other"
        }
    }
}
