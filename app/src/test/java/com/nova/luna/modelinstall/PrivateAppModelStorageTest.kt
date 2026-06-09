package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.io.File

class PrivateAppModelStorageTest {
    @Test
    fun createsExpectedPrivateFolders() {
        val baseDir = Files.createTempDirectory("nova_luna_model_storage_test").toFile()
        val storage = PrivateAppModelStorage.from(baseDir)

        assertTrue(storage.rootDir.exists())
        assertEquals(
            FilePathNormalizer.normalize(File(baseDir, "model_install")),
            FilePathNormalizer.normalize(storage.rootDir)
        )
        assertEquals(
            FilePathNormalizer.normalize(File(storage.rootDir, "model_downloads/core")),
            FilePathNormalizer.normalize(storage.downloadsDir(ModelPackId.CORE))
        )
        assertEquals(
            FilePathNormalizer.normalize(File(storage.rootDir, "models/core")),
            FilePathNormalizer.normalize(storage.modelsDir(ModelPackId.CORE))
        )
        assertEquals(
            FilePathNormalizer.normalize(File(storage.rootDir, "models/core/manifest.json")),
            FilePathNormalizer.normalize(storage.manifestFile(ModelPackId.CORE))
        )
    }

    private object FilePathNormalizer {
        fun normalize(file: java.io.File): String {
            return file.canonicalFile.path.replace('\\', '/')
        }
    }
}
