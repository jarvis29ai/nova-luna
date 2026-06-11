package com.nova.luna.brain

import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelRole
import com.nova.luna.modelinstall.ModelPackId
import com.nova.luna.modelinstall.PrivateAppModelStorage
import java.io.File

internal object DiagnosticRequestResolver {
    fun resolve(
        command: String?,
        request: String?,
        fallback: String = "ping"
    ): String {
        return command?.trim()?.takeIf { it.isNotBlank() }
            ?: request?.trim()?.takeIf { it.isNotBlank() }
            ?: fallback
    }
}

internal data class LiteNativeProbePreflight(
    val modelFile: File,
    val modelEnabled: Boolean,
    val modelExists: Boolean,
    val shouldRunNativeProbe: Boolean,
    val reason: String
)

internal object LiteNativeProbePlanner {
    fun plan(storage: PrivateAppModelStorage): LiteNativeProbePreflight {
        val entry = BrainModelCatalog.entryForRole(BrainModelRole.LITE_FALLBACK)
            ?: error("Lite fallback catalog entry is missing.")
        val modelFile = storage.packFile(ModelPackId.LITE, entry.storageRelativePath, entry.fileName)
        val modelEnabled = entry.sourceEnabled
        val modelExists = modelFile.exists() && modelFile.isFile
        val reason = when {
            !modelEnabled -> "Lite model source is disabled in the catalog."
            !modelExists -> "Lite model file is missing at ${modelFile.absolutePath}."
            else -> "Lite model file is available at ${modelFile.absolutePath}."
        }

        return LiteNativeProbePreflight(
            modelFile = modelFile,
            modelEnabled = modelEnabled,
            modelExists = modelExists,
            shouldRunNativeProbe = modelEnabled && modelExists,
            reason = reason
        )
    }
}
