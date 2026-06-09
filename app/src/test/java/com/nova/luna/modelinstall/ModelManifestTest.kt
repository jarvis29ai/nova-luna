package com.nova.luna.modelinstall

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelManifestTest {
    @Test
    fun roundTripsThroughJson() {
        val manifest = ModelManifest(
            packId = ModelPackId.CORE,
            version = "1.0.0",
            displayName = "Core",
            state = ModelInstallState.READY,
            installedAtEpochMs = 123456789L,
            files = listOf(
                ModelFileSpec(
                    fileName = "gemma-3n-q4.gguf",
                    relativePath = "core",
                    sha256 = "abc123",
                    byteCount = 42L
                )
            ),
            notes = listOf(" ready ", "  "),
            checksumSha256 = "deadbeef"
        )

        val restored = ModelManifest.fromJsonString(manifest.toJsonString())

        assertEquals(manifest.normalized(), restored)
    }
}
