package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleBasedCommandParserPhase1StabilityTest {
    private val parser = RuleBasedCommandParser()

    @Test
    fun `bare control words stay unknown until a real command is present`() {
        val phrases = listOf(
            "compare",
            "play",
            "pause",
            "cancel",
            "continue",
            "send it",
            "skip",
            "yes"
        )

        phrases.forEach { phrase ->
            val result = parser.parse(phrase)

            assertEquals("Expected unknown intent for phrase: $phrase", IntentType.UNKNOWN, result.intentType)
            assertEquals("Expected unknown action for phrase: $phrase", ActionType.UNKNOWN, result.actionType)
        }
    }

    @Test
    fun `open app commands stay distinct from media routing`() {
        val result = parser.parse("open YouTube")

        assertEquals(IntentType.OPEN_APP, result.intentType)
        assertEquals(ActionType.LAUNCH_APP, result.actionType)
        assertEquals("youtube", result.entities["appName"])
        assertEquals("youtube", result.entities["query"])
    }

    @Test
    fun `shopping phrases still route when they carry buying or comparison context`() {
        val buyResult = parser.parse("find best laptop under 60000")
        val compareResult = parser.parse("compare Amazon and Flipkart for headphones")

        assertEquals(IntentType.SHOPPING, buyResult.intentType)
        assertEquals(ActionType.SHOPPING, buyResult.actionType)
        assertEquals(IntentType.SHOPPING, compareResult.intentType)
        assertEquals(ActionType.SHOPPING, compareResult.actionType)
    }
}
