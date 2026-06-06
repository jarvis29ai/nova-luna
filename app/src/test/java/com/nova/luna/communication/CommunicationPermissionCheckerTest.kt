package com.nova.luna.communication

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommunicationPermissionCheckerTest {

    private lateinit var context: Context
    private lateinit var checker: CommunicationPermissionChecker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        checker = CommunicationPermissionChecker(context)
    }

    @Test
    fun `test Gmail permission returns blocked when no account`() {
        val status = checker.checkPermission(CommunicationPlatform.GMAIL)
        assertEquals(CommunicationPermissionStatus.BLOCKED_BY_GMAIL_ACCESS, status)
    }

    @Test
    fun `test SMS permission returns blocked when missing`() {
        val status = checker.checkPermission(CommunicationPlatform.SMS)
        assertEquals(CommunicationPermissionStatus.BLOCKED_BY_SMS_PERMISSION, status)
    }
}
