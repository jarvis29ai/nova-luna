package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase23CommandUnderstandingTest {

    private lateinit var service: CommandUnderstandingService

    @Before
    fun setup() {
        service = CommandUnderstandingService()
    }

    // 1. Low-risk commands
    @Test
    fun `open camera returns low risk`() {
        val action = service.understand("open camera")
        assertEquals(BrainActionType.OPEN_CAMERA, action.actionType)
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
        assertFalse(action.requiresConfirmation)
    }

    @Test
    fun `open settings returns low risk`() {
        val action = service.understand("open settings")
        assertEquals(BrainActionType.OPEN_SETTINGS, action.actionType)
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
        assertFalse(action.requiresConfirmation)
    }

    @Test
    fun `turn on flashlight returns low risk`() {
        val action = service.understand("turn on flashlight")
        assertEquals(BrainActionType.TOGGLE_FLASHLIGHT, action.actionType)
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
    }

    @Test
    fun `search google returns low risk`() {
        val action = service.understand("search Google for best phones under 20000")
        assertEquals(BrainActionType.SEARCH_WEB, action.actionType)
        assertEquals("best phones under 20000", action.params["query"])
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
    }

    @Test
    fun `search youtube returns low risk`() {
        val action = service.understand("search YouTube for lo-fi music")
        assertEquals(BrainActionType.SEARCH_YOUTUBE, action.actionType)
        assertEquals("lo fi music", action.params["query"])
        assertEquals("youtube", action.params["provider"])
    }

    @Test
    fun `play music returns low risk`() {
        val action = service.understand("play Arijit Singh songs")
        assertEquals(BrainActionType.PLAY_MEDIA, action.actionType)
        assertEquals("arijit singh songs", action.params["query"])
    }

    @Test
    fun `open app returns low risk`() {
        val action = service.understand("open WhatsApp")
        assertEquals(BrainActionType.OPEN_APP, action.actionType)
        assertEquals("whatsapp", action.params["appName"]?.lowercase())
    }

    // 2. Medium-risk commands (Drafts/Searches)
    @Test
    fun `call contact returns medium risk draft`() {
        val action = service.understand("call mom")
        assertEquals(BrainActionType.MAKE_CALL_DRAFT, action.actionType)
        assertEquals(BrainRiskLevel.MEDIUM, action.riskLevel)
        assertTrue(action.requiresConfirmation)
        assertEquals("mom", action.params["contact"])
    }

    @Test
    fun `message contact returns medium risk draft`() {
        val action = service.understand("message Rahul saying I am late")
        assertEquals(BrainActionType.SEND_MESSAGE_DRAFT, action.actionType)
        assertEquals(BrainRiskLevel.MEDIUM, action.riskLevel)
        assertTrue(action.requiresConfirmation)
        assertEquals("rahul", action.params["contact"])
        assertEquals("i am late", action.params["message"])
    }

    @Test
    fun `send message to contact returns medium risk draft`() {
        val action = service.understand("send message to Priya: reach in 10 minutes")
        assertEquals(BrainActionType.SEND_MESSAGE_DRAFT, action.actionType)
        assertEquals("priya", action.params["contact"])
        assertEquals("reach in 10 minutes", action.params["message"])
    }

    @Test
    fun `check cab fare returns medium risk`() {
        val action = service.understand("check cab fare to MP Nagar")
        assertEquals(BrainActionType.CAB_SEARCH, action.actionType)
        assertEquals(BrainRiskLevel.MEDIUM, action.riskLevel)
        assertEquals("mp nagar", action.params["destination"]?.lowercase())
    }

    @Test
    fun `search food returns medium risk`() {
        val action = service.understand("search biryani on Swiggy")
        assertEquals(BrainActionType.FOOD_SEARCH, action.actionType)
        assertEquals(BrainRiskLevel.MEDIUM, action.riskLevel)
    }

    @Test
    fun `grocery search returns medium risk`() {
        val action = service.understand("add milk to grocery search")
        assertEquals(BrainActionType.GROCERY_SEARCH, action.actionType)
        assertEquals(BrainRiskLevel.MEDIUM, action.riskLevel)
    }

    // 3. Human-only / Unsafe commands
    @Test
    fun `pay returns human only`() {
        val action = service.understand("pay 500 to Rohan")
        assertEquals(BrainActionType.PAYMENT_REQUEST, action.actionType)
        assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
        assertTrue(action.requiresConfirmation)
    }

    @Test
    fun `send upi payment returns human only`() {
        val action = service.understand("send UPI payment")
        assertEquals(BrainActionType.PAYMENT_REQUEST, action.actionType)
        assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
    }

    @Test
    fun `read otp returns human only`() {
        val action = service.understand("read my OTP")
        assertEquals(BrainActionType.OTP_REQUEST, action.actionType)
        assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
    }

    @Test
    fun `enter otp returns human only`() {
        val action = service.understand("enter OTP 123456")
        assertEquals(BrainActionType.OTP_REQUEST, action.actionType)
        assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
    }

    @Test
    fun `login returns human only`() {
        val action = service.understand("login to my bank")
        assertEquals(BrainActionType.LOGIN_REQUEST, action.actionType)
        assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
    }

    @Test
    fun `solve captcha returns human only`() {
        val action = service.understand("solve captcha")
        assertEquals(BrainActionType.CAPTCHA_REQUEST, action.actionType)
        assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
    }

    @Test
    fun `delete all returns high risk`() {
        val action = service.understand("delete all photos")
        assertEquals(BrainActionType.DESTRUCTIVE_REQUEST, action.actionType)
        assertEquals(BrainRiskLevel.HIGH, action.riskLevel)
    }

    // 4. Ambiguous commands
    @Test
    fun `empty command returns clarification`() {
        val action = service.understand("")
        assertEquals(BrainActionType.ASK_CLARIFICATION, action.actionType)
        assertEquals(BrainRiskLevel.UNKNOWN, action.riskLevel)
    }

    @Test
    fun `gibberish returns clarification`() {
        val action = service.understand("asdfghjkl")
        assertEquals(BrainActionType.ASK_CLARIFICATION, action.actionType)
        assertEquals(BrainRiskLevel.UNKNOWN, action.riskLevel)
    }

    @Test
    fun `ambiguous pronoun returns clarification`() {
        val action = service.understand("do it")
        assertEquals(BrainActionType.ASK_CLARIFICATION, action.actionType)
    }

    // 5. Hindi/Hinglish
    @Test
    fun `hindi camera kholo works`() {
        val action = service.understand("camera kholo")
        assertEquals(BrainActionType.OPEN_CAMERA, action.actionType)
        assertEquals("hinglish", action.language)
    }

    @Test
    fun `devanagari camera kholo works`() {
        val action = service.understand("कैमरा खोलो")
        assertEquals(BrainActionType.OPEN_CAMERA, action.actionType)
        assertEquals("hi", action.language)
    }

    @Test
    fun `hinglish torch on karo works`() {
        val action = service.understand("torch on karo")
        assertEquals(BrainActionType.TOGGLE_FLASHLIGHT, action.actionType)
        assertEquals("hinglish", action.language)
    }

    @Test
    fun `hindi message bhejo works`() {
        val action = service.understand("Rahul ko message bhejo ki main late hoon")
        assertEquals(BrainActionType.SEND_MESSAGE_DRAFT, action.actionType)
        assertEquals("rahul", action.params["contact"])
        assertEquals("hinglish", action.language)
    }

    @Test
    fun `hindi payment works`() {
        val action = service.understand("Rohan ko 500 rupay pay karo")
        assertEquals(BrainActionType.PAYMENT_REQUEST, action.actionType)
        assertEquals(BrainRiskLevel.HUMAN_ONLY, action.riskLevel)
    }

    // 6. Model JSON parser
    @Test
    fun `model json parser extracts valid json`() {
        val modelOutput = """
            Here is the action:
            {
              "intent": "open_camera",
              "actionType": "OPEN_CAMERA",
              "riskLevel": "LOW",
              "requiresConfirmation": false,
              "params": {},
              "confidence": 0.95,
              "language": "en",
              "assistantReply": "Opening camera...",
              "reason": "Request to open camera"
            }
        """.trimIndent()
        val action = service.understand("open camera", modelOutput)
        assertEquals(BrainActionSource.MODEL_WITH_RULE_REPAIR, action.source)
        assertEquals(BrainActionType.OPEN_CAMERA, action.actionType)
        assertEquals(0.95, action.confidence, 0.001)
    }

    @Test
    fun `model json parser falls back on invalid json`() {
        val modelOutput = "Invalid output"
        val action = service.understand("open camera", modelOutput)
        assertEquals(BrainActionSource.RULE_FALLBACK, action.source)
        assertEquals(BrainActionType.OPEN_CAMERA, action.actionType)
    }

    @Test
    fun `model json parser clamps confidence`() {
        val modelOutput = """
            {
              "intent": "open_camera",
              "actionType": "OPEN_CAMERA",
              "riskLevel": "LOW",
              "requiresConfirmation": false,
              "confidence": 1.5,
              "assistantReply": "Opening...",
              "reason": "Test"
            }
        """.trimIndent()
        val action = service.understand("open camera", modelOutput)
        assertEquals(1.0, action.confidence, 0.001)
    }

    // 7. Phase boundary
    @Test
    fun `no execution performed and reply is informational`() {
        val action = service.understand("open camera")
        // Check that it doesn't say "Done" or "I opened"
        assertFalse(action.assistantReply.contains("Done", ignoreCase = true))
        assertFalse(action.assistantReply.contains("I opened", ignoreCase = true))
        assertTrue(action.assistantReply.contains("I understood") || action.assistantReply.contains("Opening"))
    }
}
