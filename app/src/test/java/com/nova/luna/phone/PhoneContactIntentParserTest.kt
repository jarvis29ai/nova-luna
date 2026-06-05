package com.nova.luna.phone

import org.junit.Assert.*
import org.junit.Test

class PhoneContactIntentParserTest {

    private val parser = PhoneContactIntentParser()

    @Test
    fun testParseCallContact() {
        val request = parser.parse("call Rahul")
        assertEquals(PhoneContactCommandType.CALL_SAVED_CONTACT, request.commandType)
        assertEquals("rahul", request.contactName)
    }

    @Test
    fun testParseCallContactWithLabel() {
        val request = parser.parse("call Mom mobile")
        assertEquals(PhoneContactCommandType.CALL_SAVED_CONTACT, request.commandType)
        assertEquals("mom", request.contactName)
        assertEquals("mobile", request.label)
    }

    @Test
    fun testParseCallUnknownNumber() {
        val request = parser.parse("call 9876543210")
        assertEquals(PhoneContactCommandType.CALL_UNKNOWN_PERSON, request.commandType)
        assertEquals("9876543210", request.phoneNumber)
    }

    @Test
    fun testParseCallFromMessage() {
        val request = parser.parse("call number from Rahul's message")
        assertEquals(PhoneContactCommandType.CALL_NUMBER_FROM_MESSAGE, request.commandType)
        assertEquals("rahul", request.senderName)
    }

    @Test
    fun testParseCallFromWhatsApp() {
        val request = parser.parse("call the number sent by Priya on WhatsApp")
        assertEquals(PhoneContactCommandType.CALL_NUMBER_FROM_MESSAGE, request.commandType)
        assertEquals("priya", request.senderName)
        assertEquals("WhatsApp", request.sourceApp)
    }

    @Test
    fun testParseCreateContact() {
        val request = parser.parse("save this number as Rohan")
        assertEquals(PhoneContactCommandType.CREATE_NEW_CONTACT, request.commandType)
        assertEquals("rohan", request.contactName)
    }

    @Test
    fun testParseCreateContactWithNumber() {
        val request = parser.parse("create contact for Ankit 9876543210")
        assertEquals(PhoneContactCommandType.CREATE_NEW_CONTACT, request.commandType)
        assertEquals("ankit", request.contactName)
        assertEquals("9876543210", request.phoneNumber)
    }

    @Test
    fun testParseUpdateContact() {
        val request = parser.parse("update Rahul number")
        assertEquals(PhoneContactCommandType.UPDATE_CONTACT, request.commandType)
        assertEquals("rahul", request.contactName)
    }

    @Test
    fun testParseSelection() {
        assertEquals(0, parser.parseSelection("call first one"))
        assertEquals(1, parser.parseSelection("the second one"))
        assertEquals(2, parser.parseSelection("3"))
    }

    @Test
    fun testParseConfirmation() {
        assertEquals(true, parser.isConfirmation("yes"))
        assertEquals(true, parser.isConfirmation("yeah sure"))
        assertEquals(false, parser.isConfirmation("no stop"))
        assertEquals(null, parser.isConfirmation("maybe"))
    }
}
