package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.modelinstall.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File

class Phase8ModelRuntimeIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: PrivateAppModelStorage
    private lateinit var modelInstallService: ModelInstallService
    private lateinit var runtimeLoader: ModelRuntimeLoader
    private lateinit var runtimeManager: ModelRuntimeManager
    private lateinit var bridge: ModelInstallBrainRouterBridge
    private lateinit var liteModelFile: File

    @Before
    fun setup() {
        val rootDir = tempFolder.newFolder("nova_luna_storage")
        storage = PrivateAppModelStorage.from(rootDir)
        
        val stateStore = ModelRuntimeStateStore(storage)
        val context = mock(android.content.Context::class.java)
        val pathResolver = ModelPathResolver(context, storage, stateStore)
        val verifier = ModelInstallVerifier()
        val specRegistry = ModelInstallSpecRegistry()
        
        modelInstallService = ModelInstallService(
            specRegistry = specRegistry,
            pathResolver = pathResolver,
            verifier = verifier,
            runtimeStateStore = stateStore,
            storage = storage
        )
        
        runtimeLoader = ModelRuntimeLoader(storage, modelInstallService)
        val ramGuard = ModelRamGuard(FakeRamInfoProvider())
        runtimeManager = ModelRuntimeManager(runtimeLoader, ramGuard)
        bridge = ModelInstallBrainRouterBridge(modelInstallService)
        
        // Mock a READY lite model file
        val liteDir = File(rootDir, "model_install/models/lite").apply { mkdirs() }
        liteModelFile = File(liteDir, PhoneLocalLlmModelId.QWEN_0_5B.defaultQuantizedFileName)
        liteModelFile.writeBytes(ByteArray(100_000_001)) // Enough for PRIMARY_BRAIN minimumBytes
        
        // Save verified path so it's ready
        modelInstallService.saveVerifiedModelPath("lite", liteModelFile.absolutePath)
    }

    @Test
    fun `ModelRuntimeLoader resolves READY lite model path`() {
        assertTrue("Lite model file should exist", liteModelFile.exists())
        assertNotNull("Lite model should be ready", modelInstallService.getReadyModelPath("lite"))

        val engine = runtimeLoader.loadForRole(BrainModelRole.LITE_FALLBACK)
        assertTrue("Engine should be available", engine.available())
        assertEquals("Qwen 2.5 0.5B (Lite)", engine.modelDisplayName())
        assertTrue("Diagnostics should contain model path", engine.diagnostics().contains(liteModelFile.absolutePath))
    }

    @Test
    fun `BrainRouter uses the ready lite model for message planning`() {
        // We need a BrainService wired with dynamic runtimes
        val liteModel = LocalBrainModelClient(
            role = BrainModelRole.LITE_FALLBACK,
            roleReadinessProvider = bridge,
            manager = runtimeManager
        )
        
        val brainService = BrainService(
            localBrainRouterBridge = bridge,
            liteFallbackModelOverride = liteModel,
            modelInstallService = modelInstallService,
            modelRuntimeManager = runtimeManager
        )

        // Request that should trigger ACTION_JSON
        val request = "prepare a message to mom"
        val diagnostics = brainService.diagnose(request)

        println("DEBUG: selectedRole=${diagnostics.routeDecision?.selectedRole}")
        println("DEBUG: selectedProvider=${diagnostics.runtimeStatus?.selectedProvider}")

        assertEquals(BrainModelRole.LITE_FALLBACK, diagnostics.routeDecision?.selectedRole)
        assertEquals("Qwen 2.5 0.5B (Lite)", diagnostics.runtimeStatus?.selectedLocalModelDisplayName)
        assertNotNull(diagnostics.routerTrace)
        assertTrue(diagnostics.routerTrace?.brain_router_used == true)
        assertEquals(BrainModelRole.LITE_FALLBACK, diagnostics.routerTrace?.selected_model_role)
        assertFalse(diagnostics.routerTrace?.mock_fallback_used ?: true)
        assertTrue(diagnostics.routerTrace?.real_model_invoked == true)
    }

    @Test
    fun `Missing model falls back safely with an honest router reason`() {
        // Simulate missing model
        liteModelFile.delete()
        modelInstallService.clearBrokenModelPath("lite")
        assertNull(modelInstallService.getReadyModelPath("lite"))

        val liteModel = LocalBrainModelClient(
            role = BrainModelRole.LITE_FALLBACK,
            roleReadinessProvider = bridge,
            manager = runtimeManager
        )
        
        val brainService = BrainService(
            localBrainRouterBridge = bridge,
            liteFallbackModelOverride = liteModel,
            modelInstallService = modelInstallService,
            modelRuntimeManager = runtimeManager
        )

        val request = "prepare a message to mom"
        val diagnostics = brainService.diagnose(request)

        println("DEBUG: fallback selectedRole=${diagnostics.routeDecision?.selectedRole}")
        println("DEBUG: fallback selectedLocalModelDisplayName=${diagnostics.runtimeStatus?.selectedLocalModelDisplayName}")

        // Should fall back to ACTION_JSON (Mock) if lite is missing
        assertEquals(BrainModelRole.ACTION_JSON, diagnostics.routeDecision?.selectedRole)
        assertTrue(diagnostics.routeDecision?.reason?.contains("No ready local model was available", ignoreCase = true) == true)
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
            override fun unload(): Boolean = true
            override fun diagnostics(): String = "failing"
        }

        val liteModel = LocalBrainModelClient(
            role = BrainModelRole.LITE_FALLBACK,
            roleReadinessProvider = { true },
            engine = failingEngine
        )

        val brainService = BrainService(
            liteFallbackModelOverride = liteModel,
            modelInstallService = modelInstallService,
            modelRuntimeManager = runtimeManager
        )

        // This should not throw because BrainService uses runCatching
        val result = brainService.process("prepare a message")
        
        println("DEBUG: crash intent=${result.intent}")

        // It should return a fallback action
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
            liteFallbackModelOverride = liteModel,
            modelInstallService = modelInstallService,
            modelRuntimeManager = runtimeManager
        )

        val diagnostics = brainService.diagnose("send money to John")

        assertNotNull(diagnostics.finalSafetyDecision)
        assertFalse(diagnostics.finalSafetyDecision.allowed)
        assertTrue(diagnostics.finalBrainAction.intent == "human_only" || diagnostics.finalBrainAction.intent == "internet_unavailable")
    }
}
