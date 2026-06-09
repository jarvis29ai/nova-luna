package com.nova.luna.modelinstall

import java.io.File

enum class LocalModelLoadStatus {
    NOT_READY,
    MISSING,
    CORRUPT,
    REFUSED,
    FAILED,
    READY
}

data class LocalModelLoadResult(
    val packId: ModelPackId,
    val status: LocalModelLoadStatus,
    val modelFiles: List<File> = emptyList(),
    val reason: String,
    val installStatus: ModelInstallStatusSnapshot? = null,
    val selection: ModelPackSelection? = null
) {
    val ready: Boolean
        get() = status == LocalModelLoadStatus.READY
}

interface LocalModelLoader {
    fun load(
        packId: ModelPackId,
        selection: ModelPackSelection? = null
    ): LocalModelLoadResult
}
