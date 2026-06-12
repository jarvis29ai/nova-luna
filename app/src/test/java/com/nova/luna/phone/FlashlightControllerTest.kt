package com.nova.luna.phone

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FlashlightControllerTest {

    private val context = mock(Context::class.java)
    private val cameraManager = mock(CameraManager::class.java)
    private val characteristics = mock(CameraCharacteristics::class.java)
    private lateinit var controller: FlashlightController

    @Before
    fun setup() {
        `when`(context.getSystemService(Context.CAMERA_SERVICE)).thenReturn(cameraManager)
        controller = FlashlightController(context)
    }

    @Test
    fun `flashlight available and on succeeds`() {
        val cameraId = "0"
        `when`(cameraManager.cameraIdList).thenReturn(arrayOf(cameraId))
        `when`(cameraManager.getCameraCharacteristics(cameraId)).thenReturn(characteristics)
        `when`(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)).thenReturn(true)
        
        val status = controller.setFlashlight(true)
        
        assertEquals(FlashlightController.FlashlightStatus.FLASHLIGHT_ON, status)
        verify(cameraManager).setTorchMode(cameraId, true)
    }

    @Test
    fun `flashlight unavailable returns FLASHLIGHT_UNAVAILABLE`() {
        `when`(cameraManager.cameraIdList).thenReturn(emptyArray())
        
        val status = controller.setFlashlight(true)
        
        assertEquals(FlashlightController.FlashlightStatus.FLASHLIGHT_UNAVAILABLE, status)
    }
}
