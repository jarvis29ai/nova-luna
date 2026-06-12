package com.nova.luna.brain

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CommandBrainOpenAppTest {
    private lateinit var context: TestContext
    private lateinit var brain: CommandBrain

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        context = TestContext(baseContext)
        
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

            assertTrue("Expected success for phrase: $phrase. Result: ${result.message} (Status: ${result.status}, Action: ${result.actionType})", result.success)
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
            assertEquals("Opened WhatsApp.", result.message)
            assertFalse(result.shouldStopListening)
            assertEquals(expectedAppName, result.entities["appName"])
            assertEquals("com.whatsapp", result.entities["resolvedPackage"])
            assertEquals("WhatsApp", result.entities["resolvedLabel"])
        }

        assertEquals(5, context.launchedIntents.size)
        assertEquals(Intent.ACTION_MAIN, context.launchedIntents.last().action)
        assertEquals("com.whatsapp", context.launchedIntents.last().`package`)
    }

    private fun seedInstalledApp(label: String, packageName: String) {
        val pm = ApplicationProvider.getApplicationContext<Context>().packageManager
        val shadowPm = shadowOf(pm)
        
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.setPackage(packageName)
        
        // Use Robolectric to install the app
        val activityInfo = android.content.pm.ActivityInfo()
        activityInfo.packageName = packageName
        activityInfo.name = "MainActivity"
        activityInfo.nonLocalizedLabel = label
        
        val resolveInfo = android.content.pm.ResolveInfo()
        resolveInfo.activityInfo = activityInfo
        
        shadowPm.addResolveInfoForIntent(intent, resolveInfo)
        
        // Also add a generic launcher intent since AppResolver uses queryIntentActivities(mainIntent, 0)
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        shadowPm.addResolveInfoForIntent(mainIntent, resolveInfo)
        
        // Set launch intent
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        shadowPm.addResolveInfoForIntent(launchIntent, resolveInfo)
        
        // Install package info
        val packageInfo = android.content.pm.PackageInfo()
        packageInfo.packageName = packageName
        val appInfo = android.content.pm.ApplicationInfo().apply {
            this.packageName = packageName
            this.nonLocalizedLabel = label
            this.flags = android.content.pm.ApplicationInfo.FLAG_INSTALLED
        }
        packageInfo.applicationInfo = appInfo
        shadowPm.installPackage(packageInfo)
    }

    private class TestContext(
        baseContext: Context
    ) : ContextWrapper(baseContext) {
        val launchedIntents = mutableListOf<Intent>()

        override fun getApplicationContext(): Context = this

        override fun startActivity(intent: Intent) {
            launchedIntents.add(Intent(intent))
            super.startActivity(intent)
        }
    }
}
