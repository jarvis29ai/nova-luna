package com.nova.luna.brain

import com.nova.luna.executor.ActionExecutorGateway
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.CommandResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainServicePhase6Test {
    private val codec = BrainActionJsonCodec()

    @Test
    fun `gemma config disabled falls back to local mock provider`() {
        val service = serviceFor(
            config = gemmaConfig(
                gemmaEnabled = false,
                gemmaModelAssetPath = tempModelFilePath()
            ),
            backend = StaticGemmaBackend()
        )

        val diagnostics = service.diagnose("why is the sky blue?")

        assertEquals(BrainModelRole.GEMMA_REASONING, diagnostics.selectedRole)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals("LocalMockBrainProvider", diagnostics.finalProvider)
        assertNotNull(diagnostics.runtimeStatus)
        assertEquals(BrainModelRole.GEMMA_REASONING, diagnostics.runtimeStatus?.selectedBrainRole)
        assertFalse(diagnostics.runtimeStatus?.runtimeAvailable ?: true)
        assertFalse(diagnostics.runtimeStatus?.modelLoaded ?: true)
        assertTrue(diagnostics.runtimeStatus?.modelPathConfigured == true)
        assertTrue(diagnostics.runtimeStatus?.modelFileExists == true)
        assertTrue(diagnostics.runtimeStatus?.reason?.contains("disabled", ignoreCase = true) == true)
    }

    @Test
    fun `missing model path falls back to local mock provider`() {
        val service = serviceFor(
            config = gemmaConfig(
                gemmaEnabled = true,
                gemmaModelAssetPath = ""
            ),
            backend = StaticGemmaBackend()
        )

        val diagnostics = service.diagnose("what is a good way to learn Kotlin?")

        assertEquals(BrainModelRole.GEMMA_REASONING, diagnostics.selectedRole)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals("LocalMockBrainProvider", diagnostics.finalProvider)
        assertNotNull(diagnostics.runtimeStatus)
        assertEquals(BrainModelRole.GEMMA_REASONING, diagnostics.runtimeStatus?.selectedBrainRole)
        assertFalse(diagnostics.runtimeStatus?.modelPathConfigured ?: true)
        assertFalse(diagnostics.runtimeStatus?.modelFileExists ?: true)
        assertFalse(diagnostics.runtimeStatus?.modelLoaded ?: true)
        assertTrue(diagnostics.runtimeStatus?.runtimeAvailable == true)
        assertTrue(diagnostics.runtimeStatus?.reason?.contains("not configured", ignoreCase = true) == true)
    }

    @Test
    fun `gemma runtime unavailable falls back to local mock provider`() {
        val service = serviceFor(
            config = gemmaConfig(
                gemmaEnabled = true,
                gemmaModelAssetPath = tempModelFilePath()
            ),
            backend = StaticGemmaBackend(runtimeAvailable = false)
        )

        val diagnostics = service.diagnose("explain how a phone local assistant works")

        assertEquals(BrainModelRole.GEMMA_REASONING, diagnostics.selectedRole)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals("LocalMockBrainProvider", diagnostics.finalProvider)
        assertNotNull(diagnostics.runtimeStatus)
        assertEquals(BrainModelRole.GEMMA_REASONING, diagnostics.runtimeStatus?.selectedBrainRole)
        assertTrue(diagnostics.runtimeStatus?.modelPathConfigured == true)
        assertTrue(diagnostics.runtimeStatus?.modelFileExists == true)
        assertFalse(diagnostics.runtimeStatus?.runtimeAvailable ?: true)
        assertFalse(diagnostics.runtimeStatus?.modelLoaded ?: true)
        assertTrue(diagnostics.runtimeStatus?.reason?.contains("backend", ignoreCase = true) == true)
    }

    @Test
    fun `dangerous gemma output is rejected by the validator`() {
        val service = serviceFor(
            config = gemmaConfig(
                gemmaEnabled = true,
                gemmaModelAssetPath = tempModelFilePath()
            ),
            backend = StaticGemmaBackend(response = dangerousGemmaJson())
        )

        val diagnostics = service.diagnose("why is the sky blue?")

        assertEquals(BrainModelRole.GEMMA_REASONING, diagnostics.selectedRole)
        assertNotNull(diagnostics.parsedBrainAction)
        assertTrue(diagnostics.parsedBrainAction?.finalActionAllowed == true)
        assertFalse(diagnostics.validatorResult)
        assertTrue(diagnostics.fallbackUsed)
        assertEquals("LocalMockBrainProvider", diagnostics.finalProvider)
        assertTrue(diagnostics.runtimeStatus?.runtimeAvailable == true)
        assertTrue(diagnostics.runtimeStatus?.modelLoaded == true)
        assertTrue(diagnostics.runtimeStatus?.fallbackActive == true)
        assertEquals(BrainModelRole.GEMMA_REASONING, diagnostics.runtimeStatus?.selectedBrainRole)
        assertTrue(
            diagnostics.runtimeStatus?.reason?.contains("BrainActionValidator", ignoreCase = true) == true
        )
    }

    @Test
    fun `no model can call action executor directly`() {
        val service = serviceFor(
            config = gemmaConfig(
                gemmaEnabled = true,
                gemmaModelAssetPath = tempModelFilePath()
            ),
            backend = StaticGemmaBackend(response = dangerousGemmaJson())
        )

        val diagnostics = service.diagnose("why is the sky blue?")
        val executor = FakeActionExecutor()
        val commandRouter = CommandRouter(executor)

        commandRouter.route(diagnostics.finalBrainAction)

        assertEquals(0, executor.executeCount)
    }

    @Test
    fun `action json model stays strict when gemma reasoning input is supplied`() {
        val model = ActionJsonModel()
        val routeDecision = BrainRouteDecision(
            selectedRole = BrainModelRole.ACTION_JSON,
            reason = "Structured cab planning keeps final actions blocked.",
            requiresInternet = false,
            requiresScreenContext = false,
            fallbackAllowed = true,
            safetyNotes = listOf("Gemma reasoning input is advisory only.")
        )

        val result = model.generate(
            request = BrainRequest("book cheapest auto to DB Mall"),
            routeDecision = routeDecision,
            reasoningHint = dangerousGemmaJson()
        )

        assertTrue(result.available)
        assertNotNull(result.rawResponse)
        assertNotNull(result.candidateAction)
        assertEquals(result.candidateAction, codec.decode(result.rawResponse!!))
        assertFalse(result.candidateAction?.finalActionAllowed ?: true)
        assertTrue(
            result.safetyNotes.any { it.contains("Gemma reasoning input", ignoreCase = true) }
        )
    }

    private fun serviceFor(config: GemmaPhoneConfig, backend: PhoneGemmaRuntimeBackend): BrainService {
        return BrainService(
            gemmaRuntime = PhoneGemmaRuntime(
                config = config,
                backend = backend
            )
        )
    }

    private fun gemmaConfig(
        gemmaEnabled: Boolean,
        gemmaModelAssetPath: String
    ): GemmaPhoneConfig {
        return GemmaPhoneConfig(
            gemmaEnabled = gemmaEnabled,
            gemmaModelAssetPath = gemmaModelAssetPath,
            gemmaMaxTokens = 128,
            gemmaTemperature = 0.2,
            gemmaTopK = 40,
            gemmaContextWindow = 8192,
            gemmaRoleEnabled = true
        )
    }

    private fun tempModelFilePath(): String {
        return File.createTempFile("gemma-phone-model", ".bin").apply {
            deleteOnExit()
        }.absolutePath
    }

    private fun dangerousGemmaJson(): String {
        return """
            {
              "intent": "cab_booking",
              "reply": "Ready to book and pay now.",
              "actionType": "prepare",
              "riskLevel": "confirmation_required",
              "requiresConfirmation": true,
              "finalActionAllowed": true,
              "params": {
                "rawText": "why is the sky blue?",
                "dropLocation": "DB Mall"
              },
              "nextQuestion": "Confirm booking now?"
            }
        """.trimIndent()
    }

    private class StaticGemmaBackend(
        override val backendName: String = "StaticGemmaBackend",
        private val runtimeAvailable: Boolean = true,
        private val response: String = """
            {
              "intent": "explain",
              "reply": "Gemma reasoning placeholder.",
              "actionType": "read_only",
              "riskLevel": "safe",
              "requiresConfirmation": false,
              "finalActionAllowed": false,
              "params": {
                "rawText": "placeholder"
              }
            }
        """.trimIndent()
    ) : PhoneGemmaRuntimeBackend {
        override fun isRuntimeAvailable(): Boolean = runtimeAvailable

        override fun generate(prompt: String, config: GemmaPhoneConfig): String {
            check(runtimeAvailable) {
                "Gemma backend should not be called when it is unavailable."
            }
            return response
        }
    }

    private class FakeActionExecutor : ActionExecutorGateway {
        var executeCount: Int = 0

        override fun execute(commandIntent: CommandIntent): CommandResult {
            executeCount += 1
            return CommandResult.success(
                message = "Executed",
                intentType = commandIntent.intentType,
                actionType = commandIntent.actionType,
                entities = commandIntent.entities
            )
        }

        override fun hasActiveCabBookingSession(): Boolean = false
        override fun cancelCabBookingSession(): CommandResult = CommandResult.success("Cancelled")
        override fun handleCabBookingText(rawText: String): CommandResult = CommandResult.success("Handled")

        override fun hasActiveFoodBookingSession(): Boolean = false
        override fun cancelFoodBookingSession(): CommandResult = CommandResult.success("Cancelled food")
        override fun handleFoodBookingText(rawText: String): CommandResult = CommandResult.success("Handled food")

        override fun hasActiveGroceryBookingSession(): Boolean = false
        override fun cancelGroceryBookingSession(): CommandResult = CommandResult.success("Cancelled grocery")
        override fun handleGroceryBookingText(rawText: String, userConfirmed: Boolean): CommandResult = CommandResult.success("Handled grocery")

        override fun hasActivePhoneContactSession(): Boolean = false
        override fun handlePhoneContactText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")
        override fun hasActiveCommunicationSession(): Boolean = false
        override fun handleCommunicationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")
        override fun hasActiveContentCreationSession(): Boolean = false
        override fun handleContentCreationText(rawText: String, commandIntent: CommandIntent): CommandResult = CommandResult.success("Handled")
    }
}
