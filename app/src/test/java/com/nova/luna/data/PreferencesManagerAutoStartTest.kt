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
class PreferencesManagerAutoStartTest {
    private lateinit var tempDir: File
    private lateinit var context: TestContext

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("nova_luna_auto_start_test").toFile()
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
    fun `auto start defaults and persists across saves`() = runBlocking {
        val initialManager = PreferencesManager(context)

        assertEquals(false, initialManager.isAutoStartOnBootEnabled())
        assertEquals(false, initialManager.autoStartOnBootFlow.first())

        initialManager.setAutoStartOnBoot(true)
        val trueReloadedManager = PreferencesManager(context)
        assertEquals(true, trueReloadedManager.isAutoStartOnBootEnabled())
        assertEquals(true, trueReloadedManager.autoStartOnBootFlow.first())

        trueReloadedManager.setAutoStartOnBoot(false)
        val falseReloadedManager = PreferencesManager(context)
        assertEquals(false, falseReloadedManager.isAutoStartOnBootEnabled())
        assertEquals(false, falseReloadedManager.autoStartOnBootFlow.first())

        falseReloadedManager.setAutoStartOnBoot(true)
        val finalReloadedManager = PreferencesManager(context)
        assertEquals(true, finalReloadedManager.isAutoStartOnBootEnabled())
        assertEquals(true, finalReloadedManager.autoStartOnBootFlow.first())
    }

    private class TestContext(
        baseContext: Context,
        private val filesDir: File
    ) : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesDir
    }
}
