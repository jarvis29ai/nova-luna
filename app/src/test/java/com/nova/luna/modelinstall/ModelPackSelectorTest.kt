package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPackSelectorTest {
    private val selector = ModelPackSelector()

    @Test
    fun lowRamChoosesLitePack() {
        val selection = selector.select(
            DeviceCapabilitySnapshot(
                totalRamMb = 3072,
                freeStorageMb = 1024,
                androidVersion = 34,
                cpuAbi = "arm64-v8a"
            )
        )

        assertEquals(ModelPackId.LITE, selection.packId)
        assertTrue(selection.reason.contains("Lite"))
    }

    @Test
    fun midRamChoosesCorePack() {
        val selection = selector.select(
            DeviceCapabilitySnapshot(
                totalRamMb = 5120,
                freeStorageMb = 2048,
                androidVersion = 34,
                cpuAbi = "arm64-v8a"
            )
        )

        assertEquals(ModelPackId.CORE, selection.packId)
        assertTrue(selection.reason.contains("Core"))
    }

    @Test
    fun highRamChoosesFullPack() {
        val selection = selector.select(
            DeviceCapabilitySnapshot(
                totalRamMb = 8192,
                freeStorageMb = 4096,
                androidVersion = 34,
                cpuAbi = "arm64-v8a"
            )
        )

        assertEquals(ModelPackId.FULL, selection.packId)
        assertTrue(selection.reason.contains("Full"))
    }

    @Test
    fun unsupportedAbiFallsBackToLitePack() {
        val selection = selector.select(
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
