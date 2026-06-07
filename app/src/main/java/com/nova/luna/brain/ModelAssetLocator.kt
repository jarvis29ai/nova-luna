package com.nova.luna.brain

import java.io.File

class ModelAssetLocator(
    private val fileExists: (String) -> Boolean = { path -> path.isNotBlank() && File(path).exists() }
) {
    fun resolvePath(model: PhoneLocalLlmModelConfig): String {
        val assetPath = model.assetPath.trim()
        val fileName = model.quantizedFileName.trim()

        if (assetPath.isBlank()) {
            return fileName
        }

        val assetFile = File(assetPath)
        if (assetFile.isFile) {
            return assetFile.path
        }

        if (assetPath.endsWith(fileName, ignoreCase = true)) {
            return assetPath
        }

        return File(assetFile, fileName).path
    }

    fun exists(model: PhoneLocalLlmModelConfig): Boolean {
        val path = resolvePath(model)
        return fileExists(path)
    }

    fun describe(model: PhoneLocalLlmModelConfig): String {
        val path = resolvePath(model)
        return if (exists(model)) {
            "asset=${path}, exists=true"
        } else {
            "asset=${path}, exists=false"
        }
    }
}
