package com.nova.luna.phone

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.util.Log

class FlashlightController(private val context: Context) {

    companion object {
        private const val TAG = "NovaLunaPhase25"
    }

    enum class FlashlightStatus {
        FLASHLIGHT_ON,
        FLASHLIGHT_OFF,
        FLASHLIGHT_UNAVAILABLE,
        CAMERA_PERMISSION_MISSING,
        FLASHLIGHT_FAILED
    }

    fun setFlashlight(on: Boolean): FlashlightStatus {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            return FlashlightStatus.FLASHLIGHT_UNAVAILABLE
        }

        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId == null) {
                return FlashlightStatus.FLASHLIGHT_UNAVAILABLE
            }

            cameraManager.setTorchMode(cameraId, on)
            if (on) FlashlightStatus.FLASHLIGHT_ON else FlashlightStatus.FLASHLIGHT_OFF
        } catch (e: CameraAccessException) {
            Log.e(TAG, "CameraAccessException while toggling flashlight", e)
            FlashlightStatus.FLASHLIGHT_FAILED
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while toggling flashlight", e)
            FlashlightStatus.CAMERA_PERMISSION_MISSING
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while toggling flashlight", e)
            FlashlightStatus.FLASHLIGHT_FAILED
        }
    }
}
