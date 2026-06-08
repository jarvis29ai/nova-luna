package com.nova.luna.executor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.*
import com.nova.luna.screen.ScreenState
import com.nova.luna.service.NovaAccessibilityService
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.lang.reflect.Field
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.anyLong

@RunWith(RobolectricTestRunner::class)
class ActionExecutorPhase1Test {
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
    fun `executor returns PERMISSION_REQUIRED when service is not enabled`() {
        installServiceInstance(null)
        val executor = ActionExecutor(context)
        val result = executor.execute(CommandIntent(rawText = "tap Search", actionType = ActionType.TAP_TEXT, entities = mapOf("text" to "Search")))
        
        assertEquals(ActionResultStatus.PERMISSION_REQUIRED, result.status)
        assertFalse(result.success)
    }

    @Test
    fun `executor handles TAP_TEXT success and failure`() {
        Mockito.`when`(service.clickByTextOrDescription("Search")).thenReturn(true, false)
        val executor = ActionExecutor(context)
        
        // Success
        val result1 = executor.execute(CommandIntent(rawText = "tap Search", actionType = ActionType.TAP_TEXT, entities = mapOf("text" to "Search")))
        assertTrue(result1.success)
        assertEquals(ActionResultStatus.SUCCESS, result1.status)

        // Failure (after retries)
        val result2 = executor.execute(CommandIntent(rawText = "tap Search", actionType = ActionType.TAP_TEXT, entities = mapOf("text" to "Search")))
        assertFalse(result2.success)
        assertEquals(ActionResultStatus.NOT_FOUND, result2.status)
    }

    @Test
    fun `executor handles TAP_DESCRIPTION`() {
        Mockito.`when`(service.clickByContentDescription("Voice Search")).thenReturn(true)
        val executor = ActionExecutor(context)
        
        val result = executor.execute(CommandIntent(rawText = "tap Voice Search", actionType = ActionType.TAP_DESCRIPTION, entities = mapOf("text" to "Voice Search")))
        assertTrue(result.success)
        assertEquals(ActionResultStatus.SUCCESS, result.status)
    }

    @Test
    fun `executor handles TYPE_TEXT`() {
        Mockito.`when`(service.typeText("hello")).thenReturn(true)
        val executor = ActionExecutor(context)
        
        val result = executor.execute(CommandIntent(rawText = "type hello", actionType = ActionType.TYPE_TEXT, entities = mapOf("text" to "hello")))
        assertTrue(result.success)
    }

    @Test
    fun `executor handles SCROLL actions`() {
        Mockito.`when`(service.scrollForward()).thenReturn(true)
        Mockito.`when`(service.scrollBackward()).thenReturn(true)
        val executor = ActionExecutor(context)
        
        assertTrue(executor.execute(CommandIntent(rawText = "scroll down", actionType = ActionType.SCROLL_DOWN)).success)
        assertTrue(executor.execute(CommandIntent(rawText = "scroll up", actionType = ActionType.SCROLL_UP)).success)
    }

    @Test
    fun `executor handles READ_SCREEN`() {
        val state = mockScreenState("Screen Summary")
        Mockito.`when`(service.captureScreenState(Mockito.anyInt())).thenReturn(state)
        val executor = ActionExecutor(context)
        
        val result = executor.execute(CommandIntent(rawText = "read screen", actionType = ActionType.READ_SCREEN))
        assertTrue(result.success)
        assertEquals("Screen Summary", result.entities["screenSummary"])
    }

    @Test
    fun `executor handles WAIT_FOR_TEXT`() {
        Mockito.`when`(service.waitForText(anyString(), anyLong())).thenReturn(true)
        val executor = ActionExecutor(context)
        
        val result = executor.execute(CommandIntent(rawText = "wait for Search", actionType = ActionType.WAIT_FOR_TEXT, entities = mapOf("text" to "Search")))
        assertTrue(result.success)
    }

    private fun mockScreenState(summary: String): ScreenState {
        return ScreenState(
            packageName = "com.pkg",
            appName = "App",
            className = "Class",
            timestampMillis = System.currentTimeMillis(),
            isAccessibilityReady = true,
            summarizedState = summary,
            confidence = 1.0f,
            rawNodeCount = 1,
            truncated = false
        )
    }

    private fun installServiceInstance(instance: NovaAccessibilityService?) {
        val field: Field = NovaAccessibilityService::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, instance)
    }
}
