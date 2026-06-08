package com.nova.luna.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.brain.CommandBrain
import com.nova.luna.brain.UnifiedDomain
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
class LocalLlmBrainIntegrationTest {

    private lateinit var context: Context
    private lateinit var brain: CommandBrain
    private lateinit var fakeLlmManager: FakeLocalLlmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        brain = CommandBrain(context)
        fakeLlmManager = FakeLocalLlmManager(context)
        injectMockLlmManager(brain, fakeLlmManager)
    }

    @Test
    fun `brain calls LLM when rule confidence is low`() {
        val rawText = "something very complex and mixed language"

        fakeLlmManager.nextResult = LocalLlmResult(
            status = LocalLlmStatus.READY,
            modelId = LocalLlmModelId.GEMMA_3N_CORE,
            modelDisplayName = "Gemma 3n",
            parsedDomain = UnifiedDomain.MEDIA,
            parsedCandidateAction = CommandIntent(rawText = rawText, actionType = ActionType.LAUNCH_APP),
            confidence = 0.9f
        )

        val result = brain.process(rawText)

        assertTrue(fakeLlmManager.wasCalled)
        assertEquals(UnifiedDomain.MEDIA, result.domain)
    }

    @Test
    fun `brain does not call LLM for high confidence rule command`() {
        val rawText = "Luna open YouTube"

        val result = brain.process(rawText)

        assertFalse(fakeLlmManager.wasCalled)
        assertEquals(UnifiedDomain.PHONE_CONTROL, result.domain)
    }

    private fun injectMockLlmManager(brain: CommandBrain, manager: LocalLlmManager) {
        val field: Field = CommandBrain::class.java.getDeclaredField("localLlmManager")
        field.isAccessible = true
        field.set(brain, manager)
    }

    class FakeLocalLlmManager(context: Context) : LocalLlmManager(context) {
        var wasCalled = false
        var nextResult: LocalLlmResult? = null

        override fun process(request: LocalLlmRequest): LocalLlmResult {
            wasCalled = true
            return nextResult ?: LocalLlmResult(
                status = LocalLlmStatus.FAILED,
                modelId = request.modelId,
                modelDisplayName = "Fake",
                technicalReason = "No result set"
            )
        }
    }
}

