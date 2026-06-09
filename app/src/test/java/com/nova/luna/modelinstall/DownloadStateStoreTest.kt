package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files

class DownloadStateStoreTest {
    @Test
    fun roundTripsPersistedDownloadState() {
        val baseDir = Files.createTempDirectory("nova_luna_download_state_store_test").toFile()
        try {
            val storage = PrivateAppModelStorage.from(baseDir)
            val store = DownloadStateStore(storage)
            val source = ModelDownloadSource(
                packId = ModelPackId.LITE,
                packDisplayName = "Lite",
                sourceId = "lite-source",
                fileName = "lite.gguf",
                relativePath = "lite",
                downloadUrl = "http://127.0.0.1/lite.gguf",
                expectedSha256 = "abc123",
                expectedByteCount = 12L,
                notes = listOf("retry-safe")
            )

            store.markDownloading(
                source = source,
                bytesDownloaded = 3L,
                totalBytes = 12L,
                stagedFilePath = storage.stagedFile(ModelPackId.LITE, "lite.gguf.part").path
            )
            store.markSuccess(
                source = source,
                bytesDownloaded = 12L,
                totalBytes = 12L,
                stagedFilePath = storage.stagedFile(ModelPackId.LITE, "lite.gguf.part").path,
                finalFilePath = storage.packFile(ModelPackId.LITE, "lite.gguf").path,
                message = "ready"
            )

            val reloaded = DownloadStateStore(storage)
            val restored = reloaded.find(ModelPackId.LITE, "lite-source")

            assertNotNull(restored)
            assertEquals(ModelDownloadStatus.SUCCESS, restored!!.status)
            assertEquals(12L, restored.bytesDownloaded)
            assertEquals("ready", restored.message)
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
