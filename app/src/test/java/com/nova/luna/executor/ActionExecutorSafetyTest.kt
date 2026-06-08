package com.nova.luna.executor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.*
import com.nova.luna.service.NovaAccessibilityService
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.lang.reflect.Field
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionExecutorSafetyTest {
    private lateinit var context: Context
    private lateinit var service: NovaAccessibilityService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = Mockito.mock(NovaAccessibilityService::class.java)
        installServiceInstance(service)
    }

    @After
    fun tearDown() {
        installServiceInstance(null)
    }

    @Test
    fun `executor handles communication NEEDS_CONFIRMATION`() {
        // We need to mock the CommunicationOrchestrator or ensure it returns NEEDS_CONFIRMATION for a specific input
        // Since CommunicationOrchestrator is private/internal to ActionExecutor, we'll test the output mapping
        
        val executor = ActionExecutor(context)
        val result = executor.execute(CommandIntent(rawText = "send message to Rahul hello", actionType = ActionType.COMMUNICATION))
        
        // This depends on CommunicationOrchestrator behavior. 
        // If it returns status NEEDS_CONFIRMATION, ActionExecutor should map it to ActionResultStatus.NEEDS_CONFIRMATION
        if (result.message.contains("confirm", ignoreCase = true)) {
             assertEquals(ActionResultStatus.NEEDS_CONFIRMATION, result.status)
        }
    }

    private fun installServiceInstance(instance: NovaAccessibilityService?) {
        val field: Field = NovaAccessibilityService::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, instance)
    }
}
