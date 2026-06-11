package com.nova.luna.modelinstall

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.io.FileOutputStream

class ModelInstallServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var storage: PrivateAppModelStorage
    private lateinit var runtimeStateStore: ModelRuntimeStateStore
    private lateinit var pathResolver: ModelPathResolver
    private lateinit var verifier: ModelInstallVerifier
    private lateinit var service: ModelInstallService
    private lateinit var specRegistry: ModelInstallSpecRegistry

    @Before
    fun setup() {
        context = mock(Context::class.java)
        val rootDir = tempFolder.newFolder("app_internal")
        storage = PrivateAppModelStorage.from(rootDir)
        
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
    fun `missing model returns ready=false and MODEL_FILE_MISSING`() {
        val state = service.getInstallState("core")
        assertFalse(state.ready)
        assertEquals(ModelInstallReason.MODEL_FILE_MISSING, state.reason)
    }

    @Test
    fun `valid model file returns ready=true`() {
        val spec = specRegistry.getSpec("core")!!
        val modelDir = tempFolder.newFolder("models", "core")
        val modelFile = File(modelDir, spec.expectedFileName)
        
        // Use a spec with smaller minimum size for test
        val testSpec = spec.copy(minimumBytes = 100)
        val smallService = createServiceWithSpec(testSpec)
        
        modelFile.writeBytes(ByteArray(200))
        
        val state = smallService.getInstallState("core", debugOverridePath = modelFile.absolutePath)
        assertTrue(state.ready)
        assertEquals(ModelInstallReason.MODEL_READY_DEV_SHA_MISSING, state.reason)
    }

    @Test
    fun `too-small model returns MODEL_FILE_TOO_SMALL`() {
        val spec = specRegistry.getSpec("core")!!
        val modelFile = tempFolder.newFile(spec.expectedFileName)
        modelFile.writeBytes(ByteArray(100)) // Spec requires 100MB
        
        val state = service.getInstallState("core", debugOverridePath = modelFile.absolutePath)
        assertFalse(state.ready)
        assertEquals(ModelInstallReason.MODEL_FILE_TOO_SMALL, state.reason)
    }

    @Test
    fun `wrong extension returns MODEL_EXTENSION_NOT_ALLOWED`() {
        val spec = specRegistry.getSpec("core")!!
        val modelFile = tempFolder.newFile("model.txt")
        modelFile.writeBytes(ByteArray(100))
        
        // Custom service with small min size
        val testSpec = spec.copy(minimumBytes = 10, allowedExtensions = listOf(".gguf"))
        val smallService = createServiceWithSpec(testSpec)
        
        val state = smallService.getInstallState("core", debugOverridePath = modelFile.absolutePath)
        assertFalse(state.ready)
        assertEquals(ModelInstallReason.MODEL_EXTENSION_NOT_ALLOWED, state.reason)
    }

    @Test
    fun `SHA mismatch returns MODEL_SHA_MISMATCH`() {
        val spec = specRegistry.getSpec("core")!!
        val testSpec = spec.copy(minimumBytes = 10, expectedSha256 = "expected_hash")
        val smallService = createServiceWithSpec(testSpec)
        
        val modelFile = tempFolder.newFile(spec.expectedFileName)
        modelFile.writeBytes("wrong content".toByteArray())
        
        val state = smallService.getInstallState("core", debugOverridePath = modelFile.absolutePath)
        assertFalse(state.ready)
        assertEquals(ModelInstallReason.MODEL_SHA_MISMATCH, state.reason)
    }

    @Test
    fun `repairModelPath fixes broken saved path if valid default file exists`() {
        val spec = specRegistry.getSpec("core")!!
        val testSpec = spec.copy(minimumBytes = 10)
        val smallService = createServiceWithSpec(testSpec)
        
        // 1. Setup a valid model in default internal location
        val internalDir = storage.modelsDir(ModelPackId.CORE)
        internalDir.mkdirs()
        val internalFile = File(internalDir, spec.expectedFileName)
        internalFile.writeBytes(ByteArray(20))
        
        // 2. Setup a broken saved path in runtimeStateStore
        val brokenPath = "/non/existent/path/model.gguf"
        runtimeStateStore.upsert(ModelRuntimeState(
            packId = ModelPackId.CORE,
            version = "1.0.0",
            displayName = spec.displayName,
            runtimeStatus = ModelRuntimeStatus.READY,
            installState = ModelInstallStatus.READY,
            ready = true,
            runtimeLoaded = true,
            healthCheckPassed = true,
            expectedFileCount = 1,
            verifiedFileCount = 1,
            modelRootPath = brokenPath
        ))
        
        // 3. Verify it's considered READY via fallback even if stored path is broken
        assertTrue("Fallback should find it", smallService.getInstallState("core").ready)
        assertEquals("Should have found it in internal storage", internalFile.absolutePath, smallService.getInstallState("core").resolvedPath)
        
        // 4. Repair explicitly
        val state = smallService.repairModelPath("core")
        assertTrue(state.ready)
        assertEquals(ModelInstallReason.MODEL_REPAIR_SUCCESS, state.reason)
        assertEquals(internalFile.absolutePath, state.resolvedPath)
        
        // 5. Verify it's now saved correctly in stateStore
        assertEquals(internalFile.absolutePath, runtimeStateStore.find(ModelPackId.CORE)?.modelRootPath)
    }

    @Test
    fun `importFromLocalFile copies valid file and saves verified path`() {
        val spec = specRegistry.getSpec("core")!!
        val testSpec = spec.copy(minimumBytes = 10)
        val smallService = createServiceWithSpec(testSpec)
        
        val sourceFile = tempFolder.newFile("source.gguf")
        sourceFile.writeBytes(ByteArray(50))
        
        val state = smallService.importFromLocalFile("core", sourceFile.absolutePath)
        assertTrue(state.ready)
        
        val targetFile = File(storage.modelsDir(ModelPackId.CORE), spec.expectedFileName)
        assertTrue(targetFile.exists())
        assertEquals(50, targetFile.length())
        assertEquals(targetFile.absolutePath, state.resolvedPath)
        assertEquals(targetFile.absolutePath, runtimeStateStore.find(ModelPackId.CORE)?.modelRootPath)
    }

    private fun createServiceWithSpec(spec: ModelInstallSpec): ModelInstallService {
        val customRegistry = mock(ModelInstallSpecRegistry::class.java)
        `when`(customRegistry.getSpec(spec.modelId)).thenReturn(spec)
        return ModelInstallService(
            specRegistry = customRegistry,
            pathResolver = pathResolver,
            verifier = verifier,
            runtimeStateStore = runtimeStateStore,
            storage = storage
        )
    }
}
