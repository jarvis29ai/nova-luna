package com.nova.luna.modelinstall

import android.content.Context
import java.io.File

class PrivateAppModelStorage private constructor(
    override val rootDir: File
) : ModelStorage {
    val downloadsRootDir: File = ensureDir(File(rootDir, "model_downloads"))
    val modelsRootDir: File = ensureDir(File(rootDir, "models"))
    val registryIndexFile: File = File(rootDir, "registry.json")

    override fun downloadsDir(packId: ModelPackId): File {
        return ensureDir(File(downloadsRootDir, packId.wireValue))
    }

    override fun modelsDir(packId: ModelPackId): File {
        return ensureDir(File(modelsRootDir, packId.wireValue))
    }

    override fun manifestFile(packId: ModelPackId): File {
        return File(modelsDir(packId), "manifest.json")
    }

    override fun stagedManifestFile(packId: ModelPackId): File {
        return File(downloadsDir(packId), "manifest.json")
    }

    fun packFile(packId: ModelPackId, fileName: String): File {
        return File(modelsDir(packId), fileName.trim())
    }

    fun packFile(packId: ModelPackId, relativePath: String, fileName: String): File {
        return File(modelsDir(packId), resolvePackRelativeFilePath(packId, relativePath, fileName))
    }

    fun stagedFile(packId: ModelPackId, fileName: String): File {
        return File(downloadsDir(packId), fileName.trim())
    }

    fun stagedFile(packId: ModelPackId, relativePath: String, fileName: String): File {
        return File(downloadsDir(packId), resolvePackRelativeFilePath(packId, relativePath, fileName))
    }

    fun describe(packId: ModelPackId): String {
        return buildString {
            append("downloads=")
            append(downloadsDir(packId).path)
            append(", models=")
            append(modelsDir(packId).path)
            append(", manifest=")
            append(manifestFile(packId).path)
        }
    }

    companion object {
        fun from(context: Context): PrivateAppModelStorage {
            return from(context.filesDir)
        }

        fun from(appFilesDir: File): PrivateAppModelStorage {
            val storageRoot = ensureDir(File(appFilesDir, "model_install"))
            return PrivateAppModelStorage(storageRoot)
        }
    }
}

private fun ensureDir(file: File): File {
    if (!file.exists()) {
        file.mkdirs()
    }
    return file
}

private fun resolvePackRelativeFilePath(
    packId: ModelPackId,
    relativePath: String,
    fileName: String
): String {
    val normalizedRelativePath = relativePath.trim().trim('/').let { path ->
        if (path.isBlank()) return fileName.trim()

        val segments = path.split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (segments.isEmpty()) {
            return fileName.trim()
        }

        val packSegment = packId.wireValue
        val storageSegments = if (segments.first().equals(packSegment, ignoreCase = true)) {
            segments.drop(1)
        } else {
            segments
        }

        if (storageSegments.isEmpty()) {
            return fileName.trim()
        }

        storageSegments.joinToString("/")
    }

    return if (normalizedRelativePath.isBlank()) {
        fileName.trim()
    } else {
        "$normalizedRelativePath/${fileName.trim()}"
    }
}
