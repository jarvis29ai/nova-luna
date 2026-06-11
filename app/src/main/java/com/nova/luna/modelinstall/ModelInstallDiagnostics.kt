package com.nova.luna.modelinstall

/**
 * PRODUCTION-STYLE MODEL INSTALL AND PATH MANAGEMENT SYSTEM - PHASE 21
 */
data class ModelInstallDiagnostics(
    val phase21_model_install_ready: Boolean,
    val phase21_model_id: String?,
    val phase21_model_path: String?,
    val phase21_model_exists: Boolean,
    val phase21_model_readable: Boolean,
    val phase21_model_size_bytes: Long?,
    val phase21_model_minimum_bytes: Long,
    val phase21_model_sha_expected: String?,
    val phase21_model_sha_actual: String?,
    val phase21_model_sha_verified: Boolean,
    val phase21_model_extension_allowed: Boolean,
    val phase21_model_reason: String,
    val phase21_download_available: Boolean = false,
    val phase21_download_implemented: Boolean = false,
    val phase21_error: String? = null
) {
    companion object {
        fun fromState(state: ModelInstallState, error: String? = null): ModelInstallDiagnostics {
            return ModelInstallDiagnostics(
                phase21_model_install_ready = state.ready,
                phase21_model_id = state.modelId,
                phase21_model_path = state.resolvedPath,
                phase21_model_exists = state.exists,
                phase21_model_readable = state.readable,
                phase21_model_size_bytes = state.sizeBytes,
                phase21_model_minimum_bytes = state.minimumBytes,
                phase21_model_sha_expected = state.sha256Expected,
                phase21_model_sha_actual = state.sha256Actual,
                phase21_model_sha_verified = state.sha256Verified,
                phase21_model_extension_allowed = state.extensionAllowed,
                phase21_model_reason = state.reason,
                phase21_download_available = false,
                phase21_download_implemented = false,
                phase21_error = error
            )
        }
        
        fun notImplemented(reason: String = ModelInstallReason.DOWNLOAD_NOT_IMPLEMENTED_PHASE_21): ModelInstallDiagnostics {
            return ModelInstallDiagnostics(
                phase21_model_install_ready = false,
                phase21_model_id = null,
                phase21_model_path = null,
                phase21_model_exists = false,
                phase21_model_readable = false,
                phase21_model_size_bytes = null,
                phase21_model_minimum_bytes = 0,
                phase21_model_sha_expected = null,
                phase21_model_sha_actual = null,
                phase21_model_sha_verified = false,
                phase21_model_extension_allowed = false,
                phase21_model_reason = reason,
                phase21_download_available = false,
                phase21_download_implemented = false
            )
        }
    }
}
