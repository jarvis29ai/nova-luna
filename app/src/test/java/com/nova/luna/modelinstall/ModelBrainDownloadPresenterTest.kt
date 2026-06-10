package com.nova.luna.modelinstall

import com.nova.luna.model.BrainModelRole
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
            id = ModelPackId.LITE,
            displayName = "Lite",
            description = "Test pack for the lite brain.",
            requirement = ModelPackRequirement(
                minRamMb = 1,
                minFreeStorageMb = 1
            ),
            files = listOf(
                ModelFileSpec(
                    fileName = "gemma-3-270m-q4.gguf",
                    relativePath = "lite",
                    sha256 = sha256Hex(payload),
                    byteCount = payload.size.toLong()
                )
            )
        ).normalized()
        val sourceEntry = ModelSourceEntry(
            role = BrainModelRole.LITE_FALLBACK,
            displayName = pack.displayName,
            fileName = pack.files.first().fileName,
            relativePath = pack.files.first().relativePath,
            downloadUrl = "https://example.com/models/lite.gguf",
            expectedSha256 = sha256Hex(payload),
            expectedByteCount = payload.size.toLong(),
            enabled = true,
            minimumRamMb = pack.requirement.minRamMb,
            minimumFreeStorageMb = pack.requirement.minFreeStorageMb
        ).normalized()

        withConfiguredEnvironment(pack, sourceEntry) { manager ->
            val presenter = ModelBrainDownloadPresenter(manager)
            val report = presenter.buildReport(sampleSnapshot())

            assertTrue(report.recommendedSourceConfigured)
            assertTrue(report.canDownloadRecommended)
            assertEquals("Download available.", report.recommendedActionLabel)
            assertTrue(presenter.buildStatusLine(sampleSnapshot()).contains("Download available", ignoreCase = true))
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

    private inline fun withConfiguredEnvironment(
        pack: ModelPackSpec,
        sourceEntry: ModelSourceEntry,
        block: (DefaultModelManager) -> Unit
    ) {
        val baseDir = Files.createTempDirectory("nova_luna_brain_download_presenter_test").toFile()
        try {
            val storage = PrivateAppModelStorage.from(baseDir)
            val registry = LocalModelRegistry(storage)
            val stateStore = ModelRuntimeStateStore(storage)
            val sourceProvider = ModelDownloadSourceProvider(
                catalog = listOf(pack),
                sourceManifest = ModelSourceManifest(entries = listOf(sourceEntry))
            )
            val coordinator = ModelInstallCoordinator(
                storage = storage,
                catalog = listOf(pack),
                downloadSourceProviderOverride = sourceProvider,
                registryOverride = registry,
                runtimeStateStoreOverride = stateStore
            )
            val manager = DefaultModelManager(
                coordinator,
                capabilityChecker = DeviceCapabilityChecker(ModelPackSelector(catalog = listOf(pack)))
            )
            block(manager)
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
