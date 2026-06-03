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
class PreferencesManagerWakePhraseTest {
    private lateinit var tempDir: File
    private lateinit var context: TestContext

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("nova_luna_wake_phrase_test").toFile()
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
    fun `wake phrase defaults and persists across saves`() = runBlocking {
        val initialManager = PreferencesManager(context)

        assertEquals(PreferencesManager.DEFAULT_WAKE_PHRASE, initialManager.getWakePhrase())
        assertEquals(PreferencesManager.DEFAULT_WAKE_PHRASE, initialManager.wakePhraseFlow.first())

        initialManager.setWakePhrase("Nova Luna")
        val customReloadedManager = PreferencesManager(context)
        assertEquals("Nova Luna", customReloadedManager.getWakePhrase())
        assertEquals("Nova Luna", customReloadedManager.wakePhraseFlow.first())

        customReloadedManager.setWakePhrase("")
        val blankReloadedManager = PreferencesManager(context)
        assertEquals(PreferencesManager.DEFAULT_WAKE_PHRASE, blankReloadedManager.getWakePhrase())
        assertEquals(PreferencesManager.DEFAULT_WAKE_PHRASE, blankReloadedManager.wakePhraseFlow.first())

        blankReloadedManager.setWakePhrase("   ")
        val whitespaceReloadedManager = PreferencesManager(context)
        assertEquals(PreferencesManager.DEFAULT_WAKE_PHRASE, whitespaceReloadedManager.getWakePhrase())
        assertEquals(PreferencesManager.DEFAULT_WAKE_PHRASE, whitespaceReloadedManager.wakePhraseFlow.first())
    }

    private class TestContext(
        baseContext: Context,
        private val filesDir: File
    ) : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesDir
    }
}
