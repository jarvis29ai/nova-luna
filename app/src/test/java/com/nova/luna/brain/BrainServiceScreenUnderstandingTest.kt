package com.nova.luna.brain

import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.screen.ScreenElementType
import com.nova.luna.screen.ScreenNode
import com.nova.luna.screen.ScreenState
import com.nova.luna.screen.ScreenStateReader
import com.nova.luna.screen.ScreenRiskSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServiceScreenUnderstandingTest {
    @Test
    fun `screen queries consume the captured screen state`() {
        val screenState = ScreenState(
            packageName = "com.example.mail",
            appName = "Example Mail",
            className = "MainActivity",
            timestampMillis = 123L,
            isAccessibilityReady = true,
            visibleText = listOf("Inbox", "Compose", "Search"),
            contentDescriptions = listOf("Compose button"),
            clickableElements = listOf(
                ScreenNode(
                    text = "Compose",
                    contentDescription = "Compose button",
                    className = "android.widget.Button",
                    isClickable = true,
                    semanticRole = ScreenElementType.BUTTON
                )
            ),
            editableFields = listOf(
                ScreenNode(
                    text = "Search",
                    contentDescription = "Search mail",
                    className = "android.widget.EditText",
                    isEditable = true,
                    semanticRole = ScreenElementType.SEARCH_FIELD
                )
            ),
            scrollableElements = emptyList(),
            selectedElements = emptyList(),
            focusedElement = null,
            enabledElements = emptyList(),
            disabledElements = emptyList(),
            possibleButtons = emptyList(),
            possibleSearchFields = emptyList(),
            possibleListsOrCards = emptyList(),
            errorMessages = emptyList(),
            loadingSignals = emptyList(),
            permissionSignals = emptyList(),
            loginSignals = emptyList(),
            paymentSignals = emptyList(),
            otpSignals = emptyList(),
            passwordSignals = emptyList(),
            captchaSignals = emptyList(),
            biometricSignals = emptyList(),
            riskSignals = listOf(ScreenRiskSignal.LOADING),
            summarizedState = "Example Mail / com.example.mail with 3 visible text items. 1 tappable items. 1 editable fields. risk: loading",
            confidence = 0.88f,
            rawNodeCount = 4,
            truncated = false,
            nodes = screenStateNodes()
        )

        val service = BrainService(
            screenStateReader = FakeScreenStateReader(screenState)
        )

        val action = service.process("what's on my screen?")
        val diagnostics = service.diagnose("what's on my screen?")

        assertEquals("screen_understanding", action.intent)
        assertEquals(BrainActionType.READ_ONLY, action.actionType)
        assertEquals(BrainRiskLevel.SAFE, action.riskLevel)
        assertFalse(action.finalActionAllowed)
        assertTrue(action.reply.contains("Example Mail"))
        assertEquals("com.example.mail", action.params["screenPackageName"])
        assertEquals("Example Mail", action.params["screenAppName"])
        assertEquals("summary", action.params["screenQueryMode"])
        assertEquals(com.nova.luna.model.BrainModelRole.SCREEN_UNDERSTANDING, diagnostics.selectedRole)
        assertTrue(diagnostics.finalBrainAction.reply.contains("Example Mail"))
    }

    @Test
    fun `screen queries fall back safely when accessibility capture is unavailable`() {
        val service = BrainService(
            screenStateReader = FakeScreenStateReader(null)
        )

        val action = service.process("what's on my screen?")

        assertEquals("screen_understanding", action.intent)
        assertEquals(BrainActionType.READ_ONLY, action.actionType)
        assertTrue(action.reply.contains("Accessibility service is not ready"))
        assertFalse(action.finalActionAllowed)
    }

    private fun screenStateNodes(): List<ScreenNode> {
        return listOf(
            ScreenNode(
                text = "Inbox",
                contentDescription = "Inbox",
                className = "android.widget.TextView"
            )
        )
    }

    private class FakeScreenStateReader(
        private val state: ScreenState?
    ) : ScreenStateReader() {
        override fun captureScreenState(
            service: com.nova.luna.service.NovaAccessibilityService?,
            maxNodes: Int
        ): ScreenState? {
            return state
        }
    }
}
