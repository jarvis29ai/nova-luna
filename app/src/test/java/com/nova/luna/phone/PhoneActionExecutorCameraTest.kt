package com.nova.luna.phone

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhoneActionExecutorCameraTest {

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
    fun `open camera succeeds`() {
        val action = BrainAction(
            intent = "OPEN_CAMERA",
            actionType = BrainActionType.OPEN_CAMERA,
            riskLevel = com.nova.luna.model.BrainRiskLevel.LOW,
            requiresConfirmation = false
        )
        
        val result = executor.execute(action)
        
        assertTrue(result.success)
        assertTrue(result.attempted)
        verify(context).startActivity(any(Intent::class.java))
    }

    @Test
    fun `open camera handles exception`() {
        doThrow(RuntimeException("No camera")).`when`(context).startActivity(any(Intent::class.java))
        
        val action = BrainAction(
            intent = "OPEN_CAMERA",
            actionType = BrainActionType.OPEN_CAMERA,
            riskLevel = com.nova.luna.model.BrainRiskLevel.LOW,
            requiresConfirmation = false
        )
        
        val result = executor.execute(action)
        
        assertFalse(result.success)
        assertTrue(result.attempted)
        assertTrue(result.reason.contains("Could not open camera app"))
    }
}
