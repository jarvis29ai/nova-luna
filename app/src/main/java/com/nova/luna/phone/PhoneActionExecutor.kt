package com.nova.luna.phone

import com.nova.luna.model.BrainAction

interface PhoneActionExecutor {
    fun execute(action: BrainAction): PhoneActionResult
}
