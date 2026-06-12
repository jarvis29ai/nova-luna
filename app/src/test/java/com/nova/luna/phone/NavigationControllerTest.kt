package com.nova.luna.phone

import android.accessibilityservice.AccessibilityService
import com.nova.luna.service.NovaAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationControllerTest {

    private val service = mock(NovaAccessibilityService::class.java)
    private val controller = NavigationController()

    @Test
    fun `go home succeeds when accessibility ready`() {
        NovaAccessibilityService.setTestInstance(service)
        `when`(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)).thenReturn(true)
        
        val status = controller.goHome()
        
        assertEquals(NavigationController.NavigationStatus.SUCCESS, status)
        verify(service).performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    @Test
    fun `go back returns ACCESSIBILITY_NOT_READY when service null`() {
        NovaAccessibilityService.setTestInstance(null)
        
        val status = controller.goBack()
        
        assertEquals(NavigationController.NavigationStatus.ACCESSIBILITY_NOT_READY, status)
    }
}
