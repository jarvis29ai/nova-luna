package com.nova.luna.phone

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract

class PhoneContactWriter(private val context: Context) {

    fun createContact(name: String, number: String, label: String? = null): ContactWriteResult {
        try {
            val ops = ArrayList<ContentProviderOperation>()

            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build())

            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build())

            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, getPhoneType(label))
                .build())

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            return ContactWriteResult.CONTACT_CREATED
        } catch (e: Exception) {
            return ContactWriteResult.FAILED
        }
    }

    fun updateContact(contactId: String, newNumber: String, label: String? = null): ContactWriteResult {
        try {
            val ops = ArrayList<ContentProviderOperation>()

            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, contactId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumber)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, getPhoneType(label))
                .build())

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            return ContactWriteResult.CONTACT_UPDATED
        } catch (e: Exception) {
            return ContactWriteResult.FAILED
        }
    }

    private fun getPhoneType(label: String?): Int {
        return when (label?.lowercase()) {
            "mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
            "work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
            else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
        }
    }
}
