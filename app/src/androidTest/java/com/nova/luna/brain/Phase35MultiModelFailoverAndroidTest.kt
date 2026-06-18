package com.nova.luna.brain
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nova.luna.diagnostics.NativeProofProcessController
import com.nova.luna.diagnostics.NativeProofStage
import com.nova.luna.diagnostics.NativeProofStageResult
import com.nova.luna.model.BrainModelRole
import com.nova.luna.modelinstall.ModelInstallService
import com.nova.luna.modelinstall.ModelInstallSpecRegistry
import com.nova.luna.modelinstall.ModelInstallVerifier
import com.nova.luna.modelinstall.ModelPathResolver
import com.nova.luna.modelinstall.ModelRuntimeStateStore
import com.nova.luna.modelinstall.PrivateAppModelStorage
import com.nova.luna.modelinstall.Sha256ModelVerifier
import com.nova.luna.modelinstall.ModelInstallBrainRouterBridge
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
/**
 * Phase 35: Multi-Model Failover Android Test.
 *
 * This test verifies the failover logic across three tiers of local models:
 * 1. CORE_BRAIN (Gemma 3N LiteRT) - Depth 0
 * 2. MULTILINGUAL_BACKUP (Qwen 2.5 1.5B GGUF) - Depth 1
 * 3. LITE_FALLBACK (Qwen 2.5 0.5B GGUF) - Depth 2
 *
 * It uses FailoverOverrideMarkers to force roles unavailable and confirms that
 * ModelInstallBrainRouterBridge selects the correct next-available role.
 */
@RunWith(AndroidJUnit4::class)
class Phase35MultiModelFailoverAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var modelInstallService: ModelInstallService
    private lateinit var bridge: ModelInstallBrainRouterBridge
    private var nativeController: NativeProofProcessController? = null
    @Before
    fun setUp() {
        Log.i(TAG, "Setting up Phase 35 Failover Test")
        clearAllOverrides()
        val storage = PrivateAppModelStorage.from(context)
        val runtimeStateStore = ModelRuntimeStateStore(storage)
        modelInstallService = ModelInstallService(
            specRegistry = ModelInstallSpecRegistry(),
            pathResolver = ModelPathResolver(context, storage, runtimeStateStore),
            verifier = ModelInstallVerifier(),
            runtimeStateStore = runtimeStateStore,
            storage = storage
        )
        bridge = ModelInstallBrainRouterBridge(
            modelInstallService,
            filesDir = context.filesDir,
            coreRuntimeAvailable = {
                // CORE_BRAIN is Gemma. We check if the model file is available.
                try {
                    findGemmaModel().exists()
                } catch (e: Exception) {
                    false
                }
            }
        )
    }
    @After
    fun tearDown() {
        Log.i(TAG, "Tearing down Phase 35 Failover Test")
        clearAllOverrides()
        nativeController?.close()
        nativeController = null
    }
    companion object {
        private const val TAG = "Phase35FailoverTest"
        private const val FULL_SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
        private const val LITE_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            FailoverOverrideMarkers.allMarkerFileNames().forEach { name ->
                val file = File(context.filesDir, name)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
    private fun clearAllOverrides() {
        FailoverOverrideMarkers.allMarkerFileNames().forEach { name ->
            val file = File(context.filesDir, name)
            if (file.exists()) {
                file.delete()
            }
        }
    }
    private fun forceUnavailable(role: BrainModelRole) {
        val markerName = FailoverOverrideMarkers.markerFileName(role)
            ?: throw IllegalArgumentException("No marker for role $role")
        val file = File(context.filesDir, markerName)
        if (!file.exists()) {
            assertTrue("Failed to create override marker for $role", file.createNewFile())
        }
    }
    @Test
    fun caseA_GemmaCoreBrainWinsWhenNoOverrides() {
        Log.i(TAG, "Executing Case A: Gemma Core Brain")
        // Assert Case A readiness
        assertTrue("CORE_BRAIN should be ready when no overrides", bridge.isReady(BrainModelRole.CORE_BRAIN))
        // Run Gemma proof
        val modelFile = findGemmaModel()
        assertTrue("Gemma model missing at ${modelFile.absolutePath}", modelFile.exists())
        val config = GemmaPhoneConfig(
            gemmaEnabled = true,
            gemmaRealInferenceEnabled = true,
            gemmaModelAssetPath = modelFile.absolutePath,
            gemmaMaxTokens = 64,
            gemmaTemperature = 0.2,
            gemmaTopK = 40,
            gemmaContextWindow = 8192,
            gemmaRoleEnabled = true
        )
        val backend = LiteRTGemmaRuntimeBackend(context)
        val prompt = "Reply with exactly: GEMMA_BRAIN_OK"
        val response = backend.generate(prompt, config)
        assertNotNull("Gemma response should not be null", response)
        assertFalse("Gemma response should not be an error: $response", response.startsWith("Error:"))
        assertTrue("Gemma response should contain marker 'GEMMA_BRAIN_OK'. Got: '$response'",
            response.contains("GEMMA_BRAIN_OK", ignoreCase = true))
        // Final verification of routing
        val request = BrainRequest(rawText = "Please explain why machine learning models can sometimes fail")
        val decision = bridge.selectLocalRoute(request, allowOnlineHelper = true)
        assertNotNull("Should have a local route decision", decision)
        assertEquals("Decision should select CORE_BRAIN", BrainModelRole.CORE_BRAIN, decision?.selectedRole)
    }
    @Test
    fun caseB_QwenFullWinsWhenCoreOverridden() {
        Log.i(TAG, "Executing Case B: Qwen Full Multilingual Backup")
        forceUnavailable(BrainModelRole.CORE_BRAIN)
        // Assert Case B readiness
        assertFalse("CORE_BRAIN should NOT be ready", bridge.isReady(BrainModelRole.CORE_BRAIN))
        assertTrue("MULTILINGUAL_BACKUP should be ready", bridge.isReady(BrainModelRole.MULTILINGUAL_BACKUP))
        // Run Qwen 1.5B (full) proof
        val fixture = loadFullModelFixture()
        val controller = NativeProofProcessController(context)
        nativeController = controller
        val result = runProof(
            controller = controller,
            stage = NativeProofStage.READABLE_OUTPUT,
            modelId = "full",
            modelFile = fixture.file,
            modelSha256 = fixture.sha256,
            prompt = "Reply with exactly: NOVA_BRAIN_OK",
            maxTokens = 12
        )
        assertSuccessfulStage("Case B (Full)", result, fixture)
        assertTrue("Decoded text should contain marker 'NOVA_BRAIN_OK'",
            result.decodedText.orEmpty().contains("NOVA_BRAIN_OK"))
        // Final verification of routing
        val request = BrainRequest(rawText = "explain this in hindi")
        val decision = bridge.selectLocalRoute(request, allowOnlineHelper = true)
        assertNotNull("Should have a local route decision", decision)
        assertEquals("Decision should select MULTILINGUAL_BACKUP", BrainModelRole.MULTILINGUAL_BACKUP, decision?.selectedRole)
    }
    @Test
    fun caseC_QwenLiteWinsWhenCoreAndFullOverridden() {
        Log.i(TAG, "Executing Case C: Qwen Lite Fallback")
        forceUnavailable(BrainModelRole.CORE_BRAIN)
        forceUnavailable(BrainModelRole.MULTILINGUAL_BACKUP)
        // Assert Case C readiness
        assertFalse("CORE_BRAIN should NOT be ready", bridge.isReady(BrainModelRole.CORE_BRAIN))
        assertFalse("MULTILINGUAL_BACKUP should NOT be ready", bridge.isReady(BrainModelRole.MULTILINGUAL_BACKUP))
        assertTrue("LITE_FALLBACK should be ready", bridge.isReady(BrainModelRole.LITE_FALLBACK))
        // Run Qwen 0.5B (lite) proof
        val fixture = loadLiteModelFixture()
        val controller = NativeProofProcessController(context)
        nativeController = controller
        val result = runProof(
            controller = controller,
            stage = NativeProofStage.READABLE_OUTPUT,
            modelId = "lite",
            modelFile = fixture.file,
            modelSha256 = fixture.sha256,
            prompt = "Reply with exactly: NOVA_BRAIN_OK",
            maxTokens = 12
        )
        assertSuccessfulStage("Case C (Lite)", result, fixture)
        // Final verification of routing
        val request = BrainRequest(rawText = "quick explain")
        val decision = bridge.selectLocalRoute(request, allowOnlineHelper = true)
        assertNotNull("Should have a local route decision", decision)
        assertEquals("Decision should select LITE_FALLBACK", BrainModelRole.LITE_FALLBACK, decision?.selectedRole)
    }
    @Test
    fun caseD_NoLocalModelReadyWhenAllOverridden() {
        Log.i(TAG, "Executing Case D: No models available")
        forceUnavailable(BrainModelRole.CORE_BRAIN)
        forceUnavailable(BrainModelRole.MULTILINGUAL_BACKUP)
        forceUnavailable(BrainModelRole.LITE_FALLBACK)
        // Assert Case D readiness
        assertFalse("CORE_BRAIN should NOT be ready", bridge.isReady(BrainModelRole.CORE_BRAIN))
        assertFalse("MULTILINGUAL_BACKUP should NOT be ready", bridge.isReady(BrainModelRole.MULTILINGUAL_BACKUP))
        assertFalse("LITE_FALLBACK should NOT be ready", bridge.isReady(BrainModelRole.LITE_FALLBACK))
        // Final verification of routing
        val request = BrainRequest(rawText = "Any request")
        val decision = bridge.selectLocalRoute(request, allowOnlineHelper = true)
        assertNull("Should NOT have a local route decision when all models are overridden", decision)
    }
    // --- Helpers ---
    private fun findGemmaModel(): File {
        // Path A: Existing test fixture path (GemmaLiteRTInferenceAndroidTest pattern)
        val testFixture = File(context.filesDir, "gemma.litertlm")
        if (testFixture.exists()) return testFixture
        // Path B: Production path from ModelInstallService
        val prodPath = modelInstallService.getReadyModelPath("core")
        if (prodPath != null) {
            val file = File(prodPath)
            if (file.exists()) return file
        }
        // Path C: Expected production path
        val expectedProd = File(context.filesDir, "model_install/models/core/gemma-3n-E2B-it-int4.litertlm")
        if (expectedProd.exists()) return expectedProd
        throw IllegalStateException("Gemma model not found. Searched: ${testFixture.absolutePath}, ${expectedProd.absolutePath}")
    }
    private fun loadFullModelFixture(): ModelFixture {
        val modelFile = File(context.filesDir, "model_install/models/full/qwen2.5-1.5b-instruct-q4_k_m.gguf")
        return loadModelFixture("full", modelFile, FULL_SHA256)
    }
    private fun loadLiteModelFixture(): ModelFixture {
        val modelFile = File(context.filesDir, "model_install/models/lite/qwen2.5-0.5b-instruct-q4_k_m.gguf")
        return loadModelFixture("lite", modelFile, LITE_SHA256)
    }
    private fun loadModelFixture(label: String, modelFile: File, expectedSha256: String): ModelFixture {
        assertTrue("Missing model file for $label: ${modelFile.absolutePath}", modelFile.exists())
        val sha256 = Sha256ModelVerifier().digestHex(modelFile)
        assertEquals("SHA256 mismatch for $label", expectedSha256.lowercase(), sha256.lowercase())
        return ModelFixture(modelFile, sha256)
    }
    private fun runProof(
        controller: NativeProofProcessController,
        stage: NativeProofStage,
        modelId: String,
        modelFile: File,
        modelSha256: String,
        prompt: String,
        maxTokens: Int
    ): NativeProofStageResult {
        return controller.runStage(
            stage = stage,
            modelId = modelId,
            debugOverridePath = modelFile.absolutePath,
            modelSha256 = modelSha256,
            prompt = prompt,
            maxTokens = maxTokens,
            stageTimeoutMs = 120_000L
        )
    }
    private fun assertSuccessfulStage(
        label: String,
        result: NativeProofStageResult,
        fixture: ModelFixture
    ) {
        assertTrue("Stage must succeed for $label: ${result.message}", result.success)
        assertFalse("Simulation must remain false for $label", result.simulation)
        assertTrue("Forward pass must execute for $label", result.realForwardPass)
        assertTrue("Must sample from logits for $label", result.sampledFromModelLogits)
        assertTrue("Must generate tokens for $label", result.tokensGenerated > 0)
        assertFalse("Decoded text must not be blank for $label", result.decodedText.isNullOrBlank())
        assertEquals("Model path mismatch for $label", fixture.file.absolutePath, result.modelPath)
        assertEquals("Model SHA mismatch for $label", fixture.sha256, result.modelSha256)
    }
    private data class ModelFixture(val file: File, val sha256: String)
}