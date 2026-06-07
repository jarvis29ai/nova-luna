package com.nova.luna.agent

class StuckDetector(
    private val maxRepeatedScreens: Int = 2
) {
    fun isStuck(history: List<TaskStep>, currentScreenSignature: String?): Boolean {
        if (history.isEmpty()) return false

        val recentSignatures = history
            .mapNotNull { it.screenSnapshotId ?: it.screenSummary }
            .takeLast(maxRepeatedScreens + 1)

        if (currentScreenSignature != null) {
            val repeatedCurrent = recentSignatures.filter { it == currentScreenSignature }
            if (repeatedCurrent.size >= maxRepeatedScreens + 1) {
                return true
            }
        }

        if (recentSignatures.size >= maxRepeatedScreens + 1 &&
            recentSignatures.distinct().size == 1
        ) {
            return true
        }

        val repeatedFailures = history
            .takeLast(maxRepeatedScreens + 1)
            .count { step ->
                step.status == TaskStepStatus.FAILED || step.status == TaskStepStatus.STOPPED
            }

        return repeatedFailures >= maxRepeatedScreens + 1
    }
}
