package com.nova.luna.brain

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import com.nova.luna.service.NovaAccessibilityService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CommandBrainNotificationsTest {
    private lateinit var context: Context
    private lateinit var service: NovaAccessibilityService
    private lateinit var brain: CommandBrain

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val notificationManager = context.getSystemService(NotificationManager::class.java)!!
        shadowOf(notificationManager).setNotificationsEnabled(true)
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(context, NovaAccessibilityService::class.java).flattenToString()
        )

        service = Robolectric.buildService(NovaAccessibilityService::class.java)
            .create()
            .get()
        installServiceInstance()
        seedNotificationSummary()

        brain = CommandBrain(context)
    }

    @After
    fun tearDown() {
        service.onDestroy()
    }

    private fun installServiceInstance() {
        val field = NovaAccessibilityService::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, service)
    }

    private fun seedNotificationSummary() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
        event.text.add("Package delivery")
        event.packageName = "com.example.mail"
        service.onAccessibilityEvent(event)
    }

    @Test
    fun `open notifications aliases route through the same shade action`() {
        val phrases = listOf("open notifications", "show notifications")

        phrases.forEach { phrase ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected navigation intent for phrase: $phrase",
                IntentType.NAVIGATION,
                result.intentType
            )
            assertEquals(
                "Expected open notifications action for phrase: $phrase",
                ActionType.OPEN_NOTIFICATIONS,
                result.actionType
            )
            assertEquals("Opening notifications.", result.message)
            assertFalse(result.shouldStopListening)
        }

        assertEquals(
            listOf(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            ),
            shadowOf(service).globalActionsPerformed
        )
    }

    @Test
    fun `read notifications aliases route through the same reader action`() {
        val phrases = listOf("read notifications", "check notifications")

        phrases.forEach { phrase ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected read notifications intent for phrase: $phrase",
                IntentType.READ_NOTIFICATIONS,
                result.intentType
            )
            assertEquals(
                "Expected read notifications action for phrase: $phrase",
                ActionType.READ_NOTIFICATIONS,
                result.actionType
            )
            assertEquals(
                "Latest notification: Package delivery from com.example.mail",
                result.message
            )
            assertFalse(result.shouldStopListening)
        }
    }
}
