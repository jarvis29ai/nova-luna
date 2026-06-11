package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.modelinstall.ModelPackId
import com.nova.luna.modelinstall.PrivateAppModelStorage
import com.nova.luna.modelinstall.LocalRuntimeReadinessChecker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Phase8ModelRuntimeIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: PrivateAppModelStorage
    private lateinit var readinessChecker: LocalRuntimeReadinessChecker
    private lateinit var runtimeLoader: ModelRuntimeLoader
    private lateinit var bridge: com.nova.luna.modelinstall.ModelInstallBrainRouterBridge
    private lateinit var liteModelFile: File

    class FakeReadinessChecker(storage: PrivateAppModelStorage) : LocalRuntimeReadinessChecker(storage) {
        var liteReady = true
        override fun installReady(packId: ModelPackId): Boolean {
            return if (packId == ModelPackId.LITE) liteReady else false
        }
    }

    @Before
    fun setup() {
        val rootDir = tempFolder.newFolder("nova_luna_storage")
        storage = PrivateAppModelStorage.from(rootDir)
        readinessChecker = FakeReadinessChecker(storage)
        runtimeLoader = ModelRuntimeLoader(storage, readinessChecker)
        bridge = com.nova.luna.modelinstall.ModelInstallBrainRouterBridge(readinessChecker)
        
        // Mock a READY lite model file
        val liteDir = File(rootDir, "model_install/models/lite").apply { mkdirs() }
        liteModelFile = File(liteDir, PhoneLocalLlmModelId.QWEN_0_5B.defaultQuantizedFileName)
        liteModelFile.writeText("fake-gguf-content")
    }

    @Test
    fun `ModelRuntimeLoader resolves READY lite model path`() {
        assertTrue("Lite model file should exist", liteModelFile.exists())
        assertTrue("Lite pack should be ready", readinessChecker.installReady(ModelPackId.LITE))

        val engine = runtimeLoader.loadForRole(BrainModelRole.LITE_FALLBACK)
        assertTrue("Engine should be available", engine.available())
        assertEquals("Qwen 2.5 0.5B (Lite)", engine.modelDisplayName())
        assertTrue("Diagnostics should contain model path", engine.diagnostics().contains(liteModelFile.absolutePath))
    }

    @Test
    fun `BrainRouter prefers lite model for action JSON when ready`() {
        // We need a BrainService wired with dynamic runtimes
        val liteModel = LocalBrainModelClient(
            role = BrainModelRole.LITE_FALLBACK,
            roleReadinessProvider = bridge,
            engine = DynamicModelRuntime(BrainModelRole.LITE_FALLBACK, runtimeLoader)
        )
        
        val brainService = BrainService(
            localBrainRouterBridge = bridge,
            liteFallbackModelOverride = liteModel
        )

        // Request that should trigger ACTION_JSON
        val request = "prepare a message to mom"
        val diagnostics = brainService.diagnose(request)

        println("DEBUG: selectedRole=${diagnostics.routeDecision?.selectedRole}")
        println("DEBUG: selectedProvider=${diagnostics.runtimeStatus?.selectedProvider}")

        assertEquals(BrainModelRole.ACTION_JSON, diagnostics.routeDecision?.selectedRole)
        // In Phase 8, BrainService.evaluateRouteDecision prefers liteFallbackModel for ACTION_JSON if ready
        assertEquals("Qwen 2.5 0.5B (Lite)", diagnostics.runtimeStatus?.selectedLocalModelDisplayName)
    }

    @Test
    fun `Missing model falls back safely to rule-based ActionJsonModel`() {
        // Simulate missing model via readiness checker
        (readinessChecker as FakeReadinessChecker).liteReady = false
        assertFalse(readinessChecker.installReady(ModelPackId.LITE))

        val liteModel = LocalBrainModelClient(
            role = BrainModelRole.LITE_FALLBACK,
            roleReadinessProvider = bridge,
            engine = DynamicModelRuntime(BrainModelRole.LITE_FALLBACK, runtimeLoader)
        )
        
        val brainService = BrainService(
            localBrainRouterBridge = bridge,
            liteFallbackModelOverride = liteModel
        )

        val request = "prepare a message to mom"
        val diagnostics = brainService.diagnose(request)

        println("DEBUG: fallback selectedRole=${diagnostics.routeDecision?.selectedRole}")
        println("DEBUG: fallback selectedLocalModelDisplayName=${diagnostics.runtimeStatus?.selectedLocalModelDisplayName}")

        assertEquals(BrainModelRole.ACTION_JSON, diagnostics.routeDecision?.selectedRole)
        // Should fall back to rule-based ActionJsonModel because lite LLM is not ready
        assertNotEquals("Qwen 2.5 0.5B (Lite)", diagnostics.runtimeStatus?.selectedLocalModelDisplayName)
    }

    @Test
    fun `Runtime failure does not crash the app`() {
        // Create an engine that throws
        val failingEngine = object : PhoneLocalLlmEngine {
            override val engineName: String = "FailingEngine"
            override fun available(): Boolean = true
            override fun readinessStatus(): PhoneLocalLlmStatus = PhoneLocalLlmStatus.READY
            override fun modelId(): PhoneLocalLlmModelId = PhoneLocalLlmModelId.QWEN_0_5B
            override fun modelDisplayName(): String = "Failing Qwen"
            override fun maxInputTokens(): Int = 2048
            override fun generate(prompt: String, timeoutMs: Long): PhoneLocalLlmGenerationResult {
                throw RuntimeException("Simulated crash")
            }
            override fun cancel(): Boolean = false
            override fun diagnostics(): String = "failing"
        }

        val liteModel = LocalBrainModelClient(
            role = BrainModelRole.LITE_FALLBACK,
            roleReadinessProvider = { true },
            engine = failingEngine
        )

        val brainService = BrainService(
            liteFallbackModelOverride = liteModel
        )

        // This should not throw because BrainService uses runCatching
        // We use a planning request to ensure it attempts to use the liteFallbackModel
        val result = brainService.process("prepare a message")
        
        println("DEBUG: crash intent=${result.intent}")

        // It should return a fallback action
        // If LocalBrainModelClient catches the exception, it returns unavailable.
        // Then BrainService falls back to fallbackProvider (Mock) or fallback()
        assertTrue(result.intent == "local_model_unavailable" || result.intent == "unknown")
    }

    @Test
    fun `SafetyGate still blocks unsafe actions when lite generation is unavailable`() {
        val engine = LiteLocalModelRuntime(liteModelFile)
        val liteModel = LocalBrainModelClient(
            role = BrainModelRole.LITE_FALLBACK,
            roleReadinessProvider = bridge,
            engine = engine
        )
        
        val brainService = BrainService(
            localBrainRouterBridge = bridge,
            liteFallbackModelOverride = liteModel
        )

        val diagnostics = brainService.diagnose("send money to John")

        assertNotNull(diagnostics.finalSafetyDecision)
        assertFalse(diagnostics.finalSafetyDecision.allowed)
        assertTrue(diagnostics.finalBrainAction.intent == "human_only" || diagnostics.finalBrainAction.intent == "internet_unavailable")
    }
}
