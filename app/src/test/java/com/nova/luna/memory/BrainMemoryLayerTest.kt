package com.nova.luna.memory

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.screen.ScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainMemoryLayerTest {
    @Test
    fun `redacts sensitive screen text before storing it in memory`() {
        val redactor = MemoryRedactor()
        val snapshot = redactor.redactScreenState(
            ScreenState(
                packageName = "com.example.bank",
                appName = "Bank",
                className = "android.widget.FrameLayout",
                timestampMillis = 42L,
                isAccessibilityReady = true,
                visibleText = listOf(
                    "Enter OTP 123456",
                    "Email user@example.com",
                    "+91 9876543210"
                ),
                contentDescriptions = listOf("Card PIN"),
                summarizedState = "OTP screen with password prompt and card pin field.",
                confidence = 0.9f,
                rawNodeCount = 4,
                truncated = false
            )
        )

        assertEquals("[REDACTED]", snapshot.summary)
        assertTrue(snapshot.redactionCount >= 4)
        assertTrue(snapshot.visibleOptions.isNotEmpty())
        assertTrue(snapshot.visibleOptions.all { it == "[REDACTED]" })
    }

    @Test
    fun `session manager records sessions confirmations and preferences`() {
        val store = InMemoryBrainMemoryStore()
        val manager = BrainSessionManager(store)
        manager.setPreferences(
            LocalUserPreferences(
                preferredLanguage = "hi",
                preferredVoiceResponseStyle = "warm",
                privateMode = false
            )
        )
        manager.startSession(
            sessionType = BrainSessionType.GROCERY,
            sourceCommand = "buy milk",
            normalizedGoal = "buy milk"
        )

        val brainAction = groceryBrainAction()
        val confirmation = manager.queueConfirmation(
            sessionType = BrainSessionType.GROCERY,
            actionSummary = "Add milk to the cart",
            type = PendingConfirmationType.PLACE_ORDER,
            rawText = "buy milk",
            brainAction = brainAction,
            sessionId = "session-grocery"
        )

        val snapshot = manager.snapshot()

        assertEquals(BrainSessionType.GROCERY, snapshot.activeSessionType())
        assertEquals(1, snapshot.activeSessionCount)
        assertEquals(1, snapshot.activePendingConfirmationCount)
        assertNotNull(snapshot.activePendingConfirmation)
        assertEquals(BrainSessionType.GROCERY, confirmation.sessionType)
        assertEquals("hi", snapshot.preferences.preferredLanguage)
        assertFalse(snapshot.activePendingConfirmation?.sanitizedMetadata?.get("rawText").isNullOrBlank())
    }

    @Test
    fun `confirmation resolver matches yes and no responses`() {
        val pending = pendingConfirmation()
        val resolver = ConfirmationResolver(clock = { System.currentTimeMillis() })

        val confirmed = resolver.resolve("yes", pending)
        val denied = resolver.resolve("no", pending)

        assertNotNull(confirmed)
        assertTrue(confirmed?.confirmed == true)
        assertNotNull(denied)
        assertTrue(denied?.denied == true)
    }

    @Test
    fun `follow up resolver keeps active grocery and music sessions on their own paths`() {
        val grocerySession = activeSession(BrainSessionType.GROCERY)
        val musicSession = activeSession(BrainSessionType.MUSIC)
        val resolver = FollowUpResolver()

        val groceryResolution = resolver.resolve(
            rawText = "cheapest",
            snapshot = BrainMemorySnapshot(
                sessions = mapOf(BrainSessionType.GROCERY to grocerySession)
            )
        )
        val musicResolution = resolver.resolve(
            rawText = "continue",
            snapshot = BrainMemorySnapshot(
                sessions = mapOf(BrainSessionType.MUSIC to musicSession)
            )
        )

        assertEquals(FollowUpKind.CHEAPEST, groceryResolution?.kind)
        assertEquals(BrainSessionType.GROCERY, groceryResolution?.sessionType)
        assertEquals(FollowUpKind.CONTINUE, musicResolution?.kind)
        assertEquals(BrainSessionType.MUSIC, musicResolution?.sessionType)
    }

    private fun groceryBrainAction(): BrainAction {
        return BrainAction(
            intent = "grocery_booking",
            reply = "Add milk to the cart.",
            actionType = BrainActionType.READ_ONLY,
            riskLevel = BrainRiskLevel.SAFE,
            requiresConfirmation = false,
            finalActionAllowed = false,
            params = mapOf(
                "rawText" to "buy milk",
                "selectedItem" to "milk"
            ),
            nextQuestion = "Add milk to the cart?"
        )
    }

    private fun pendingConfirmation(): PendingConfirmation {
        return PendingConfirmation(
            confirmationId = "confirmation-grocery",
            type = PendingConfirmationType.PLACE_ORDER,
            sessionId = "session-grocery",
            sessionType = BrainSessionType.GROCERY,
            createdAtMillis = 1_000L,
            expiresAtMillis = System.currentTimeMillis() + 60_000L,
            userFacingSummary = "Add milk to the cart",
            actionSummary = "Add milk to the cart",
            riskLevel = BrainRiskLevel.SAFE,
            brainAction = groceryBrainAction(),
            sanitizedMetadata = mapOf("rawText" to "buy milk"),
            confirmationPhraseExpected = "yes",
            denialPhraseExpected = "no"
        )
    }

    private fun activeSession(sessionType: BrainSessionType): BrainSession {
        return BrainSession(
            sessionId = "$sessionType-session",
            sessionType = sessionType,
            status = BrainSessionStatus.ACTIVE,
            startedAtMillis = 1_000L,
            updatedAtMillis = 1_000L,
            sourceCommand = "start $sessionType",
            normalizedGoal = "continue $sessionType",
            activeDomain = sessionType
        )
    }
}
