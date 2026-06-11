package com.nova.luna.brain

import com.nova.luna.model.BrainModelRole
import com.nova.luna.modelinstall.PrivateAppModelStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Phase18NativeLlamaIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: PrivateAppModelStorage
    private lateinit var liteModelFile: File
    private lateinit var fakeNativeRuntime: FakeNativeLlamaRuntime

    class FakeNativeLlamaRuntime : NativeLlamaRuntime {
        var loadSuccess = true
        var loaded = false
        var lastPrompt: String? = null
        var generateCalls = 0
        var generateHandler: ((String, Long) -> NativeLlamaResult)? = null

        override fun loadModel(modelFile: File): Boolean {
            loaded = loadSuccess
            return loadSuccess
        }

        override fun generate(prompt: String, timeoutMs: Long): NativeLlamaResult {
            lastPrompt = prompt
            generateCalls += 1
            return generateHandler?.invoke(prompt, timeoutMs) ?: proofOnlyResult(prompt)
        }

        fun proofOnlyResult(prompt: String): NativeLlamaResult {
            val preview = if (prompt.contains("camera", ignoreCase = true)) {
                "[151643, 482, 991]"
            } else {
                "[151643, 483, 992]"
            }

            return NativeLlamaResult(
                text = null,
                success = false,
                errorMessage = "Tokenizer is available, but real native generation is not implemented yet.",
                latencyMillis = 1500,
                tokensGenerated = 0,
                promptTokens = 12,
                modelLoadMs = 50,
                promptEvalMs = 120,
                contextSize = 2048,
                threadsUsed = 4,
                backendType = "native",
                backend = "native",
                modelArch = "qwen2",
                vocabSize = 151936,
                tensorsLoaded = 291,
                modelDetected = true,
                realTokenIds = true,
                realInference = false,
                nativeGenerationAvailable = false,
                metadataParsed = true,
                tokenizerLoaded = true,
                tensorsWeightLoaded = true,
                ggmlGraphCompute = true,
                logitsGenerated = true,
                samplingActive = false,
                deterministicResponse = true,
                tokensPerSecond = 0.0,
                memoryEstimateBytes = 350L * 1024L * 1024L,
                tokenizerType = "bpe",
                bosTokenId = 151643,
                eosTokenId = 151645,
                specialTokensCount = 10,
                tokenizationSuccess = true,
                tokenizationOk = true,
                tokenizerLoadMs = 10L,
                ggmlGraphBuilt = true,
                graphNodesCount = 724,
                memoryMappedMb = 384,
                logitsFromModelWeights = true,
                decodedTokens = true,
                sampledTokenIdsCount = 0,
                simulation = false,
                decodedText = null,
                errorCode = "REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED",
                message = "Tokenizer is available, but real native generation is not implemented yet.",
                simulationActive = false,
                upstreamSamplerLinked = false,
                tokenIdsPreview = preview,
                lastError = "REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED",
                lastFailure = "Tokenizer is available, but real native generation is not implemented yet."
            )
        }

        override fun isLoaded(): Boolean = loaded
        override fun unload() { loaded = false }
        override fun diagnostics(): String = "library_loaded=$libraryLoaded, loaded=$loaded"

        companion object {
            private const val libraryLoaded = true
        }
    }

    @Before
    fun setup() {
        val rootDir = tempFolder.newFolder("nova_luna_storage")
        storage = PrivateAppModelStorage.from(rootDir)

        val liteDir = File(rootDir, "model_install/models/lite").apply { mkdirs() }
        liteModelFile = File(liteDir, PhoneLocalLlmModelId.QWEN_0_5B.defaultQuantizedFileName)
        liteModelFile.writeText("GGUF-magic-content")

        fakeNativeRuntime = FakeNativeLlamaRuntime()
    }

    @Test
    fun `native bridge surfaces tokenizer proof but never fake success`() {
        val runtime = LiteLocalModelRuntime(
            modelFile = liteModelFile,
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("open camera", 5000)

        assertFalse(result.success)
        assertEquals(PhoneLocalLlmStatus.REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED, result.status)
        assertNull(result.text)
        assertTrue(result.reason.contains("Tokenizer is available", ignoreCase = true))
        assertEquals(1, fakeNativeRuntime.generateCalls)

        val diag = runtime.diagnostics()
        assertTrue("Model should be detected, was: $diag", diag.contains("model_detected=true"))
        assertTrue("Tokenizer should be loaded, was: $diag", diag.contains("tokenizer_loaded=true"))
        assertTrue("Vocab size should be surfaced, was: $diag", diag.contains("vocab_size=151936"))
        assertTrue("Tokenization proof should be true, was: $diag", diag.contains("tokenization_ok=true"))
        assertTrue("Prompt token count should be surfaced, was: $diag", diag.contains("prompt_tokens=12"))
        assertTrue("Real token IDs proof should be true, was: $diag", diag.contains("real_token_ids=true"))
        assertTrue("Real inference must stay false, was: $diag", diag.contains("real_inference=false"))
        assertTrue("Simulation must stay false, was: $diag", diag.contains("simulation=false"))
        assertTrue("Native generation must stay unavailable, was: $diag", diag.contains("native_generation_available=false"))
        assertTrue("Tokens generated must stay zero, was: $diag", diag.contains("tokens_generated=0"))
        assertTrue("Decoded text must stay empty, was: $diag", diag.contains("decoded_text=none"))
        assertTrue("Backend should stay native, was: $diag", diag.contains("backend=native"))
        assertTrue("Error code should be honest, was: $diag", diag.contains("error=REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED"))
    }

    @Test
    fun `fake success blobs are still rejected as unavailable`() {
        fakeNativeRuntime.generateHandler = { prompt, _ ->
            fakeNativeRuntime.proofOnlyResult(prompt).copy(
                success = true,
                text = """{"intent":"open_app","reply":"Opening camera via tokenizer pipeline","actionType":"EXTERNAL_ACTION","riskLevel":"safe","requiresConfirmation":false,"finalActionAllowed":true,"params":{"app":"camera"}}""",
                decodedText = """{"intent":"open_app","reply":"Opening camera via tokenizer pipeline","actionType":"EXTERNAL_ACTION","riskLevel":"safe","requiresConfirmation":false,"finalActionAllowed":true,"params":{"app":"camera"}}""",
                nativeGenerationAvailable = false,
                simulation = true,
                simulationActive = true,
                realInference = false,
                errorMessage = "Simulated native success",
                errorCode = "REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED",
                message = "Tokenizer is available, but real native generation is not implemented yet.",
                lastError = "REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED",
                lastFailure = "Tokenizer is available, but real native generation is not implemented yet."
            )
        }

        val runtime = LiteLocalModelRuntime(
            modelFile = liteModelFile,
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("open camera", 5000)

        assertFalse(result.success)
        assertNull(result.text)

        val diag = runtime.diagnostics()
        assertTrue("Fake success must not be surfaced, was: $diag", diag.contains("real_inference=false"))
        assertTrue("Fake success must not be surfaced, was: $diag", diag.contains("simulation=false"))
        assertTrue("Fake success must not be surfaced, was: $diag", diag.contains("native_generation_available=false"))
        assertTrue("Fake success must not be surfaced, was: $diag", diag.contains("decoded_text=none"))
        assertTrue("Fake success must not be surfaced, was: $diag", diag.contains("tokens_generated=0"))
    }

    @Test
    fun `native json parser accepts honest not-ready diagnostics`() {
        val honestJson = """{"success":false,"ok":false,"text":null,"decodedText":null,"decoded_text":null,"backend":"native","backendType":"native","model_detected":true,"modelDetected":true,"tokenizer_loaded":true,"vocab_size":151936,"real_token_ids":true,"real_inference":false,"native_generation_available":false,"simulation":false,"simulationActive":false,"tokensGenerated":0,"prompt_tokens":4,"token_ids_preview":"[151643, 482, 991]","error":"REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED","message":"Tokenizer is available, but real native generation is not implemented yet.","last_error":"REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED","last_failure":"Tokenizer is available, but real native generation is not implemented yet."}"""

        val parsed = LlamaCppJni().parseNativeJson(honestJson)

        assertFalse(parsed.success)
        assertEquals("native", parsed.backend)
        assertTrue(parsed.modelDetected)
        assertTrue(parsed.tokenizerLoaded)
        assertTrue(parsed.realTokenIds)
        assertFalse(parsed.realInference)
        assertFalse(parsed.nativeGenerationAvailable)
        assertFalse(parsed.simulation)
        assertEquals(0, parsed.tokensGenerated)
        assertNull(parsed.text)
        assertNull(parsed.decodedText)
        assertEquals("REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED", parsed.errorCode)
        assertEquals("Tokenizer is available, but real native generation is not implemented yet.", parsed.message)
        assertEquals("[151643, 482, 991]", parsed.tokenIdsPreview)
    }
}
