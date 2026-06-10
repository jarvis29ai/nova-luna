package com.nova.luna.modelinstall

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoBundledModelAssetsTest {
    @Test
    fun sourceTreeDoesNotContainBundledModelBinariesInAssetsOrRawResources() {
        val repoRoot = findRepoRoot()
        val resourceRoots = listOf(
            File(repoRoot, "app/src/main/assets"),
            File(repoRoot, "app/src/main/res/raw")
        )

        val bundledModelFiles = resourceRoots.flatMap { resourceRoot ->
            if (!resourceRoot.exists()) {
                emptyList()
            } else {
                resourceRoot.walkTopDown()
                    .filter { it.isFile && looksLikeModelBinary(it) }
                    .map { it.relativeTo(repoRoot).path }
                    .toList()
            }
        }

        assertTrue(
            "Bundled model binaries must stay out of APK resources: ${bundledModelFiles.joinToString()}",
            bundledModelFiles.isEmpty()
        )
    }

    private fun looksLikeModelBinary(file: File): Boolean {
        return listOf(
            ".gguf",
            ".litertlm",
            ".bin",
            ".safetensors",
            ".onnx",
            ".tflite",
            ".task"
        ).any { suffix -> file.name.endsWith(suffix, ignoreCase = true) }
    }

    private fun findRepoRoot(): File {
        val userDir = System.getProperty("user.dir") ?: "."
        var current = File(userDir).canonicalFile
        while (true) {
            if (
                File(current, "app/build.gradle").exists() ||
                File(current, "settings.gradle").exists() ||
                File(current, "settings.gradle.kts").exists()
            ) {
                return current
            }

            current = current.parentFile ?: return current
        }
    }
}
