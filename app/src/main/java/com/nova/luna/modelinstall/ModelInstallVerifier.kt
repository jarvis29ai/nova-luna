package com.nova.luna.modelinstall

import java.io.File

/**
 * PRODUCTION-STYLE MODEL INSTALL AND PATH MANAGEMENT SYSTEM - PHASE 21
 */
class ModelInstallVerifier(
    private val shaVerifier: Sha256ModelVerifier = Sha256ModelVerifier()
) {
    fun verify(spec: ModelInstallSpec, path: String?): ModelInstallState {
        val modelId = spec.modelId
        val displayName = spec.displayName
        val role = spec.role
        val expectedFileName = spec.expectedFileName
        val minBytes = spec.minimumBytes
        val expectedSha = spec.expectedSha256

        if (path == null) {
            return errorState(spec, null, ModelInstallReason.MODEL_FILE_MISSING)
        }

        val file = File(path)
        if (!file.exists()) {
            return errorState(spec, path, ModelInstallReason.MODEL_FILE_MISSING)
        }

        if (!file.isFile || !file.canRead()) {
            return errorState(spec, path, ModelInstallReason.MODEL_FILE_UNREADABLE)
        }

        val size = file.length()
        if (size < minBytes) {
            return errorState(spec, path, ModelInstallReason.MODEL_FILE_TOO_SMALL, size)
        }

        val extension = "." + file.extension.lowercase()
        val extensionAllowed = spec.allowedExtensions.any { it.lowercase() == extension }
        if (!extensionAllowed) {
            return errorState(spec, path, ModelInstallReason.MODEL_EXTENSION_NOT_ALLOWED, size)
        }

        var shaActual: String? = null
        var shaVerified = false
        var reason = ModelInstallReason.MODEL_READY

        if (expectedSha != null) {
            shaActual = shaVerifier.digestHex(file)
            shaVerified = shaActual.equals(expectedSha, ignoreCase = true)
            if (!shaVerified) {
                reason = ModelInstallReason.MODEL_SHA_MISMATCH
            } else {
                reason = ModelInstallReason.MODEL_SHA_VERIFIED
            }
        } else {
            // Debug/dev mode policy: allow size + extension if SHA is missing
            reason = ModelInstallReason.MODEL_READY_DEV_SHA_MISSING
        }

        val ready = reason == ModelInstallReason.MODEL_SHA_VERIFIED || 
                    reason == ModelInstallReason.MODEL_READY_DEV_SHA_MISSING ||
                    reason == ModelInstallReason.MODEL_READY

        return ModelInstallState(
            modelId = modelId,
            displayName = displayName,
            role = role,
            expectedFileName = expectedFileName,
            resolvedPath = path,
            exists = true,
            readable = true,
            sizeBytes = size,
            minimumBytes = minBytes,
            sha256Expected = expectedSha,
            sha256Actual = shaActual,
            sha256Verified = shaVerified,
            extensionAllowed = true,
            ready = ready,
            reason = reason
        )
    }

    private fun errorState(
        spec: ModelInstallSpec,
        path: String?,
        reason: String,
        size: Long? = null
    ): ModelInstallState {
        return ModelInstallState(
            modelId = spec.modelId,
            displayName = spec.displayName,
            role = spec.role,
            expectedFileName = spec.expectedFileName,
            resolvedPath = path,
            exists = path?.let { File(it).exists() } ?: false,
            readable = path?.let { File(it).canRead() } ?: false,
            sizeBytes = size,
            minimumBytes = spec.minimumBytes,
            sha256Expected = spec.expectedSha256,
            sha256Actual = null,
            sha256Verified = false,
            extensionAllowed = path?.let { p -> spec.allowedExtensions.any { it.lowercase() == "." + File(p).extension.lowercase() } } ?: false,
            ready = false,
            reason = reason
        )
    }
}
