package com.nova.luna.phone

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhoneActionExecutorOpenAppTest {

    private val context = mock(Context::class.java)
    private val pm = mock(PackageManager::class.java)
    private val appResolver = mock(AppResolver::class.java)
    private val flashlightController = mock(FlashlightController::class.java)
    private val navigationController = mock(NavigationController::class.java)
    private lateinit var executor: AndroidPhoneActionExecutor

    @Before
    fun setup() {
        `when`(context.packageManager).thenReturn(pm)
        `when`(context.applicationContext).thenReturn(context)
        executor = AndroidPhoneActionExecutor(context, appResolver, flashlightController, navigationController)
    }

    @Test
    fun `open installed app succeeds`() {
        val appName = "YouTube"
        val packageName = "com.google.android.youtube"
        val launchIntent = Intent("launch")
        
        `when`(appResolver.resolvePackage(appName)).thenReturn(packageName)
        `when`(pm.getLaunchIntentForPackage(packageName)).thenReturn(launchIntent)
        
        val action = BrainAction(
            intent = "OPEN_APP",
            actionType = BrainActionType.OPEN_APP,
            params = mapOf("appName" to appName),
            riskLevel = com.nova.luna.model.BrainRiskLevel.LOW,
            requiresConfirmation = false
        )
        
        val result = executor.execute(action)
        
        assertTrue(result.success)
        assertTrue(result.attempted)
        assertEquals(packageName, result.packageName)
        verify(context).startActivity(launchIntent)
    }

    @Test
    fun `open unknown app returns APP_NOT_FOUND`() {
        val appName = "NonExistentApp"
        
        `when`(appResolver.resolvePackage(appName)).thenReturn(null)
        
        val action = BrainAction(
            intent = "OPEN_APP",
            actionType = BrainActionType.OPEN_APP,
            params = mapOf("appName" to appName),
            riskLevel = com.nova.luna.model.BrainRiskLevel.LOW,
            requiresConfirmation = false
        )
        
        val result = executor.execute(action)
        
        assertFalse(result.success)
        assertTrue(result.attempted)
        assertEquals("APP_NOT_FOUND", result.errorCode)
    }

    @Test
    fun `missing launch intent returns LAUNCH_INTENT_NOT_FOUND`() {
        val appName = "YouTube"
        val packageName = "com.google.android.youtube"
        
        `when`(appResolver.resolvePackage(appName)).thenReturn(packageName)
        `when`(pm.getLaunchIntentForPackage(packageName)).thenReturn(null)
        
        val action = BrainAction(
            intent = "OPEN_APP",
            actionType = BrainActionType.OPEN_APP,
            params = mapOf("appName" to appName),
            riskLevel = com.nova.luna.model.BrainRiskLevel.LOW,
            requiresConfirmation = false
        )
        
        val result = executor.execute(action)
        
        assertFalse(result.success)
        assertTrue(result.attempted)
        assertEquals("LAUNCH_INTENT_NOT_FOUND", result.errorCode)
    }
}
