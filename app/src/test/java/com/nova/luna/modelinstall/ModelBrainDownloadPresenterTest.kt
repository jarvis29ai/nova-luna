package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ModelBrainDownloadPresenterTest {
    @Test
    fun reportShowsModelSourceNotConfiguredSafely() {
        val baseDir = Files.createTempDirectory("nova_luna_brain_download_report_test").toFile()
        try {
            val manager = DefaultModelManager(PrivateAppModelStorage.from(baseDir))
            val presenter = ModelBrainDownloadPresenter(manager)
            val report = presenter.buildReport(sampleSnapshot())

            assertFalse(report.canDownloadRecommended)
            assertEquals(MODEL_SOURCE_NOT_CONFIGURED_MESSAGE, report.recommendedActionLabel)
            assertTrue(report.toText().contains("model source not configured", ignoreCase = true))
            assertFalse(report.toText().contains(".gguf", ignoreCase = true))
            assertFalse(report.toText().contains("model_install", ignoreCase = true))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun reportAllowsDownloadWhenSourceIsConfigured() {
        val payload = "downloadable payload".toByteArray()
        val pack = ModelPackSpec(
            id = ModelPackId.CORE,
            displayName = "Core",
            description = "Test pack for the core brain.",
            requirement = ModelPackRequirement(
                minRamMb = 1,
                minFreeStorageMb = 1
            ),
            files = listOf(
                ModelFileSpec(
                    fileName = "gemma-3n-q4.gguf",
                    relativePath = "core",
                    sha256 = sha256Hex(payload),
                    byteCount = payload.size.toLong()
                )
            )
        ).normalized()

        withMaintenanceEnvironment(
            catalog = listOf(pack)
        ) { env ->
            val manager = DefaultModelManager(
                env.coordinator,
                capabilityChecker = DeviceCapabilityChecker(ModelPackSelector(catalog = listOf(pack)))
            )
            val presenter = ModelBrainDownloadPresenter(manager)
            val report = presenter.buildReport(sampleSnapshot())

            assertTrue(report.canDownloadRecommended)
            assertEquals("Download available.", report.recommendedActionLabel)
            assertTrue(report.toText().contains("Download available", ignoreCase = true))
        }
    }

    private fun sampleSnapshot(): DeviceCapabilitySnapshot {
        return DeviceCapabilitySnapshot(
            totalRamMb = 8192,
            freeStorageMb = 4096,
            androidVersion = 34,
            cpuAbi = "arm64-v8a",
            networkAvailable = true
        )
    }
}
