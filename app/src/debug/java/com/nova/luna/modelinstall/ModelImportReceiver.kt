package com.nova.luna.modelinstall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import com.nova.luna.model.BrainModelRole
import java.io.File

class ModelImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_IMPORT_MODEL_PACK) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        Thread {
            try {
                val storage = PrivateAppModelStorage.from(appContext)
                val importer = ModelPackImportManager(storage)
                val sourceDir = resolveSourceDir(appContext, intent)
                val targetPackId = resolvePackId(intent)

                val results = if (targetPackId != null) {
                    listOf(importer.importPack(targetPackId, sourceDir))
                } else {
                    importer.importAvailablePacks(sourceDir)
                }

                results.forEach { result ->
                    logResult(result)
                }

                writeReport(appContext, results)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Model import failed: ${throwable.message.orEmpty()}", throwable)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun resolvePackId(intent: Intent): ModelPackId? {
        intent.getStringExtra(EXTRA_PACK_ID)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { packIdValue ->
                ModelPackId.fromWireValue(packIdValue)?.let { return it }
            }

        intent.getStringExtra(EXTRA_ROLE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { roleValue ->
                BrainModelRole.fromWireValue(roleValue)?.toModelPackIdOrNull()?.let { return it }
            }

        return null
    }

    private fun resolveSourceDir(context: Context, intent: Intent): File {
        intent.getStringExtra(EXTRA_SOURCE_DIR)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { explicitPath ->
                return File(explicitPath)
            }

        val externalDownloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DEFAULT_SOURCE_DIR_NAME
        )
        if (externalDownloadDir.exists()) {
            return externalDownloadDir
        }

        val appSpecificExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (appSpecificExternal != null) {
            return File(appSpecificExternal, DEFAULT_SOURCE_DIR_NAME)
        }

        return File(context.filesDir, DEFAULT_SOURCE_DIR_NAME)
    }

    private fun logResult(result: ModelImportResult) {
        if (result.ready) {
            Log.i(TAG, "Model import succeeded for ${result.displayName}: ${result.message}")
        } else {
            Log.w(TAG, "Model import did not complete for ${result.displayName}: ${result.message}")
        }

        if (result.sourcePaths.isNotEmpty()) {
            Log.d(TAG, "sourcePaths=${result.sourcePaths.joinToString()}")
        }
        if (result.importedFileKeys.isNotEmpty()) {
            Log.d(TAG, "importedFileKeys=${result.importedFileKeys.joinToString()}")
        }
        if (result.warnings.isNotEmpty()) {
            Log.w(TAG, "warnings=${result.warnings.joinToString(" | ")}")
        }
        Log.i(
            TAG,
            "installStatus=${result.installStatus.runtimeStatus.name}, ready=${result.installStatus.ready}, path=${result.installStatus.runtimeState.modelRootPath.orEmpty()}"
        )
    }

    private fun writeReport(context: Context, results: List<ModelImportResult>) {
        val reportFile = File(context.cacheDir, "model-import-results.txt")
        reportFile.writeText(
            results.joinToString(separator = System.lineSeparator()) { result ->
                buildString {
                    append(result.displayName)
                    append(" | ready=")
                    append(result.ready)
                    append(" | status=")
                    append(result.installStatus.runtimeStatus.name)
                    append(" | message=")
                    append(result.message)
                }
            }
        )
    }

    companion object {
        const val ACTION_IMPORT_MODEL_PACK = "com.nova.luna.debug.ACTION_IMPORT_MODEL_PACK"
        const val EXTRA_PACK_ID = "com.nova.luna.debug.extra.PACK_ID"
        const val EXTRA_ROLE = "com.nova.luna.debug.extra.ROLE"
        const val EXTRA_SOURCE_DIR = "com.nova.luna.debug.extra.SOURCE_DIR"

        private const val TAG = "NovaLunaModelImport"
        private const val DEFAULT_SOURCE_DIR_NAME = "nova-luna-model-import"
    }
}
