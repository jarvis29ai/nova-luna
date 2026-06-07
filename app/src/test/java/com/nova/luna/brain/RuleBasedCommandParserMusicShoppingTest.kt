package com.nova.luna.brain

import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleBasedCommandParserMusicShoppingTest {
    private val parser = RuleBasedCommandParser()

    @Test
    fun `plain music play requests route to music actions`() {
        val result = parser.parse("play Tum Hi Ho")

        assertEquals(IntentType.CONTROL, result.intentType)
        assertEquals(ActionType.MUSIC, result.actionType)
    }

    @Test
    fun `music controls without video context stay in the music branch`() {
        val result = parser.parse("pause music")

        assertEquals(IntentType.CONTROL, result.intentType)
        assertEquals(ActionType.MUSIC, result.actionType)
    }

    @Test
    fun `media app searches stay in the media branch`() {
        val result = parser.parse("Search MrBeast latest video on YouTube")

        assertEquals(IntentType.MEDIA_CONTROL, result.intentType)
        assertEquals(ActionType.MEDIA_CONTROL, result.actionType)
    }

    @Test
    fun `shopping buy requests stay in the shopping branch`() {
        val result = parser.parse("buy a phone from Amazon")

        assertEquals(IntentType.SHOPPING, result.intentType)
        assertEquals(ActionType.SHOPPING, result.actionType)
    }
}
