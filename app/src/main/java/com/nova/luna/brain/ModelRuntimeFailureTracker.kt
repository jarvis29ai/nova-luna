package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole

data class ModelRuntimeFailureSnapshot(
    val role: BrainModelRole,
    val failureCount: Int,
    val lastFailureReason: String?,
    val lastFailureAtEpochMs: Long,
    val suppressedUntilEpochMs: Long
) {
    val suppressed: Boolean
        get() = suppressedUntilEpochMs > System.currentTimeMillis()
}

class ModelRuntimeFailureTracker(
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class FailureRecord(
        val failureCount: Int,
        val lastFailureReason: String?,
        val lastFailureAtEpochMs: Long,
        val suppressedUntilEpochMs: Long
    )

    private val lock = Any()
    private val failures = mutableMapOf<BrainModelRole, FailureRecord>()

    fun recordSuccess(role: BrainModelRole) {
        synchronized(lock) {
            failures.remove(role)
        }
    }

    fun recordFailure(role: BrainModelRole, reason: String? = null) {
        synchronized(lock) {
            val now = clock()
            val existing = failures[role]
            val failureCount = (existing?.failureCount ?: 0) + 1
            val suppressionMultiplier = failureCount.coerceAtMost(3)
            val suppressedUntilEpochMs = now + (cooldownMillis * suppressionMultiplier)
            failures[role] = FailureRecord(
                failureCount = failureCount,
                lastFailureReason = reason?.trim()?.takeIf { it.isNotBlank() },
                lastFailureAtEpochMs = now,
                suppressedUntilEpochMs = suppressedUntilEpochMs
            )
        }
    }

    fun isSuppressed(role: BrainModelRole): Boolean {
        synchronized(lock) {
            val record = failures[role] ?: return false
            return record.suppressedUntilEpochMs > clock()
        }
    }

    fun snapshot(): List<ModelRuntimeFailureSnapshot> {
        synchronized(lock) {
            val now = clock()
            return failures.entries.map { (role, record) ->
                ModelRuntimeFailureSnapshot(
                    role = role,
                    failureCount = record.failureCount,
                    lastFailureReason = record.lastFailureReason,
                    lastFailureAtEpochMs = record.lastFailureAtEpochMs,
                    suppressedUntilEpochMs = if (record.suppressedUntilEpochMs > now) {
                        record.suppressedUntilEpochMs
                    } else {
                        0L
                    }
                )
            }.sortedBy { it.role.wireValue }
        }
    }

    fun clear() {
        synchronized(lock) {
            failures.clear()
        }
    }

    companion object {
        private const val DEFAULT_COOLDOWN_MILLIS = 2 * 60 * 1000L
    }
}
