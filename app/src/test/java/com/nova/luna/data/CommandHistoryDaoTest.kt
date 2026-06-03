package com.nova.luna.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class CommandHistoryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: CommandHistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.commandHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and getRecent returns newest history first`() = runBlocking {
        val older = CommandHistoryEntity(
            rawText = "open app",
            normalizedText = "open app",
            intentType = "NAVIGATION",
            actionType = "OPEN_APP",
            safetyLevel = "SAFE",
            safetyMessage = "Allowed",
            resultMessage = "Opening app.",
            success = true,
            shouldStopListening = false,
            timestamp = 100L
        )
        val newer = CommandHistoryEntity(
            rawText = "stop listening",
            normalizedText = "stop listening",
            intentType = "CONTROL",
            actionType = "STOP_SERVICE",
            safetyLevel = "SAFE",
            safetyMessage = "Allowed",
            resultMessage = "Voice assistant stopped.",
            success = true,
            shouldStopListening = true,
            timestamp = 200L
        )

        val olderId = dao.insert(older)
        val newerId = dao.insert(newer)
        val recent = dao.getRecent(10)

        assertTrue(olderId > 0)
        assertTrue(newerId > 0)
        assertNotEquals(olderId, newerId)
        assertEquals(2, recent.size)
        assertEquals("stop listening", recent[0].rawText)
        assertEquals(200L, recent[0].timestamp)
        assertEquals("open app", recent[1].rawText)
        assertEquals(100L, recent[1].timestamp)
    }

    @Test
    fun `clearAll removes all history rows`() = runBlocking {
        dao.insert(
            CommandHistoryEntity(
                rawText = "go home",
                normalizedText = "go home",
                intentType = "NAVIGATION",
                actionType = "GO_HOME",
                safetyLevel = "SAFE",
                safetyMessage = "Allowed",
                resultMessage = "Going home.",
                success = true,
                shouldStopListening = false,
                timestamp = 300L
            )
        )
        dao.insert(
            CommandHistoryEntity(
                rawText = "stop listening",
                normalizedText = "stop listening",
                intentType = "CONTROL",
                actionType = "STOP_SERVICE",
                safetyLevel = "SAFE",
                safetyMessage = "Allowed",
                resultMessage = "Voice assistant stopped.",
                success = true,
                shouldStopListening = true,
                timestamp = 400L
            )
        )

        val beforeClear = dao.getRecent(10)
        assertEquals(2, beforeClear.size)

        dao.clearAll()

        val afterClear = dao.getRecent(10)
        assertTrue(afterClear.isEmpty())
        assertEquals(0, afterClear.size)
    }

    @Test
    fun `getRecent one returns only the newest row`() = runBlocking {
        dao.insert(
            CommandHistoryEntity(
                rawText = "open app",
                normalizedText = "open app",
                intentType = "APP",
                actionType = "OPEN_APP",
                safetyLevel = "SAFE",
                safetyMessage = "Allowed",
                resultMessage = "Opening app.",
                success = true,
                shouldStopListening = false,
                timestamp = 500L
            )
        )
        dao.insert(
            CommandHistoryEntity(
                rawText = "stop listening",
                normalizedText = "stop listening",
                intentType = "CONTROL",
                actionType = "STOP_SERVICE",
                safetyLevel = "SAFE",
                safetyMessage = "Allowed",
                resultMessage = "Voice assistant stopped.",
                success = true,
                shouldStopListening = true,
                timestamp = 600L
            )
        )

        val recent = dao.getRecent(1)

        assertEquals(1, recent.size)
        assertEquals("stop listening", recent[0].rawText)
        assertEquals(600L, recent[0].timestamp)
    }
}
