package com.nova.luna.brain

import com.nova.luna.modelinstall.PrivateAppModelStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiagnosticBroadcastSupportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `command extra wins over request extra`() {
        val resolved = DiagnosticRequestResolver.resolve(
            command = "open camera",
            request = "ping"
        )

        assertEquals("open camera", resolved)
    }

    @Test
    fun `preflight reports the exact lite model path when missing`() {
        val storage = PrivateAppModelStorage.from(tempFolder.newFolder("model_root"))

        val preflight = LiteNativeProbePlanner.plan(storage)

        assertFalse(preflight.modelExists)
        assertFalse(preflight.shouldRunNativeProbe)
        assertTrue(preflight.reason.contains(preflight.modelFile.absolutePath))
        assertTrue(preflight.reason.contains("missing"))
    }

    @Test
    fun `preflight enables the native probe when the lite model file exists`() {
        val storage = PrivateAppModelStorage.from(tempFolder.newFolder("model_root"))
        val preflight = LiteNativeProbePlanner.plan(storage)

        preflight.modelFile.parentFile?.mkdirs()
        preflight.modelFile.writeText("fake gguf bytes")

        val readyPreflight = LiteNativeProbePlanner.plan(storage)

        assertTrue(readyPreflight.modelEnabled)
        assertTrue(readyPreflight.modelExists)
        assertTrue(readyPreflight.shouldRunNativeProbe)
        assertTrue(readyPreflight.reason.contains(readyPreflight.modelFile.absolutePath))
        assertTrue(readyPreflight.reason.contains("available"))
    }
}
