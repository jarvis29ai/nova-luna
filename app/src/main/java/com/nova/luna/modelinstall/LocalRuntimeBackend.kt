package com.nova.luna.modelinstall

import java.io.File

interface LocalRuntimeBackend {
    fun load(modelFiles: List<File>): Boolean
}

object NoOpLocalRuntimeBackend : LocalRuntimeBackend {
    override fun load(modelFiles: List<File>): Boolean {
        return modelFiles.isNotEmpty() && modelFiles.all { it.exists() && it.isFile }
    }
}
