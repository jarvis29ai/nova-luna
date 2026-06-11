package com.nova.luna.brain

import com.nova.luna.model.BrainModelCatalog
import com.nova.luna.model.BrainModelRole
import com.nova.luna.model.BrainRouteDecision
import com.nova.luna.modelinstall.ModelInstallService
import com.nova.luna.modelinstall.ModelInstallStatus
import com.nova.luna.modelinstall.ModelRuntimeStatus
import com.nova.luna.modelinstall.ModelRuntimeState
import com.nova.luna.modelinstall.ModelRuntimeStateStore
import com.nova.luna.modelinstall.ModelPathResolver
import com.nova.luna.modelinstall.ModelInstallVerifier
import com.nova.luna.modelinstall.ModelInstallSpecRegistry
import com.nova.luna.modelinstall.PrivateAppModelStorage
import com.nova.luna.modelinstall.ModelPackId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class BrainPhase22MultiModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: PrivateAppModelStorage
    private lateinit var modelInstallService: ModelInstallService
    private lateinit var runtimeLoader: ModelRuntimeLoader
    private lateinit var ramInfoProvider: FakeRamInfoProvider
    private lateinit var ramGuard: ModelRamGuard
    private lateinit var runtimeManager: ModelRuntimeManager
    private lateinit var bridge: com.nova.luna.modelinstall.ModelInstallBrainRouterBridge
    private lateinit var brainService: BrainService

    @Before
    fun setup() {
        val rootDir = tempFolder.newFolder("nova_luna_storage")
        storage = PrivateAppModelStorage.from(rootDir)
        
        val stateStore = ModelRuntimeStateStore(storage)
        val context = mock(android.content.Context::class.java)
        val pathResolver = ModelPathResolver(context, storage, stateStore)
        val verifier = ModelInstallVerifier()
        
        val testSpecs = listOf(
            com.nova.luna.modelinstall.ModelInstallSpec(
                modelId = "core",
                displayName = "Core Brain",
                role = "BRAIN",
                expectedFileName = "gemma-3n-E2B-it-int4.litertlm",
                expectedSha256 = null,
                minimumBytes = 1,
                preferredInstallDirName = "core",
                allowedExtensions = listOf(".gguf", ".bin", ".litertlm")
            ),
            com.nova.luna.modelinstall.ModelInstallSpec(
                modelId = "lite",
                displayName = "Lightweight Fallback",
                role = "LITE_FALLBACK",
                expectedFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                expectedSha256 = null,
                minimumBytes = 1,
                preferredInstallDirName = "lite",
                allowedExtensions = listOf(".gguf", ".bin")
            ),
            com.nova.luna.modelinstall.ModelInstallSpec(
                modelId = "full",
                displayName = "Multilingual Backup",
                role = "MULTILINGUAL_BACKUP",
                expectedFileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                expectedSha256 = null,
                minimumBytes = 1,
                preferredInstallDirName = "full",
                allowedExtensions = listOf(".gguf", ".bin")
            )
        )
        val specRegistry = com.nova.luna.modelinstall.ModelInstallSpecRegistry(testSpecs)
        
        modelInstallService = ModelInstallService(
            specRegistry = specRegistry,
            pathResolver = pathResolver,
            verifier = verifier,
            runtimeStateStore = stateStore,
            storage = storage
        )
        
        runtimeLoader = ModelRuntimeLoader(storage, modelInstallService)
        ramInfoProvider = FakeRamInfoProvider(_availableRamMb = 8192) // Lots of RAM
        ramGuard = ModelRamGuard(ramInfoProvider)
        runtimeManager = ModelRuntimeManager(runtimeLoader, ramGuard)
        bridge = com.nova.luna.modelinstall.ModelInstallBrainRouterBridge(modelInstallService)
        
        brainService = BrainService(
            localBrainRouterBridge = bridge,
            modelInstallService = modelInstallService,
            modelRuntimeManager = runtimeManager
        )
        
        // Seed models using the same specRegistry
        testSpecs.forEach { spec ->
            val packId = ModelPackId.fromWireValue(spec.modelId) ?: ModelPackId.CORE
            val dir = storage.modelsDir(packId).apply { mkdirs() }
            val file = File(dir, spec.expectedFileName)
            file.writeBytes(ByteArray(10))
            modelInstallService.saveVerifiedModelPath(spec.modelId, file.absolutePath)
        }
    }

    private fun seedModel(modelId: String, size: Long) {
        // Redundant with setup() loop but kept for compatibility
    }

    @Test
    fun `CORE_BRAIN selected for complex English request`() {
        assertTrue("CORE_BRAIN should be ready", bridge.isReady(BrainModelRole.CORE_BRAIN))
        assertTrue("LITE_FALLBACK should be ready", bridge.isReady(BrainModelRole.LITE_FALLBACK))
        assertTrue("MULTILINGUAL_BACKUP should be ready", bridge.isReady(BrainModelRole.MULTILINGUAL_BACKUP))

        val request = "Plan my day and summarize what I should do first"
        val diagnostics = brainService.diagnose(request)
        
        println("DEBUG: CORE_BRAIN selectedRole=${diagnostics.routeDecision?.selectedRole}")
        println("DEBUG: CORE_BRAIN reason=${diagnostics.routeDecision?.reason}")
        println("DEBUG: CORE_BRAIN sessionTrace.selectedRole=${diagnostics.sessionTrace?.selectedRole}")
        println("DEBUG: CORE_BRAIN sessionTrace.fallbackReason=${diagnostics.sessionTrace?.fallbackReason}")

        assertEquals(BrainModelRole.CORE_BRAIN, diagnostics.routeDecision?.selectedRole)
        assertEquals(BrainModelRole.CORE_BRAIN, diagnostics.sessionTrace?.selectedRole)
    }

    @Test
    fun `LITE_FALLBACK selected for simple command`() {
        val request = "open camera"
        val diagnostics = brainService.diagnose(request)
        
        assertEquals(BrainModelRole.LITE_FALLBACK, diagnostics.routeDecision?.selectedRole)
        assertEquals(BrainModelRole.LITE_FALLBACK, diagnostics.sessionTrace?.selectedRole)
    }

    @Test
    fun `MULTILINGUAL_BACKUP selected for Hindi Hinglish`() {
        // Hindi text (Devanagari)
        val request1 = "नमस्ते, आप कैसे हैं?"
        val diagnostics1 = brainService.diagnose(request1)
        assertEquals(BrainModelRole.MULTILINGUAL_BACKUP, diagnostics1.routeDecision?.selectedRole)

        // Hinglish text
        val request2 = "camera kholo mujhe photo leni hai"
        val diagnostics2 = brainService.diagnose(request2)
        assertEquals(BrainModelRole.MULTILINGUAL_BACKUP, diagnostics2.routeDecision?.selectedRole)
    }

    @Test
    fun `Fallback when preferred role is missing`() {
        // Remove multilingual model
        val spec = ModelInstallSpecRegistry().getSpec("full")!!
        val file = File(storage.modelsDir(ModelPackId.FULL), spec.expectedFileName)
        file.delete()
        modelInstallService.clearBrokenModelPath("full")
        
        val request = "कृपया मुझे समझाओ" // Preferred: MULTILINGUAL_BACKUP
        val diagnostics = brainService.diagnose(request)
        
        // Should fall back to CORE_BRAIN (since it's ready)
        assertEquals(BrainModelRole.CORE_BRAIN, diagnostics.routeDecision?.selectedRole)
        assertTrue(diagnostics.routeDecision?.reason?.contains("next ready local fallback") == true)
    }

    @Test
    fun `RAM guard blocks core and uses lite`() {
        // Set RAM enough for LITE (2GB) but not for CORE (4GB)
        ramInfoProvider._availableRamMb = 3000
        
        val request = "Summarize this very long document and provide key takeaways" // Complex -> Preferred: CORE_BRAIN
        val diagnostics = brainService.diagnose(request)
        
        assertEquals(BrainModelRole.LITE_FALLBACK, diagnostics.sessionTrace?.selectedRole)
        assertEquals("BLOCK_USE_LITE_FALLBACK", diagnostics.sessionTrace?.ramGuardDecision)
        assertTrue(diagnostics.sessionTrace?.fallbackUsed == true)
    }

    @Test
    fun `Switching unloads previous model`() {
        // 1. Load CORE_BRAIN (needs > 8 words to be complex)
        brainService.diagnose("Please help me plan my day and organize my tasks")
        assertEquals(BrainModelRole.CORE_BRAIN, runtimeManager.getCurrentLoadedRole())
        
        // 2. Load LITE_FALLBACK
        brainService.diagnose("open camera")
        assertEquals(BrainModelRole.LITE_FALLBACK, runtimeManager.getCurrentLoadedRole())
        
        // 3. Verify counts
        val trace = brainService.diagnose("open camera").sessionTrace!!
        assertTrue("Should have at least one unload", trace.unloadCount > 0)
        assertTrue("Should have at least one load", trace.loadCount > 0)
    }

    @Test
    fun `Reuse same loaded model`() {
        // 1. Load CORE_BRAIN
        brainService.diagnose("Please help me plan my day and organize my tasks 1")
        val trace1 = brainService.diagnose("Please help me plan my day and organize my tasks 1").sessionTrace!!
        val loadCount1 = trace1.loadCount
        
        // 2. Load CORE_BRAIN again
        val diagnostics2 = brainService.diagnose("Please help me plan my day and organize my tasks 2")
        val trace2 = diagnostics2.sessionTrace!!
        
        assertEquals(loadCount1, trace2.loadCount)
        assertTrue(trace2.reusedLoadedModel)
        assertFalse(trace2.switched)
    }

    @Test
    fun `No ready model returns honest failure`() {
        // Delete all models
        modelInstallService.clearBrokenModelPath("core")
        modelInstallService.clearBrokenModelPath("lite")
        modelInstallService.clearBrokenModelPath("full")
        
        // Physically delete files too
        storage.rootDir.deleteRecursively()
        storage.rootDir.mkdirs()

        val request = "Plan my day and summarize what I should do first"
        val diagnostics = brainService.diagnose(request)
        
        assertEquals(BrainModelRole.MOCK_FALLBACK, diagnostics.routeDecision?.selectedRole)
        assertTrue(diagnostics.routeDecision?.reason?.contains("AI brain is not installed yet") == true)
    }
}
