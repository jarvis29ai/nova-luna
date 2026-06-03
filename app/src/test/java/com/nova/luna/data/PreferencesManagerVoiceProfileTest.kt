package com.nova.luna.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.model.VoiceProfile
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesManagerVoiceProfileTest {
    private lateinit var tempDir: File
    private lateinit var context: TestContext

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("nova_luna_voice_profile_test").toFile()
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
    fun `voice profile defaults and persists across saves`() = runBlocking {
        val initialManager = PreferencesManager(context)

        assertEquals(VoiceProfile.NOVA, initialManager.getVoiceProfile())
        assertEquals(VoiceProfile.NOVA, initialManager.voiceProfileFlow.first())

        initialManager.setVoiceProfile(VoiceProfile.NOVA)
        val novaReloadedManager = PreferencesManager(context)
        assertEquals(VoiceProfile.NOVA, novaReloadedManager.getVoiceProfile())
        assertEquals(VoiceProfile.NOVA, novaReloadedManager.voiceProfileFlow.first())

        novaReloadedManager.setVoiceProfile(VoiceProfile.LUNA)
        val lunaReloadedManager = PreferencesManager(context)
        assertEquals(VoiceProfile.LUNA, lunaReloadedManager.getVoiceProfile())
        assertEquals(VoiceProfile.LUNA, lunaReloadedManager.voiceProfileFlow.first())
    }

    private class TestContext(
        baseContext: Context,
        private val filesDir: File
    ) : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = filesDir
    }
}
