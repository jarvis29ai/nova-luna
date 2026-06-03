package com.nova.luna.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesManagerAssistantEnabledTest {
    private lateinit var tempDir: File
    private lateinit var context: TestContext

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("nova_luna_assistant_enabled_test").toFile()
        context = TestContext(
            baseContext = ApplicationProvider.getApplicationContext(),
            filesDir = tempDir
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `assistant enabled defaults and persists across saves`() = runBlocking {
        val initialManager = PreferencesManager(context)

        assertEquals(true, initialManager.isAssistantEnabled())
        assertEquals(true, initialManager.assistantEnabledFlow.first())

        initialManager.setAssistantEnabled(true)
        val trueReloadedManager = PreferencesManager(context)
        assertEquals(true, trueReloadedManager.isAssistantEnabled())
        assertEquals(true, trueReloadedManager.assistantEnabledFlow.first())

        trueReloadedManager.setAssistantEnabled(false)
        val falseReloadedManager = PreferencesManager(context)
        assertEquals(false, falseReloadedManager.isAssistantEnabled())
        assertEquals(false, falseReloadedManager.assistantEnabledFlow.first())

        falseReloadedManager.setAssistantEnabled(true)
        val finalReloadedManager = PreferencesManager(context)
        assertEquals(true, finalReloadedManager.isAssistantEnabled())
        assertEquals(true, finalReloadedManager.assistantEnabledFlow.first())
    }

    private class TestContext(
        baseContext: Context,
        private val filesDir: File
    ) : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesDir
    }
}
