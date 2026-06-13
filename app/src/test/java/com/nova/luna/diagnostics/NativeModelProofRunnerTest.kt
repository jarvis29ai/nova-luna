package com.nova.luna.diagnostics

import android.content.Context
import com.nova.luna.brain.NativeLlamaResult
import com.nova.luna.brain.NativeLlamaRuntime
import com.nova.luna.modelinstall.ModelInstallReason
import com.nova.luna.modelinstall.ModelInstallService
import com.nova.luna.modelinstall.ModelInstallSpec
import com.nova.luna.modelinstall.ModelInstallSpecRegistry
import com.nova.luna.modelinstall.ModelInstallVerifier
import com.nova.luna.modelinstall.ModelPackId
import com.nova.luna.modelinstall.ModelPathResolver
import com.nova.luna.modelinstall.ModelRuntimeStateStore
import com.nova.luna.modelinstall.PrivateAppModelStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File

class NativeModelProofRunnerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var storage: PrivateAppModelStorage
    private lateinit var runtimeStateStore: ModelRuntimeStateStore
    private lateinit var modelInstallService: ModelInstallService

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        storage = PrivateAppModelStorage.from(tempFolder.newFolder("app_files"))
        runtimeStateStore = ModelRuntimeStateStore(storage)
        modelInstallService = createServiceWithLiteSpec()
    }

    @Test
    fun `missing model returns honest tokenizer proof`() {
        val runner = NativeModelProofRunner(
            storage = storage,
            modelInstallService = modelInstallService,
            runtimeFactory = { FakeRuntime() }
        )

        val missingPath = File(tempFolder.root, "missing.gguf")
        val proof = runner.runTokenizerProof(
            modelId = ModelPackId.LITE.wireValue,
            sampleText = "open camera",
            debugOverridePath = missingPath.absolutePath
        )
        val expectedModelPath = File(storage.modelsDir(ModelPackId.LITE), defaultLiteSpec().expectedFileName)

        assertEquals(NativeProofStatus.MODEL_MISSING, proof.status)
        assertFalse(proof.modelFound)
        assertEquals(expectedModelPath.absolutePath, proof.modelPath)
        assertFalse(proof.tokenizerLoaded)
        assertEquals(0, proof.vocabSize)
        assertEquals("open camera", proof.sampleText)
        assertTrue(proof.sampleTokenIds.isEmpty())
        assertNull(proof.tokenizerError)
        assertNotNull(proof.instructionsForUser)
        assertTrue(proof.instructionsForUser!!.contains(expectedModelPath.absolutePath))
    }

    @Test
    fun `failed native call does not claim real inference`() {
        val modelFile = readyLiteModelFile()
        val runtime = FakeRuntime().apply {
            loadResult = true
            generateThrows = IllegalStateException("native explode")
        }
        val runner = NativeModelProofRunner(
            storage = storage,
            modelInstallService = modelInstallService,
            runtimeFactory = { runtime }
        )

        val proof = runner.runInferenceProof(
            modelId = ModelPackId.LITE.wireValue,
            promptText = """Return JSON only: {"actionType":"OPEN_CAMERA","riskLevel":"LOW"}""",
            debugOverridePath = modelFile.absolutePath
        )

        assertEquals(NativeProofStatus.ERROR, proof.status)
        assertTrue(proof.modelFound)
        assertEquals(modelFile.absolutePath, proof.modelPath)
        assertFalse(proof.realInference)
        assertEquals(0, proof.generatedTokenCount)
        assertNull(proof.decodedText)
        assertEquals("NATIVE_GGUF", proof.outputSource)
        assertFalse(proof.simulation)
        assertNotNull(proof.inferenceError)
        assertTrue(proof.inferenceError!!.contains("native explode", ignoreCase = true))
    }

    @Test
    fun `successful mocked native adapter validates wrapper behavior`() {
        val modelFile = readyLiteModelFile()
        val runtime = FakeRuntime().apply {
            loadResult = true
            generateResult = fakeSuccessResult(
                prompt = """Return JSON only: {"actionType":"OPEN_CAMERA","riskLevel":"LOW"}"""
            )
        }
        val runner = NativeModelProofRunner(
            storage = storage,
            modelInstallService = modelInstallService,
            runtimeFactory = { runtime }
        )

        val tokenizerProof = runner.runTokenizerProof(
            modelId = ModelPackId.LITE.wireValue,
            sampleText = "open camera",
            debugOverridePath = modelFile.absolutePath
        )
        val inferenceProof = runner.runInferenceProof(
            modelId = ModelPackId.LITE.wireValue,
            promptText = """Return JSON only: {"actionType":"OPEN_CAMERA","riskLevel":"LOW"}""",
            debugOverridePath = modelFile.absolutePath
        )

        assertEquals(NativeProofStatus.READY, tokenizerProof.status)
        assertTrue(tokenizerProof.tokenizerLoaded)
        assertTrue(tokenizerProof.sampleTokenIds.isNotEmpty())
        assertTrue(tokenizerProof.vocabSize > 0)
        assertEquals("open camera", tokenizerProof.sampleText)

        assertEquals(NativeProofStatus.READY, inferenceProof.status)
        assertTrue(inferenceProof.realInference)
        assertTrue(inferenceProof.generatedTokenCount > 0)
        assertNotNull(inferenceProof.decodedText)
        assertTrue(inferenceProof.decodedText!!.isNotBlank())
        assertEquals("NATIVE_GGUF", inferenceProof.outputSource)
        assertFalse(inferenceProof.simulation)
        assertTrue(inferenceProof.promptTokenIds.isNotEmpty())
        assertTrue(inferenceProof.generatedTokenIds.isNotEmpty())
    }

    private fun createServiceWithLiteSpec(): ModelInstallService {
        val spec = defaultLiteSpec().copy(
            minimumBytes = 1,
            expectedSha256 = null
        )
        val registry = ModelInstallSpecRegistry(customSpecs = listOf(spec))
        return ModelInstallService(
            specRegistry = registry,
            pathResolver = ModelPathResolver(context, storage, runtimeStateStore),
            verifier = ModelInstallVerifier(),
            runtimeStateStore = runtimeStateStore,
            storage = storage
        )
    }

    private fun defaultLiteSpec(): ModelInstallSpec {
        return ModelInstallSpecRegistry().LITE_FALLBACK
    }

    private fun readyLiteModelFile(): File {
        val modelDir = storage.modelsDir(ModelPackId.LITE)
        val modelFile = File(modelDir, defaultLiteSpec().expectedFileName)
        modelFile.parentFile?.mkdirs()
        modelFile.writeBytes(ByteArray(16))
        return modelFile
    }

    private fun fakeSuccessResult(prompt: String): NativeLlamaResult {
        return NativeLlamaResult(
            text = """{"intent":"open_camera","reply":"Opening camera","actionType":"OPEN_CAMERA","riskLevel":"low","requiresConfirmation":false,"finalActionAllowed":true,"params":{"app":"camera"}}""",
            success = true,
            errorMessage = null,
            latencyMillis = 120,
            tokensGenerated = 4,
            promptTokens = 3,
            modelLoadMs = 8,
            loadMs = 8,
            promptEvalMs = 12,
            generationMs = 100,
            contextSize = 2048,
            threadsUsed = 4,
            backendType = "native",
            backend = "native",
            modelArch = "qwen2",
            vocabSize = 151936,
            tensorsLoaded = 291,
            modelDetected = true,
            realTokenIds = true,
            realInference = true,
            nativeGenerationAvailable = true,
            metadataParsed = true,
            tokenizerLoaded = true,
            tensorsWeightLoaded = true,
            ggmlGraphCompute = true,
            logitsGenerated = true,
            samplingActive = false,
            deterministicResponse = true,
            memoryEstimateBytes = 350L * 1024L * 1024L,
            tokensPerSecond = 4.0,
            nativeBridgeStable = true,
            jsonReturnBridge = true,
            crashFree = true,
            tokenizerType = "bpe",
            bosTokenId = 151643,
            eosTokenId = 151645,
            specialTokensCount = 10,
            tokenizationSuccess = true,
            tokenizerLoadMs = 5,
            ggmlGraphBuilt = true,
            graphNodesCount = 724,
            memoryMappedMb = 384,
            logitsFromModelWeights = true,
            decodedTokens = true,
            sampledTokenIdsCount = 4,
            simulation = false,
            decodedText = """{"intent":"open_camera","reply":"Opening camera","actionType":"OPEN_CAMERA","riskLevel":"low","requiresConfirmation":false,"finalActionAllowed":true,"params":{"app":"camera"}}""",
            errorCode = null,
            message = null,
            simulationActive = false,
            upstreamSamplerLinked = true,
            tokenIdsPreview = "[151643, 482, 991]",
            tokenizationOk = true,
            lastError = null,
            lastFailure = null,
            modelLoaded = true,
            modelReused = false,
            modelLoadCount = 1,
            generationCallCount = 1,
            promptText = prompt,
            promptTokenIdsSample = listOf(151643, 482, 991),
            generatedTokenIdsSample = listOf(101, 102, 103, 104),
            jsonParseAttempted = true,
            jsonParseSuccess = true,
            parsedIntent = "open_camera",
            parsedRiskLevel = "low",
            finishReason = "stop"
        )
    }

    private class FakeRuntime : NativeLlamaRuntime {
        var loadResult: Boolean = true
        var generateResult: NativeLlamaResult? = null
        var generateThrows: Throwable? = null

        override fun loadModel(modelFile: File): Boolean = loadResult

        override fun generate(prompt: String, timeoutMs: Long): NativeLlamaResult {
            generateThrows?.let { throw it }
            return generateResult ?: NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Not configured",
                backendType = "native",
                backend = "native"
            )
        }

        override fun isLoaded(): Boolean = loadResult

        override fun unload() = Unit

        override fun diagnostics(): String = "fake-runtime"
    }
}
