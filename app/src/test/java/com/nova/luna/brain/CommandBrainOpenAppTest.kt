package com.nova.luna.brain

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.executor.AppLauncher
import com.nova.luna.model.ActionType
import com.nova.luna.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommandBrainOpenAppTest {
    private lateinit var context: TestContext
    private lateinit var packageManager: PackageManager
    private lateinit var brain: CommandBrain

    @Before
    fun setUp() {
        packageManager = Mockito.mock(PackageManager::class.java)
        context = TestContext(
            baseContext = ApplicationProvider.getApplicationContext(),
            packageManager = packageManager
        )
        Mockito.`when`(packageManager.getLaunchIntentForPackage("com.whatsapp"))
            .thenReturn(Intent(Intent.ACTION_MAIN))

        brain = CommandBrain(context)
        seedInstalledApp("WhatsApp", "com.whatsapp")
    }

    @Test
    fun `open app aliases preserve app name and route through same launch path`() {
        val phrases = linkedMapOf(
            "open whatsapp" to "whatsapp",
            "launch whatsapp" to "whatsapp",
            "start whatsapp" to "whatsapp",
            "open app whatsapp" to "whatsapp",
            "Luna open WhatsApp" to "whatsapp"
        )

        phrases.forEach { (phrase, expectedAppName) ->
            val result = brain.process(phrase)

            assertTrue("Expected success for phrase: $phrase", result.success)
            assertEquals(
                "Expected open app intent for phrase: $phrase",
                IntentType.OPEN_APP,
                result.intentType
            )
            assertEquals(
                "Expected launch action for phrase: $phrase",
                ActionType.LAUNCH_APP,
                result.actionType
            )
            assertEquals("Opening WhatsApp.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedAppName, result.entities["appName"])
            assertEquals(expectedAppName, result.entities["query"])
            assertEquals("WhatsApp", result.entities["resolvedLabel"])
            assertEquals("com.whatsapp", result.entities["resolvedPackage"])
        }

        Mockito.verify(packageManager, Mockito.times(5))
            .getLaunchIntentForPackage("com.whatsapp")
        assertEquals(5, context.launchedIntents.size)
        assertEquals(Intent.ACTION_MAIN, context.launchedIntents.last().action)
    }

    private fun seedInstalledApp(label: String, packageName: String) {
        val entries = listOf(AppLauncher.AppEntry(label, packageName))
        setCachedAppsOnAppLauncher(getResolverAppLauncher(), entries)
        setCachedAppsOnAppLauncher(getExecutorAppLauncher(), entries)
    }

    private fun getResolverAppLauncher(): Any {
        val field = CommandBrain::class.java.getDeclaredField("appLauncher")
        field.isAccessible = true
        return field.get(brain)!!
    }

    private fun getExecutorAppLauncher(): Any {
        val routerField = CommandBrain::class.java.getDeclaredField("router")
        routerField.isAccessible = true
        val router = routerField.get(brain)

        val actionExecutorField = router.javaClass.getDeclaredField("actionExecutor")
        actionExecutorField.isAccessible = true
        val actionExecutor = actionExecutorField.get(router)

        val appLauncherField = actionExecutor.javaClass.getDeclaredField("appLauncher")
        appLauncherField.isAccessible = true
        return appLauncherField.get(actionExecutor)!!
    }

    private fun setCachedAppsOnAppLauncher(appLauncher: Any, entries: List<AppLauncher.AppEntry>) {
        val cachedAppsField = AppLauncher::class.java.getDeclaredField("cachedApps")
        cachedAppsField.isAccessible = true
        cachedAppsField.set(appLauncher, entries)
    }

    private class TestContext(
        baseContext: Context,
        private val packageManager: PackageManager
    ) : ContextWrapper(baseContext) {
        val launchedIntents = mutableListOf<Intent>()

        override fun getApplicationContext(): Context = this

        override fun getPackageManager(): PackageManager = packageManager

        override fun startActivity(intent: Intent) {
            launchedIntents.add(Intent(intent))
        }
    }
}
