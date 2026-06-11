package com.nova.luna.modelinstall

/**
 * PRODUCTION-STYLE MODEL INSTALL AND PATH MANAGEMENT SYSTEM - PHASE 21
 */

data class ModelInstallSpec(
    val modelId: String,
    val displayName: String,
    val role: String,
    val expectedFileName: String,
    val expectedSha256: String?,
    val minimumBytes: Long,
    val preferredInstallDirName: String,
    val allowedExtensions: List<String>
)

data class ModelInstallState(
    val modelId: String,
    val displayName: String,
    val role: String,
    val expectedFileName: String,
    val resolvedPath: String?,
    val exists: Boolean,
    val readable: Boolean,
    val sizeBytes: Long?,
    val minimumBytes: Long,
    val sha256Expected: String?,
    val sha256Actual: String?,
    val sha256Verified: Boolean,
    val extensionAllowed: Boolean,
    val ready: Boolean,
    val reason: String
)

object ModelInstallReason {
    const val MODEL_READY = "MODEL_READY"
    const val MODEL_READY_DEV_SHA_MISSING = "MODEL_READY_DEV_SHA_MISSING"
    const val MODEL_SHA_MISSING_ACCEPTED_DEBUG = "MODEL_SHA_MISSING_ACCEPTED_DEBUG"
    const val MODEL_FILE_MISSING = "MODEL_FILE_MISSING"
    const val MODEL_PATH_NOT_CONFIGURED = "MODEL_PATH_NOT_CONFIGURED"
    const val MODEL_FILE_UNREADABLE = "MODEL_FILE_UNREADABLE"
    const val MODEL_FILE_TOO_SMALL = "MODEL_FILE_TOO_SMALL"
    const val MODEL_EXTENSION_NOT_ALLOWED = "MODEL_EXTENSION_NOT_ALLOWED"
    const val MODEL_SHA_MISSING = "MODEL_SHA_MISSING"
    const val MODEL_SHA_MISMATCH = "MODEL_SHA_MISMATCH"
    const val MODEL_SHA_VERIFIED = "MODEL_SHA_VERIFIED"
    const val MODEL_REPAIR_SUCCESS = "MODEL_REPAIR_SUCCESS"
    const val MODEL_REPAIR_FAILED = "MODEL_REPAIR_FAILED"
    const val DOWNLOAD_NOT_IMPLEMENTED_PHASE_21 = "DOWNLOAD_NOT_IMPLEMENTED_PHASE_21"
    const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
}
