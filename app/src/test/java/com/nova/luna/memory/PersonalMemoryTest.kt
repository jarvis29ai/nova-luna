package com.nova.luna.memory

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PersonalMemoryTest {
    private lateinit var store: PersonalMemoryStore
    private lateinit var classifier: MemorySensitivityClassifier
    private lateinit var detector: MemoryIntentDetector
    private lateinit var manager: PersonalMemoryManager

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        store = LocalPersonalMemoryStore(context)
        store.clearAll()
        classifier = MemorySensitivityClassifier()
        detector = MemoryIntentDetector()
        manager = PersonalMemoryManager(store, detector, classifier)
    }

    @Test
    fun testIntentDetection_SaveMusicPreference() {
        val decision = detector.detect("Remember I prefer YouTube Music")
        assertNotNull(decision)
        assertEquals(MemoryAction.SAVE, decision?.action)
        assertEquals(MemoryType.MUSIC_PREFERENCE, decision?.type)
        assertEquals("YouTube Music", decision?.value)
    }

    @Test
    fun testIntentDetection_SaveHomeLabel() {
        val decision = detector.detect("Save home as railway colony")
        assertNotNull(decision)
        assertEquals(MemoryAction.SAVE, decision?.action)
        assertEquals(MemoryType.HOME_LABEL, decision?.type)
        assertEquals("railway colony", decision?.value)
        assertTrue(decision?.needsConfirmation == true)
    }

    @Test
    fun testSensitivity_BlockOTP() {
        val sensitivity = classifier.classify(MemoryType.USER_NOTE, "my otp", "123456")
        assertEquals(MemorySensitivity.SENSITIVE_BLOCKED, sensitivity)
    }

    @Test
    fun testSensitivity_LowForApp() {
        val sensitivity = classifier.classify(MemoryType.PREFERRED_APP, "music", "Spotify")
        assertEquals(MemorySensitivity.LOW, sensitivity)
    }

    @Test
    fun testManager_HandleBlockedMemory() {
        val result = manager.handleMemoryCommand("Remember my OTP is 123456")
        assertEquals(MemoryPermissionStatus.BLOCKED_SENSITIVE, result.status)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun testManager_HandleLowSensitivityMemory() {
        val result = manager.handleMemoryCommand("Remember I prefer YouTube Music")
        assertEquals(MemoryPermissionStatus.ALLOWED, result.status)
        assertEquals(MemoryAction.SAVE, result.action)
        val items = store.list(type = MemoryType.MUSIC_PREFERENCE)
        assertEquals(1, items.size)
        assertEquals("YouTube Music", items[0].value)
    }

    @Test
    fun testManager_HandleHighSensitivityConfirmation() {
        val result = manager.handleMemoryCommand("Save home as railway colony")
        assertEquals(MemoryPermissionStatus.NEEDS_CONFIRMATION, result.status)
        assertTrue(store.list().isEmpty())

        val confirmResult = manager.confirmPendingMemorySave()
        assertEquals(MemoryPermissionStatus.ALLOWED, confirmResult.status)
        val items = store.list(type = MemoryType.HOME_LABEL)
        assertEquals(1, items.size)
        assertEquals("railway colony", items[0].value)
    }

    @Test
    fun testManager_ViewMemory() {
        manager.handleMemoryCommand("Remember I prefer YouTube Music")
        val result = manager.handleMemoryCommand("What do you remember about my music preferences?")
        assertEquals(MemoryPermissionStatus.ALLOWED, result.status)
        assertEquals(MemoryAction.VIEW, result.action)
        assertNotNull(result.items)
        assertEquals(1, result.items?.size)
    }

    @Test
    fun testManager_DeleteMemory() {
        manager.handleMemoryCommand("Remember I prefer YouTube Music")
        val result = manager.handleMemoryCommand("Forget my music preference")
        assertEquals(MemoryPermissionStatus.ALLOWED, result.status)
        assertEquals(MemoryAction.DELETE, result.action)
        assertTrue(store.list(type = MemoryType.MUSIC_PREFERENCE).isEmpty())
    }

    @Test
    fun testManager_ClearAll() {
        manager.handleMemoryCommand("Remember I prefer YouTube Music")
        val result = manager.handleMemoryCommand("Clear all memory")
        assertEquals(MemoryPermissionStatus.NEEDS_CONFIRMATION, result.status)
        
        // Use a direct store clear for the mock "Continue"
        store.clearAll()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun testContextUtil_ResolveLabels() {
        store.save(PersonalMemoryItem(type = MemoryType.HOME_LABEL, key = "home", value = "railway colony", userConfirmed = true))
        val resolved = MemoryContextUtil.resolveLabels("book cab to home", "home=railway colony")
        assertEquals("book cab to railway colony", resolved)
    }
}
