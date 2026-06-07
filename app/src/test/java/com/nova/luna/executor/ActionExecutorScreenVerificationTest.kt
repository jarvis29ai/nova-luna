package com.nova.luna.executor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import com.nova.luna.screen.ScreenState
import com.nova.luna.service.NovaAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.lang.reflect.Field
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionExecutorScreenVerificationTest {
    private lateinit var context: Context
    private lateinit var service: NovaAccessibilityService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = Mockito.mock(NovaAccessibilityService::class.java)
        installServiceInstance(service)
    }

    @After
    fun tearDown() {
        installServiceInstance(null)
    }

    @Test
    fun `screen aware actions include verification metadata`() {
        val before = screenState(
            packageName = "com.example.launcher",
            className = "LauncherActivity",
            summary = "Launcher screen"
        )
        val after = screenState(
            packageName = "com.example.settings",
            className = "SettingsActivity",
            summary = "Settings screen"
        )

        Mockito.`when`(service.captureScreenState()).thenReturn(before, after)
        Mockito.`when`(service.goHome()).thenReturn(true)

        val executor = ActionExecutor(context)
        val result = executor.execute(
            CommandIntent(
                rawText = "go home",
                intentType = IntentType.NAVIGATION,
                actionType = ActionType.GO_HOME
            )
        )

        assertTrue(result.entities["screenVerificationApplicable"] == "true")
        assertEquals("true", result.entities["screenVerificationVerified"])
        assertEquals("true", result.entities["screenVerificationChanged"])
        assertEquals("changed", result.entities["screenVerificationStatus"])
    }

    private fun screenState(
        packageName: String,
        className: String,
        summary: String
    ): ScreenState {
        return ScreenState(
            packageName = packageName,
            appName = packageName.substringAfterLast('.'),
            className = className,
            timestampMillis = System.currentTimeMillis(),
            isAccessibilityReady = true,
            visibleText = listOf(summary),
            contentDescriptions = emptyList(),
            clickableElements = emptyList(),
            editableFields = emptyList(),
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
            riskSignals = emptyList(),
            summarizedState = summary,
            confidence = 0.9f,
            rawNodeCount = 1,
            truncated = false,
            nodes = emptyList()
        )
    }

    private fun installServiceInstance(instance: NovaAccessibilityService?) {
        val field: Field = NovaAccessibilityService::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, instance)
    }
}
