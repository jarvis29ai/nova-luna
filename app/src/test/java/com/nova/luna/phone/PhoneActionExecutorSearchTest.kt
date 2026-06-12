package com.nova.luna.phone

import android.content.Context
import android.content.Intent
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhoneActionExecutorSearchTest {

    private val context = mock(Context::class.java)
    private val appResolver = mock(AppResolver::class.java)
    private val flashlightController = mock(FlashlightController::class.java)
    private val navigationController = mock(NavigationController::class.java)
    private lateinit var executor: AndroidPhoneActionExecutor

    @Before
    fun setup() {
        executor = AndroidPhoneActionExecutor(context, appResolver, flashlightController, navigationController)
    }

    @Test
    fun `search with query succeeds`() {
        val action = BrainAction(
            intent = "SEARCH_WEB",
            actionType = BrainActionType.SEARCH_WEB,
            params = mapOf("query" to "weather"),
            riskLevel = com.nova.luna.model.BrainRiskLevel.LOW,
            requiresConfirmation = false
        )
        
        val result = executor.execute(action)
        
        assertTrue(result.success)
        verify(context).startActivity(any(Intent::class.java))
    }

    @Test
    fun `empty query returns EMPTY_QUERY`() {
        val action = BrainAction(
            intent = "SEARCH_WEB",
            actionType = BrainActionType.SEARCH_WEB,
            params = mapOf("query" to ""),
            riskLevel = com.nova.luna.model.BrainRiskLevel.LOW,
            requiresConfirmation = false
        )
        
        val result = executor.execute(action)
        
        assertFalse(result.success)
        assertEquals("EMPTY_QUERY", result.errorCode)
    }
}
