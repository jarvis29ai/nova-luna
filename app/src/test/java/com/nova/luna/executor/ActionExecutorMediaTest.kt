package com.nova.luna.executor

import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionExecutorMediaTest {
    @Test
    fun `media control commands are routed through the media orchestrator`() {
        val executor = ActionExecutor(ApplicationProvider.getApplicationContext())

        val result = executor.execute(
            CommandIntent(
                rawText = "pause",
                intentType = IntentType.CONTROL,
                actionType = ActionType.MEDIA_CONTROL
            )
        )

        assertEquals(ActionType.MEDIA_CONTROL, result.actionType)
        assertTrue(result.success)
    }
}
