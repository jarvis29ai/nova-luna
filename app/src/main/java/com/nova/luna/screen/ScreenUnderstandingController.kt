package com.nova.luna.screen

import android.util.Log
import com.nova.luna.model.BrainAction
import com.nova.luna.service.NovaAccessibilityService

class ScreenUnderstandingController(
    private val service: NovaAccessibilityService,
    private val reader: AccessibilityScreenReader,
    private val classifier: ScreenClassifier,
    private val planner: ScreenStepPlanner,
    private val recovery: ScreenRecoveryController
) {
    companion object {
        private const val TAG = "ScreenUnderstandingCtrl"
    }

    fun executeActionStep(action: BrainAction): ScreenStepResult {
        val snapshot = reader.readScreen() ?: return ScreenStepResult(false, ScreenStep(action = ScreenAction.NO_OP, reason = "Failed to capture screen"), "Failed to capture")
        val classifiedSnapshot = classifier.classify(snapshot)
        
        val plan = planner.planNextStep(action, classifiedSnapshot)
        
        if (plan.success) {
            return executeStep(plan.stepExecuted)
        }
        
        // Try recovery
        val strategy = recovery.recover(classifiedSnapshot, plan.stepExecuted, plan.error ?: "UNKNOWN")
        return applyRecovery(strategy, action)
    }

    private fun executeStep(step: ScreenStep): ScreenStepResult {
        return when (step.action) {
            ScreenAction.TYPE_TEXT -> {
                val text = step.inputText ?: ""
                val success = service.typeText(text)
                ScreenStepResult(success, step, if (success) "Typed text" else "Failed to type")
            }
            ScreenAction.CLICK -> {
                // Simplified, needs actual target element logic from accessibility service
                val success = service.clickByContentDescription(step.targetElement?.contentDescription ?: "")
                ScreenStepResult(success, step, if (success) "Clicked" else "Failed to click")
            }
            else -> ScreenStepResult(false, step, "Unsupported action ${step.action}")
        }
    }
    
    private fun applyRecovery(strategy: RecoveryStrategy, action: BrainAction): ScreenStepResult {
        Log.i(TAG, "Applying recovery: $strategy")
        // Minimal recovery implementation for now
        return ScreenStepResult(false, ScreenStep(action = ScreenAction.NO_OP, reason = "Recovery strategy: $strategy"), "Recovery required: $strategy")
    }
}
