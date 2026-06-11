package com.nova.luna.brain

import com.nova.luna.modelinstall.SimpleJson
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

    private lateinit var liteModelFile: File
    private lateinit var fakeNativeRuntime: FakeNativeLlamaRuntime

    class FakeNativeLlamaRuntime : NativeLlamaRuntime {
        var loadSuccess = true
        var loaded = false
        var loadCalls = 0
        var lastPrompt: String? = null
        var generateCalls = 0
        var generateHandler: ((String, Long) -> NativeLlamaResult)? = null

        override fun loadModel(modelFile: File): Boolean {
            loadCalls += 1
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
                loadMs = 50,
                modelLoadMs = 50,
                promptEvalMs = 120,
                generationMs = 0,
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
                modelLoaded = true,
                modelReused = false,
                modelLoadCount = 1,
                generationCallCount = generateCalls,
                promptText = prompt,
                promptTokenIdsSample = listOf(151643, 482, 991),
                generatedTokenIdsSample = emptyList(),
                jsonParseAttempted = false,
                jsonParseSuccess = false,
                parsedIntent = null,
                parsedRiskLevel = null,
                finishReason = "proof_only",
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
        assertEquals(1, fakeNativeRuntime.loadCalls)
        assertEquals(1, fakeNativeRuntime.generateCalls)

        val diag = runtime.diagnostics()
        assertTrue("Model should be detected, was: $diag", diag.contains("model_detected=true"))
        assertTrue("Tokenizer should be loaded, was: $diag", diag.contains("tokenizer_loaded=true"))
        assertTrue("Vocab size should be surfaced, was: $diag", diag.contains("vocab_size=151936"))
        assertTrue("Tokenization proof should be true, was: $diag", diag.contains("tokenization_ok=true"))
        assertTrue("Prompt token count should be surfaced, was: $diag", diag.contains("prompt_tokens=12"))
        assertTrue("Load count should be surfaced, was: $diag", diag.contains("model_load_count=1"))
        assertTrue("Generation count should be surfaced, was: $diag", diag.contains("generation_call_count=1"))
        assertTrue("Reuse should stay false on first call, was: $diag", diag.contains("model_reused=false"))
        assertTrue("Load latency should be surfaced, was: $diag", diag.contains("load_ms=50"))
        assertTrue("Generation latency should be surfaced, was: $diag", diag.contains("generation_ms=0"))
        assertTrue("Real token IDs proof should be true, was: $diag", diag.contains("real_token_ids=true"))
        assertTrue("Real inference must stay false, was: $diag", diag.contains("real_inference=false"))
        assertTrue("Simulation must stay false, was: $diag", diag.contains("simulation=false"))
        assertTrue("Native generation must stay unavailable, was: $diag", diag.contains("native_generation_available=false"))
        assertTrue("Tokens generated must stay zero, was: $diag", diag.contains("tokens_generated=0"))
        assertTrue("Decoded text must stay empty, was: $diag", diag.contains("decoded_text=none"))
        assertTrue("Prompt token sample should be surfaced, was: $diag", diag.contains("prompt_token_ids_sample=[151643, 482, 991]"))
        assertTrue("Generated token sample should stay empty, was: $diag", diag.contains("generated_token_ids_sample=none"))
        assertTrue("JSON parse should stay false, was: $diag", diag.contains("json_parse_attempted=false"))
        assertTrue("JSON parse success should stay false, was: $diag", diag.contains("json_parse_success=false"))
        assertTrue("Backend should stay native, was: $diag", diag.contains("backend=native"))
        assertTrue("Error code should be honest, was: $diag", diag.contains("error=REAL_NATIVE_INFERENCE_NOT_IMPLEMENTED"))
    }

    @Test
    fun `native bridge records reuse across repeated generations`() {
        val runtime = LiteLocalModelRuntime(
            modelFile = liteModelFile,
            nativeRuntime = fakeNativeRuntime
        )

        val first = runtime.generate("open camera", 5000)
        val second = runtime.generate("open camera again", 5000)

        assertFalse(first.success)
        assertFalse(second.success)
        assertEquals(1, fakeNativeRuntime.loadCalls)
        assertEquals(2, fakeNativeRuntime.generateCalls)

        val diag = runtime.diagnostics()
        assertTrue("Load count should stay one, was: $diag", diag.contains("model_load_count=1"))
        assertTrue("Generation count should increase, was: $diag", diag.contains("generation_call_count=2"))
        assertTrue("Reuse should flip on repeat generation, was: $diag", diag.contains("model_reused=true"))
        assertTrue("Prompt token sample should remain visible, was: $diag", diag.contains("prompt_token_ids_sample=[151643, 482, 991]"))
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
        assertTrue("Fake success blob should stay visible for diagnostics", result.text?.contains("open_app") == true)
        assertEquals(1, fakeNativeRuntime.loadCalls)
        assertEquals(1, fakeNativeRuntime.generateCalls)

        val diag = runtime.diagnostics()
        assertTrue("Fake success must not be surfaced, was: $diag", diag.contains("real_inference=false"))
        assertTrue("Fake success should stay marked as simulated, was: $diag", diag.contains("simulation=true"))
        assertTrue("Fake success must not be surfaced, was: $diag", diag.contains("native_generation_available=false"))
        assertTrue("Fake success should still expose the simulated decoded blob, was: $diag", diag.contains("decoded_text={"))
        assertTrue("Fake success should still expose the simulated decoded blob, was: $diag", diag.contains("open_app"))
        assertTrue("Fake success must not be surfaced, was: $diag", diag.contains("tokens_generated=0"))
        assertTrue("Fake success must keep load count visible, was: $diag", diag.contains("model_load_count=1"))
        assertTrue("Fake success must keep generation count visible, was: $diag", diag.contains("generation_call_count=1"))
        assertTrue("Fake success must keep reuse false, was: $diag", diag.contains("model_reused=false"))
    }

    @Test
    fun `missing model path is reported honestly`() {
        val runtime = LiteLocalModelRuntime(
            modelFile = File("   "),
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("open camera", 5000)

        assertFalse(result.success)
        assertEquals(PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE, result.status)
        assertTrue(result.reason.contains("MODEL_PATH_MISSING", ignoreCase = true))
        assertEquals(0, fakeNativeRuntime.loadCalls)
        assertEquals(0, fakeNativeRuntime.generateCalls)
    }

    @Test
    fun `missing model file is reported honestly`() {
        val missingFile = File(tempFolder.root, "missing.gguf")
        val runtime = LiteLocalModelRuntime(
            modelFile = missingFile,
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("open camera", 5000)

        assertFalse(result.success)
        assertEquals(PhoneLocalLlmStatus.MODEL_ASSET_MISSING, result.status)
        assertTrue(result.reason.contains("MODEL_FILE_NOT_FOUND", ignoreCase = true))
        assertEquals(0, fakeNativeRuntime.loadCalls)
        assertEquals(0, fakeNativeRuntime.generateCalls)
    }

    @Test
    fun `empty prompt is reported honestly`() {
        fakeNativeRuntime.generateHandler = { _, _ ->
            fakeNativeRuntime.proofOnlyResult("   ").copy(
                success = false,
                text = null,
                decodedText = null,
                errorCode = "EMPTY_PROMPT",
                message = "Prompt was empty.",
                lastError = "EMPTY_PROMPT",
                lastFailure = "Prompt was empty.",
                realInference = false,
                nativeGenerationAvailable = false,
                tokensGenerated = 0,
                decodedTokens = false
            )
        }

        val runtime = LiteLocalModelRuntime(
            modelFile = liteModelFile,
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("   ", 5000)

        assertFalse(result.success)
        assertEquals(PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE, result.status)
        assertTrue(result.reason.contains("Prompt was empty", ignoreCase = true))

        val diag = runtime.diagnostics()
        assertTrue("Empty prompt should be honest, was: $diag", diag.contains("error=EMPTY_PROMPT"))
        assertTrue("Empty prompt should not fake inference, was: $diag", diag.contains("real_inference=false"))
        assertTrue("Empty prompt should not emit decoded text, was: $diag", diag.contains("decoded_text=none"))
    }

    @Test
    fun `tokenizer failure is reported honestly`() {
        fakeNativeRuntime.generateHandler = { prompt, _ ->
            fakeNativeRuntime.proofOnlyResult(prompt).copy(
                tokenizerLoaded = false,
                realTokenIds = false,
                realInference = false,
                nativeGenerationAvailable = false,
                tokensGenerated = 0,
                decodedText = null,
                text = null,
                errorCode = "TOKENIZER_NOT_LOADED",
                message = "Tokenizer metadata not loaded from GGUF metadata.",
                lastError = "TOKENIZER_NOT_LOADED",
                lastFailure = "Tokenizer metadata not loaded from GGUF metadata.",
                generatedTokenIdsSample = emptyList(),
                jsonParseAttempted = false,
                jsonParseSuccess = false
            )
        }

        val runtime = LiteLocalModelRuntime(
            modelFile = liteModelFile,
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("open camera", 5000)

        assertFalse(result.success)
        assertEquals(PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE, result.status)

        val diag = runtime.diagnostics()
        assertTrue("Tokenizer failure should be honest, was: $diag", diag.contains("error=TOKENIZER_NOT_LOADED"))
        assertTrue("Tokenizer failure should hide inference, was: $diag", diag.contains("tokenizer_loaded=false"))
        assertTrue("Tokenizer failure should hide real token ids, was: $diag", diag.contains("real_token_ids=false"))
        assertTrue("Tokenizer failure should hide real inference, was: $diag", diag.contains("real_inference=false"))
        assertTrue("Tokenizer failure should keep generated tokens at zero, was: $diag", diag.contains("tokens_generated=0"))
    }

    @Test
    fun `generation failure is reported honestly`() {
        fakeNativeRuntime.generateHandler = { prompt, _ ->
            fakeNativeRuntime.proofOnlyResult(prompt).copy(
                realTokenIds = true,
                realInference = false,
                nativeGenerationAvailable = false,
                tokensGenerated = 0,
                decodedText = null,
                text = null,
                errorCode = "PROMPT_EVAL_FAILED",
                message = "Graph compute failed during prefill.",
                lastError = "PROMPT_EVAL_FAILED",
                lastFailure = "Graph compute failed during prefill.",
                generatedTokenIdsSample = emptyList(),
                jsonParseAttempted = false,
                jsonParseSuccess = false
            )
        }

        val runtime = LiteLocalModelRuntime(
            modelFile = liteModelFile,
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("open camera", 5000)

        assertFalse(result.success)
        assertEquals(PhoneLocalLlmStatus.MODEL_RUNTIME_NOT_AVAILABLE, result.status)

        val diag = runtime.diagnostics()
        assertTrue("Generation failure should be honest, was: $diag", diag.contains("error=PROMPT_EVAL_FAILED"))
        assertTrue("Generation failure should keep token ids honest, was: $diag", diag.contains("real_token_ids=true"))
        assertTrue("Generation failure should keep real inference false, was: $diag", diag.contains("real_inference=false"))
        assertTrue("Generation failure should keep decoded text empty, was: $diag", diag.contains("decoded_text=none"))
    }

    @Test
    fun `decode failure is reported honestly`() {
        fakeNativeRuntime.generateHandler = { prompt, _ ->
            fakeNativeRuntime.proofOnlyResult(prompt).copy(
                realTokenIds = true,
                realInference = true,
                nativeGenerationAvailable = true,
                tokensGenerated = 1,
                decodedText = null,
                text = null,
                errorCode = "DECODE_FAILED",
                message = "Failed to decode generated token.",
                lastError = "DECODE_FAILED",
                lastFailure = "Failed to decode generated token.",
                generatedTokenIdsSample = listOf(123),
                tokenIdsPreview = "[123]",
                jsonParseAttempted = false,
                jsonParseSuccess = false
            )
        }

        val runtime = LiteLocalModelRuntime(
            modelFile = liteModelFile,
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("open camera", 5000)

        assertFalse(result.success)
        assertEquals(PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED, result.status)

        val diag = runtime.diagnostics()
        assertTrue("Decode failure should be honest, was: $diag", diag.contains("error=DECODE_FAILED"))
        assertTrue("Decode failure should show real inference, was: $diag", diag.contains("real_inference=true"))
        assertTrue("Decode failure should report generated tokens, was: $diag", diag.contains("tokens_generated=1"))
        assertTrue("Decode failure should keep decoded text null, was: $diag", diag.contains("decoded_text=none"))
        assertTrue("Decode failure should surface generated token ids, was: $diag", diag.contains("generated_token_ids_sample=[123]"))
    }

    @Test
    fun `empty decoded tokens are reported honestly`() {
        fakeNativeRuntime.generateHandler = { prompt, _ ->
            fakeNativeRuntime.proofOnlyResult(prompt).copy(
                realTokenIds = true,
                realInference = true,
                nativeGenerationAvailable = true,
                tokensGenerated = 1,
                decodedText = null,
                text = null,
                errorCode = "GENERATED_TOKENS_DECODED_EMPTY",
                message = "Generated tokens decoded to empty text.",
                lastError = "GENERATED_TOKENS_DECODED_EMPTY",
                lastFailure = "Generated tokens decoded to empty text.",
                generatedTokenIdsSample = listOf(123),
                tokenIdsPreview = "[123]",
                jsonParseAttempted = false,
                jsonParseSuccess = false
            )
        }

        val runtime = LiteLocalModelRuntime(
            modelFile = liteModelFile,
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("open camera", 5000)

        assertFalse(result.success)
        assertEquals(PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED, result.status)

        val diag = runtime.diagnostics()
        assertTrue("Empty decode should be honest, was: $diag", diag.contains("error=GENERATED_TOKENS_DECODED_EMPTY"))
        assertTrue("Empty decode should report real inference, was: $diag", diag.contains("real_inference=true"))
        assertTrue("Empty decode should report generated tokens, was: $diag", diag.contains("tokens_generated=1"))
        assertTrue("Empty decode should keep decoded text null, was: $diag", diag.contains("decoded_text=none"))
    }

    @Test
    fun `json parse failure is reported honestly`() {
        fakeNativeRuntime.generateHandler = { prompt, _ ->
            fakeNativeRuntime.proofOnlyResult(prompt).copy(
                success = false,
                realTokenIds = true,
                realInference = true,
                nativeGenerationAvailable = true,
                tokensGenerated = 1,
                decodedText = """{"intent":"open_app","reply":"Opening camera","actionType":"EXTERNAL_ACTION","riskLevel":"safe""",
                text = """{"intent":"open_app","reply":"Opening camera","actionType":"EXTERNAL_ACTION","riskLevel":"safe""",
                errorCode = "JSON_PARSE_FAILED",
                message = "Generated text was not valid JSON.",
                lastError = "JSON_PARSE_FAILED",
                lastFailure = "Generated text was not valid JSON.",
                generatedTokenIdsSample = listOf(123),
                tokenIdsPreview = "[123]",
                jsonParseAttempted = true,
                jsonParseSuccess = false,
                parsedIntent = null,
                parsedRiskLevel = null,
                finishReason = "error"
            )
        }

        val runtime = LiteLocalModelRuntime(
            modelFile = liteModelFile,
            nativeRuntime = fakeNativeRuntime
        )

        val result = runtime.generate("open camera", 5000)

        assertFalse(result.success)
        assertEquals(PhoneLocalLlmStatus.OUTPUT_PARSE_FAILED, result.status)

        val diag = runtime.diagnostics()
        assertTrue("JSON parse failure should be honest, was: $diag", diag.contains("error=JSON_PARSE_FAILED"))
        assertTrue("JSON parse failure should mark parse attempted, was: $diag", diag.contains("json_parse_attempted=true"))
        assertTrue("JSON parse failure should mark parse success false, was: $diag", diag.contains("json_parse_success=false"))
        assertTrue("JSON parse failure should keep real inference true, was: $diag", diag.contains("real_inference=true"))
        assertTrue("JSON parse failure should surface generated text, was: $diag", diag.contains("decoded_text={"))
        assertTrue("JSON parse failure should surface generated token ids, was: $diag", diag.contains("generated_token_ids_sample=[123]"))
    }

    @Test
    fun `native json parser accepts honest generated output with arrays`() {
        val generatedText = """{"intent":"open_app","reply":"Opening camera","actionType":"EXTERNAL_ACTION","riskLevel":"safe","requiresConfirmation":false,"finalActionAllowed":true,"params":{"app":"camera"}}"""
        val honestJson = SimpleJson.stringify(
            linkedMapOf<String, Any?>(
                "success" to true,
                "ok" to true,
                "text" to generatedText,
                "decodedText" to generatedText,
                "decoded_text" to generatedText,
                "backend" to "native",
                "backendType" to "native",
                "model_detected" to true,
                "modelDetected" to true,
                "tokenizer_loaded" to true,
                "tokenizerLoaded" to true,
                "vocab_size" to 151936,
                "real_token_ids" to true,
                "realTokenIds" to true,
                "real_inference" to true,
                "realInference" to true,
                "native_generation_available" to true,
                "nativeGenerationAvailable" to true,
                "simulation" to false,
                "simulationActive" to false,
                "tokens_generated" to 3,
                "prompt_tokens" to 4,
                "prompt_tokens_count" to 4,
                "load_ms" to 120,
                "generation_ms" to 45,
                "model_load_count" to 2,
                "generation_call_count" to 3,
                "model_reused" to true,
                "prompt_text" to "open camera",
                "prompt_token_ids_sample" to listOf(151643, 482, 991, 12),
                "generated_token_ids_sample" to listOf(42, 43, 44),
                "json_parse_attempted" to true,
                "json_parse_success" to true,
                "parsed_intent" to "open_app",
                "parsed_risk_level" to "safe",
                "finish_reason" to "stop",
                "token_ids_preview" to "[151643, 482, 991, 12]",
                "message" to "Generated JSON action."
            ),
            indentSpaces = 0
        )

        val parsed = LlamaCppJni().parseNativeJson(honestJson)

        assertTrue(parsed.success)
        assertEquals("native", parsed.backend)
        assertEquals(generatedText, parsed.text)
        assertEquals(generatedText, parsed.decodedText)
        assertTrue(parsed.modelDetected)
        assertTrue(parsed.tokenizerLoaded)
        assertTrue(parsed.realTokenIds)
        assertTrue(parsed.realInference)
        assertTrue(parsed.nativeGenerationAvailable)
        assertFalse(parsed.simulation)
        assertEquals(3, parsed.tokensGenerated)
        assertEquals(4, parsed.promptTokens)
        assertEquals(120L, parsed.loadMs)
        assertEquals(45L, parsed.generationMs)
        assertEquals(2, parsed.modelLoadCount)
        assertEquals(3, parsed.generationCallCount)
        assertTrue(parsed.modelReused)
        assertEquals("open camera", parsed.promptText)
        assertEquals(listOf(151643, 482, 991, 12), parsed.promptTokenIdsSample)
        assertEquals(listOf(42, 43, 44), parsed.generatedTokenIdsSample)
        assertTrue(parsed.jsonParseAttempted)
        assertTrue(parsed.jsonParseSuccess)
        assertEquals("open_app", parsed.parsedIntent)
        assertEquals("safe", parsed.parsedRiskLevel)
        assertEquals("stop", parsed.finishReason)
        assertEquals("[151643, 482, 991, 12]", parsed.tokenIdsPreview)
        assertEquals("Generated JSON action.", parsed.message)
    }
}
