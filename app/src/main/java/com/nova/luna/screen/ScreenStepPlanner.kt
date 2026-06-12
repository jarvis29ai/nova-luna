package com.nova.luna.screen

import com.nova.luna.model.BrainAction

interface ScreenStepPlanner {
    fun planNextStep(action: BrainAction, snapshot: ScreenSnapshot): ScreenStepResult
}
