package com.nova.luna.executor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.ActionResultStatus
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionExecutorCommunicationTest {

    @Test
    fun `communication cancel is reported as a successful cancellation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = ActionExecutor(context)

        val draftIntent = CommandIntent(
            rawText = "draft reply to Alex on WhatsApp saying I will call back later",
            intentType = IntentType.COMMUNICATION,
            actionType = ActionType.COMMUNICATION
        )

        val draftResult = executor.handleCommunicationText(draftIntent.rawText, draftIntent)
        assertEquals(ActionResultStatus.NEEDS_CONFIRMATION, draftResult.status)
        assertTrue(draftResult.success)

        val cancelIntent = CommandIntent(
            rawText = "cancel",
            intentType = IntentType.COMMUNICATION,
            actionType = ActionType.COMMUNICATION
        )

        val cancelResult = executor.handleCommunicationText(cancelIntent.rawText, cancelIntent)
        assertEquals(IntentType.COMMUNICATION, cancelResult.intentType)
        assertEquals(ActionType.COMMUNICATION, cancelResult.actionType)
        assertEquals(ActionResultStatus.SUCCESS, cancelResult.status)
        assertTrue(cancelResult.success)
        assertTrue(cancelResult.message.contains("cancelled", ignoreCase = true))
        assertEquals("CANCELLED", cancelResult.entities["communicationStatus"])
    }
}
