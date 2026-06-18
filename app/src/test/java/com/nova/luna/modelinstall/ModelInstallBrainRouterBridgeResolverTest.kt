package com.nova.luna.modelinstall
import android.content.Context
import com.nova.luna.brain.FailoverOverrideMarkers
import com.nova.luna.model.BrainModelRole
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File
class ModelInstallBrainRouterBridgeResolverTest {
    @get:Rule
    val tempFolder = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var storage: PrivateAppModelStorage
    private lateinit var runtimeStateStore: ModelRuntimeStateStore
    private lateinit var pathResolver: ModelPathResolver
    private lateinit var verifier: ModelInstallVerifier
    private lateinit var service: ModelInstallService
    private lateinit var specRegistry: ModelInstallSpecRegistry
    private lateinit var filesDir: File
    @Before
    fun setup() {
        context = mock(Context::class.java)
        filesDir = tempFolder.newFolder("app_internal")
        storage = PrivateAppModelStorage.from(filesDir)
        runtimeStateStore = ModelRuntimeStateStore(storage)
        pathResolver = ModelPathResolver(context, storage, runtimeStateStore)
        verifier = ModelInstallVerifier()
        specRegistry = ModelInstallSpecRegistry()
        service = ModelInstallService(
            specRegistry = specRegistry,
            pathResolver = pathResolver,
            verifier = verifier,
            runtimeStateStore = runtimeStateStore,
            storage = storage
        )
    }
    @Test
    fun `Official path takes priority over app-private recovery path`() {
        val modelId = "core"
        val spec = specRegistry.getSpec(modelId)!!
        // 1. Create a valid file in app-private recovery location
        val recoveryDir = storage.modelsDir(ModelPackId.CORE)
        recoveryDir.mkdirs()
        val recoveryFile = File(recoveryDir, spec.expectedFileName)
        recoveryFile.writeBytes(ByteArray(spec.minimumBytes.toInt() + 1024))
        // 2. Create a valid file in a "custom" location and register it as official
        val customDir = tempFolder.newFolder("custom_models")
        val customFile = File(customDir, spec.expectedFileName)
        customFile.writeBytes(ByteArray(spec.minimumBytes.toInt() + 2048))
        runtimeStateStore.upsert(ModelRuntimeState(
            packId = ModelPackId.CORE,
            version = "1.0.0",
            displayName = spec.displayName,
            runtimeStatus = ModelRuntimeStatus.READY,
            installState = ModelInstallStatus.READY,
            ready = true,
            modelRootPath = customFile.absolutePath
        ))
        val bridge = ModelInstallBrainRouterBridge(service, filesDir = filesDir)
        // Should be ready
        assertTrue(bridge.isReady(BrainModelRole.CORE_BRAIN))
        // Official service should return custom path
        assertEquals(customFile.absolutePath, service.getReadyModelPath(modelId))
    }
    @Test
    fun `App-private model file resolves to ready without a separately cached runtime state`() {
        // Uses "full" (always-null expectedSha256), not "lite" — the lite spec's
        // expectedSha256 comes from BuildConfig.NOVA_LUNA_LIGHTWEIGHT_MODEL_SHA256 and is
        // non-blank in this project, so a fake zero-filled file would correctly fail real
        // checksum verification there. This test targets the dev-mode-without-sha path.
        val modelId = "full"
        val spec = specRegistry.getSpec(modelId)!!
        // Create valid file in app-private recovery location
        val recoveryDir = storage.modelsDir(ModelPackId.FULL)
        recoveryDir.mkdirs()
        val recoveryFile = File(recoveryDir, spec.expectedFileName)
        recoveryFile.writeBytes(ByteArray(spec.minimumBytes.toInt() + 1024))
        val bridge = ModelInstallBrainRouterBridge(service, filesDir = filesDir)
        // Bridge should be ready because repair/recovery (or Tier C resolution) finds it
        assertTrue(bridge.isReady(BrainModelRole.MULTILINGUAL_BACKUP))
    }
    @Test
    fun `Missing file in both locations resolves as not ready`() {
        val bridge = ModelInstallBrainRouterBridge(service, filesDir = filesDir)
        assertFalse(bridge.isReady(BrainModelRole.CORE_BRAIN))
    }
    @Test
    fun `File under wrong role path does not make other role ready`() {
        val coreSpec = specRegistry.getSpec("core")!!
        // Put a "core" named file under "lite" directory
        val liteDir = storage.modelsDir(ModelPackId.LITE)
        liteDir.mkdirs()
        val wrongFile = File(liteDir, coreSpec.expectedFileName)
        wrongFile.writeBytes(ByteArray(coreSpec.minimumBytes.toInt() + 1024))
        val bridge = ModelInstallBrainRouterBridge(service, filesDir = filesDir)
        // Core should NOT be ready (wrong directory)
        assertFalse(bridge.isReady(BrainModelRole.CORE_BRAIN))
        // Lite should NOT be ready (wrong filename)
        assertFalse(bridge.isReady(BrainModelRole.LITE_FALLBACK))
    }
    @Test
    fun `Too small file fails verification and resolves as not ready`() {
        val spec = specRegistry.getSpec("core")!!
        val recoveryDir = storage.modelsDir(ModelPackId.CORE)
        recoveryDir.mkdirs()
        val recoveryFile = File(recoveryDir, spec.expectedFileName)
        // Write very small content, spec expects much more
        recoveryFile.writeBytes(ByteArray(100))
        val bridge = ModelInstallBrainRouterBridge(service, filesDir = filesDir)
        assertFalse(bridge.isReady(BrainModelRole.CORE_BRAIN))
    }
    @Test
    fun `FailoverOverrideMarkers mapping is correct`() {
        assertEquals("force_core_off", FailoverOverrideMarkers.markerFileName(BrainModelRole.CORE_BRAIN))
        assertEquals("force_full_off", FailoverOverrideMarkers.markerFileName(BrainModelRole.MULTILINGUAL_BACKUP))
        assertEquals("force_lite_off", FailoverOverrideMarkers.markerFileName(BrainModelRole.LITE_FALLBACK))
        assertNull(FailoverOverrideMarkers.markerFileName(BrainModelRole.GEMMA_REASONING))
        assertNull(FailoverOverrideMarkers.markerFileName(BrainModelRole.ACTION_JSON))
    }
    @Test
    fun `Forced-unavailable marker prevents readiness even if file is valid`() {
        val spec = specRegistry.getSpec("core")!!
        val recoveryDir = storage.modelsDir(ModelPackId.CORE)
        recoveryDir.mkdirs()
        val recoveryFile = File(recoveryDir, spec.expectedFileName)
        recoveryFile.writeBytes(ByteArray(spec.minimumBytes.toInt() + 1024))
        val bridge = ModelInstallBrainRouterBridge(service, filesDir = filesDir)
        // Initially ready
        assertTrue(bridge.isReady(BrainModelRole.CORE_BRAIN))
        // Write marker
        val markerFile = File(filesDir, "force_core_off")
        markerFile.writeText("")
        // Now NOT ready
        assertFalse(bridge.isReady(BrainModelRole.CORE_BRAIN))
        // Delete marker
        markerFile.delete()
        // Ready again
        assertTrue(bridge.isReady(BrainModelRole.CORE_BRAIN))
    }
    @Test
    fun `Forcing unavailable does not fabricate readiness`() {
        val bridge = ModelInstallBrainRouterBridge(service, filesDir = filesDir)
        // Not ready initially (no model file)
        assertFalse(bridge.isReady(BrainModelRole.CORE_BRAIN))
        // Marker absence (default state) - still not ready
        assertFalse(bridge.isReady(BrainModelRole.CORE_BRAIN))
    }
}