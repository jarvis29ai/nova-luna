package com.nova.luna.phone

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhoneContactSafetyTest {

    @Mock
    private lateinit var context: Context
    @Mock
    private lateinit var permissionChecker: PhoneContactPermissionChecker

    private lateinit var executor: PhoneCallExecutor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        executor = PhoneCallExecutor(context, permissionChecker)
    }

    @Test
    fun testEmergencyNumberSafety() {
        val emergencyNumbers = listOf("911", "100", "101", "112")
        
        for (number in emergencyNumbers) {
            val target = PhoneCallTarget(null, number, isEmergency = true)
            // Should always open dialer for emergency numbers, never direct call
            val result = executor.executeCall(target, forceDialer = false)
            assertEquals(PhoneCallResult.DIALER_OPENED, result)
        }
    }

    @Test
    fun testDirectCallRequiresPermission() {
        val target = PhoneCallTarget("Rahul", "9876543210")
        
        `when`(permissionChecker.hasCallPhonePermission()).thenReturn(false)
        
        val result = executor.executeCall(target, forceDialer = false)
        assertEquals(PhoneCallResult.DIALER_OPENED, result)
    }

    @Test
    fun testDirectCallWithPermission() {
        val target = PhoneCallTarget("Rahul", "9876543210")
        
        `when`(permissionChecker.hasCallPhonePermission()).thenReturn(true)
        
        val result = executor.executeCall(target, forceDialer = false)
        assertEquals(PhoneCallResult.CALL_STARTED, result)
    }
}
