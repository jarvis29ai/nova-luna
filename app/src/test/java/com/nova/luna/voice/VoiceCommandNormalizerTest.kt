package com.nova.luna.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceCommandNormalizerTest {

    private val normalizer = VoiceCommandNormalizer()

    @Test
    fun `normalize strips Luna wake word`() {
        val raw = "Luna open YouTube"
        assertEquals("open youtube", normalizer.normalize(raw))
    }

    @Test
    fun `normalize strips Hey Nova wake word`() {
        val raw = "Hey Nova please play music"
        assertEquals("please play music", normalizer.normalize(raw))
    }

    @Test
    fun `normalize strips Okay Luna wake word`() {
        val raw = "Okay Luna what is the time"
        assertEquals("what is the time", normalizer.normalize(raw))
    }

    @Test
    fun `normalize handles command without wake word`() {
        val raw = "open settings"
        assertEquals("open settings", normalizer.normalize(raw))
    }

    @Test
    fun `normalize handles punctuation after wake word`() {
        val raw = "Luna, open YouTube"
        assertEquals("open youtube", normalizer.normalize(raw))
    }

    @Test
    fun `isWakeWordDetected returns true for Luna`() {
        assertTrue(normalizer.isWakeWordDetected("Luna do something"))
    }

    @Test
    fun `isWakeWordDetected returns false for no wake word`() {
        assertFalse(normalizer.isWakeWordDetected("do something"))
    }

    @Test
    fun `isValidCommand returns true for valid command`() {
        assertTrue(normalizer.isValidCommand("open app"))
    }

    @Test
    fun `isValidCommand returns false for blank command`() {
        assertFalse(normalizer.isValidCommand(" "))
    }

    @Test
    fun `isValidCommand returns false for single character`() {
        assertFalse(normalizer.isValidCommand("a"))
    }
    
    @Test
    fun `normalize handles Hindi wake words`() {
        val raw = "नमस्ते लूना यूट्यूब खोलो"
        // Since I'm using lowercase(Locale.getDefault()) and my Hindi strings are fixed, 
        // I should check if it works as expected. 
        // Note: lowercase might not affect Hindi characters much but trim and removal will.
        assertEquals("यूट्यूब खोलो", normalizer.normalize(raw))
    }
}
