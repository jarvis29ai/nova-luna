package com.nova.luna.brain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlutterAppIsolationTest {
    @Test
    fun `flutter app is not wired into the Android module graph`() {
        val settingsFile = listOf(
            File("settings.gradle"),
            File("../settings.gradle")
        ).firstOrNull { it.exists() }
            ?: error("Could not find settings.gradle from the unit test working directory.")

        val settingsGradle = settingsFile.readText()

        assertTrue(settingsGradle.contains(":app"))
        assertTrue(settingsGradle.contains(":wear"))
        assertTrue(settingsGradle.contains(":shared"))
        assertFalse(settingsGradle.contains("flutter_app"))
    }
}
