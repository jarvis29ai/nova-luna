package com.nova.luna.phone

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.mockito.ArgumentMatchers.any as mockitoAny

@RunWith(RobolectricTestRunner::class)
class PhoneContactOrchestratorTest {

    @Mock
    private lateinit var context: Context
    @Mock
    private lateinit var parser: PhoneContactIntentParser
    @Mock
    private lateinit var permissionChecker: PhoneContactPermissionChecker
    @Mock
    private lateinit var contactRepo: PhoneContactRepository
    @Mock
    private lateinit var callLogRepo: PhoneCallLogRepository
    @Mock
    private lateinit var messageRepo: PhoneMessageSearchRepository
    @Mock
    private lateinit var executor: PhoneCallExecutor
    @Mock
    private lateinit var writer: PhoneContactWriter

    private lateinit var orchestrator: PhoneContactOrchestrator
    private val voiceResponses = PhoneContactVoiceResponses()

    private fun <T> safeAny(type: Class<T>): T = mockitoAny(type) ?: createInstance(type)

    @Suppress("UNCHECKED_CAST")
    private fun <T> createInstance(type: Class<T>): T {
        return when (type) {
            PhoneCallTarget::class.java -> PhoneCallTarget(null, "", false) as T
            else -> mock(type)
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        orchestrator = PhoneContactOrchestrator(
            context, parser, permissionChecker, contactRepo, callLogRepo, messageRepo, executor, writer, voiceResponses
        )
    }

    @Test
    fun testCallSavedContactExactMatch() {
        val request = PhoneContactRequest(
            commandType = PhoneContactCommandType.CALL_SAVED_CONTACT,
            contactName = "rahul",
            rawText = "call rahul"
        )
        val match = PhoneContactMatch("1", "Rahul", listOf(PhoneNumberCandidate("9876543210", "mobile", PhoneContactSearchSource.PHONE_CONTACTS)))
        
        `when`(permissionChecker.hasReadContactsPermission()).thenReturn(true)
        `when`(contactRepo.searchContacts("rahul")).thenReturn(listOf(match))
        `when`(executor.executeCall(safeAny(PhoneCallTarget::class.java), anyBoolean())).thenReturn(PhoneCallResult.CALL_STARTED)

        val result = orchestrator.start(request)
        
        assertEquals(PhoneContactStatus.SUCCESS, result.status)
        assertTrue(result.message.contains("Calling Rahul"))
    }

    @Test
    fun testCallSavedContactMultipleNumbers() {
        val request = PhoneContactRequest(
            commandType = PhoneContactCommandType.CALL_SAVED_CONTACT,
            contactName = "rahul",
            rawText = "call rahul"
        )
        val match = PhoneContactMatch("1", "Rahul", listOf(
            PhoneNumberCandidate("111", "mobile", PhoneContactSearchSource.PHONE_CONTACTS),
            PhoneNumberCandidate("222", "work", PhoneContactSearchSource.PHONE_CONTACTS)
        ))
        
        `when`(permissionChecker.hasReadContactsPermission()).thenReturn(true)
        `when`(contactRepo.searchContacts("rahul")).thenReturn(listOf(match))

        val result = orchestrator.start(request)
        
        assertEquals(PhoneContactStatus.NEEDS_USER_INPUT, result.status)
        assertTrue(result.message.contains("multiple numbers"))
        
        // Simulate user selection
        `when`(parser.parseSelection(anyString())).thenReturn(0)
        `when`(executor.executeCall(safeAny(PhoneCallTarget::class.java), anyBoolean())).thenReturn(PhoneCallResult.CALL_STARTED)
        
        val followUpResult = orchestrator.handleUserInput("the first one")
        assertEquals(PhoneContactStatus.SUCCESS, followUpResult.status)
        assertTrue(followUpResult.message.contains("Calling Rahul"))
    }

    @Test
    fun testCallFromMessageFlow() {
        val request = PhoneContactRequest(
            commandType = PhoneContactCommandType.CALL_NUMBER_FROM_MESSAGE,
            senderName = "priya",
            rawText = "call number from priya"
        )
        val candidate = MessageNumberCandidate("9876543210", "Priya", "SMS", System.currentTimeMillis())
        
        `when`(permissionChecker.hasReadSmsPermission()).thenReturn(true)
        `when`(messageRepo.searchSms("priya")).thenReturn(listOf(candidate))

        val result = orchestrator.start(request)
        
        assertEquals(PhoneContactStatus.NEEDS_CONFIRMATION, result.status)
        assertTrue(result.message.contains("9876543210"))

        // User says yes
        `when`(parser.isConfirmation(anyString())).thenReturn(true)
        `when`(executor.executeCall(safeAny(PhoneCallTarget::class.java), anyBoolean())).thenReturn(PhoneCallResult.CALL_STARTED)
        
        val followUpResult = orchestrator.handleUserInput("yes")
        assertEquals(PhoneContactStatus.SUCCESS, followUpResult.status)
    }

    @Test
    fun testUpdateContactFlow() {
        val request = PhoneContactRequest(
            commandType = PhoneContactCommandType.UPDATE_CONTACT,
            contactName = "rahul",
            phoneNumber = "999",
            rawText = "update rahul number to 999"
        )
        val match = PhoneContactMatch("1", "Rahul", emptyList())
        
        `when`(contactRepo.searchContacts("rahul")).thenReturn(listOf(match))
        
        val result = orchestrator.start(request)
        
        assertEquals(PhoneContactStatus.NEEDS_CONFIRMATION, result.status)
        assertTrue(result.message.contains("already exists"))

        // User chooses update
        `when`(permissionChecker.hasWriteContactsPermission()).thenReturn(true)
        `when`(writer.updateContact(anyString(), anyString(), nullable(String::class.java))).thenReturn(ContactWriteResult.CONTACT_UPDATED)
        
        val followUpResult = orchestrator.handleUserInput("update it")
        assertEquals(PhoneContactStatus.SUCCESS, followUpResult.status)
        assertTrue(followUpResult.message.contains("updated successfully"))
    }

    @Test
    fun testCancelFlow() {
        val request = PhoneContactRequest(
            commandType = PhoneContactCommandType.CALL_UNKNOWN_PERSON,
            phoneNumber = "999",
            rawText = "call 999"
        )
        
        val result = orchestrator.start(request)
        assertEquals(PhoneContactStatus.NEEDS_CONFIRMATION, result.status)

        // User says no
        `when`(parser.isConfirmation(anyString())).thenReturn(false)
        
        val followUpResult = orchestrator.handleUserInput("no")
        assertEquals(PhoneContactStatus.CANCELLED, followUpResult.status)
    }

    @Test
    fun testPermissionBlocked() {
        val request = PhoneContactRequest(
            commandType = PhoneContactCommandType.CALL_SAVED_CONTACT,
            contactName = "rahul",
            rawText = "call rahul"
        )
        
        `when`(permissionChecker.hasReadContactsPermission()).thenReturn(false)

        val result = orchestrator.start(request)
        
        assertEquals(PhoneContactStatus.BLOCKED, result.status)
        assertTrue(result.message.contains("permission"))
    }
}
