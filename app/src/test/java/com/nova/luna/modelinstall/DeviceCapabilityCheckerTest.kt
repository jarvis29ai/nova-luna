package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityCheckerTest {
    private val checker = DeviceCapabilityChecker()

    @Test
    fun selectsLiteForLowRamDevices() {
        val selection = checker.select(
            DeviceCapabilitySnapshot(
                totalRamMb = 3072,
                freeStorageMb = 1024,
                androidVersion = 34,
                cpuAbi = "arm64-v8a"
            )
        )

        assertEquals(ModelPackId.LITE, selection.packId)
        assertTrue(selection.reason.contains("Lite"))
        assertEquals(ModelPackId.LITE, checker.recommendedPackId(
            DeviceCapabilitySnapshot(
                totalRamMb = 3072,
                freeStorageMb = 1024,
                androidVersion = 34,
                cpuAbi = "arm64-v8a"
            )
        ))
    }

    @Test
    fun fallsBackToLiteForNonArm64Devices() {
        val selection = checker.select(
            DeviceCapabilitySnapshot(
                totalRamMb = 8192,
                freeStorageMb = 4096,
                androidVersion = 34,
                cpuAbi = "x86_64"
            )
        )

        assertEquals(ModelPackId.LITE, selection.packId)
        assertTrue(selection.warnings.any { it.contains("ABI") })
    }
}
