package com.nova.luna.brain

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nova.luna.diagnostics.NativeProofProcessController
import com.nova.luna.diagnostics.NativeProofStage
import com.nova.luna.diagnostics.NativeProofStageResult
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.modelinstall.Sha256ModelVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeLlamaRealInferenceAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val controller = NativeProofProcessController(context = context)
    private val jsonCodec = BrainActionJsonCodec()
    private val validator = BrainActionValidator()

    @Test
    fun fullModelCompletesFirstForwardPass() {
        val fixture = loadFullModelFixture()
        val result = runProof(
            stage = NativeProofStage.DECODE_ONE_PROMPT_TOKEN,
            modelFile = fixture.file,
            modelSha256 = fixture.sha256,
            prompt = "a",
            maxTokens = 1
        )

        assertSuccessfulStage(
            label = "fullModelCompletesFirstForwardPass",
            result = result,
            fixture = fixture
        )
        assertEquals("prompt_decode_complete", result.stageReached)
        assertTrue("Prompt tokens must be present", result.promptTokenIds.isNotEmpty())
        assertTrue("Prompt logits must be computed", result.logitsComputed)
        assertTrue("Prompt logits must be finite", result.logitsFinite)
        assertNotNull("Prompt logits preview must be present", result.logitsPreview)
        assertTrue("Prompt forward pass must execute", result.nativeForwardPassCount > 0)
        assertTrue("Prompt stage must not generate output tokens", result.generatedTokenIds.isEmpty())
        assertEquals("Prompt stage must not count generated tokens", 0, result.tokensGenerated)
        assertTokenIdsWithinVocab("fullModelCompletesFirstForwardPass", result)
    }

    @Test
    fun fullModelGeneratesRealTokens() {
        val fixture = loadFullModelFixture()
        val result = runProof(
            stage = NativeProofStage.READABLE_OUTPUT,
            modelFile = fixture.file,
            modelSha256 = fixture.sha256,
            prompt = "Write eight common fruit names as a comma-separated line.",
            maxTokens = 16
        )

        assertSuccessfulStage(
            label = "fullModelGeneratesRealTokens",
            result = result,
            fixture = fixture
        )
        assertEquals("generation_complete", result.stageReached)
        assertTrue("Readable generation must compute logits", result.logitsComputed)
        assertTrue("Readable generation must report finite logits", result.logitsFinite)
        assertTrue("Readable generation must sample from model logits", result.sampledFromModelLogits)
        assertTrue("Readable generation must produce at least eight tokens", result.tokensGenerated >= 8)
        assertTrue("Readable generation must return at least eight token ids", result.generatedTokenIds.size >= 8)
        assertTrue("Readable generation must return decoded text", !result.decodedText.isNullOrBlank())
        assertTokenIdsWithinVocab("fullModelGeneratesRealTokens", result)
    }

    @Test
    fun fullModelProducesReadableOutput() {
        val fixture = loadFullModelFixture()
        val result = runProof(
            stage = NativeProofStage.READABLE_OUTPUT,
            modelFile = fixture.file,
            modelSha256 = fixture.sha256,
            prompt = "Reply with exactly: NOVA_BRAIN_OK",
            maxTokens = 12
        )

        assertSuccessfulStage(
            label = "fullModelProducesReadableOutput",
            result = result,
            fixture = fixture
        )
        assertEquals("generation_complete", result.stageReached)
        assertTrue("Readable output must compute logits", result.logitsComputed)
        assertTrue("Readable output must report finite logits", result.logitsFinite)
        assertTrue("Readable output must sample from model logits", result.sampledFromModelLogits)
        assertTrue("Readable output must emit generated tokens", result.generatedTokenIds.isNotEmpty())
        assertTrue("Readable output must contain marker NOVA_BRAIN_OK", result.decodedText.orEmpty().contains("NOVA_BRAIN_OK"))
        assertTokenIdsWithinVocab("fullModelProducesReadableOutput", result)
    }

    @Test
    fun fullModelProducesValidBrainActionJson() {
        val fixture = loadFullModelFixture()
        val result = runProof(
            stage = NativeProofStage.STRUCTURED_JSON_OUTPUT,
            modelFile = fixture.file,
            modelSha256 = fixture.sha256,
            prompt = "Open the camera.",
            maxTokens = 24
        )

        assertSuccessfulStage(
            label = "fullModelProducesValidBrainActionJson",
            result = result,
            fixture = fixture
        )
        assertEquals("json_complete", result.stageReached)
        assertTrue("Structured output must compute logits", result.logitsComputed)
        assertTrue("Structured output must report finite logits", result.logitsFinite)
        assertTrue("Structured output must sample from model logits", result.sampledFromModelLogits)
        assertTrue("Structured output must emit generated tokens", result.generatedTokenIds.isNotEmpty())
        assertTrue("Structured output must return JSON text", result.decodedText.orEmpty().trim().startsWith("{"))

        val action: BrainAction = requireNotNull(jsonCodec.decode(result.decodedText.orEmpty())) {
            "Structured output did not decode into BrainAction JSON: ${result.decodedText}"
        }

        assertEquals(BrainActionType.OPEN_CAMERA, action.actionType)
        assertTrue("Intent must identify camera opening", action.intent.equals("OPEN_CAMERA", ignoreCase = true))
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertTrue("Validator must accept the structured JSON action", validator.isAcceptable(action))
        assertTrue("Structured JSON must not come from an error source", action.source != BrainActionSource.ERROR)
        assertTokenIdsWithinVocab("fullModelProducesValidBrainActionJson", result)
    }

    @Test
    fun fullModelHandlesHindiOrHinglishCommand() {
        val fixture = loadFullModelFixture()
        val result = runProof(
            stage = NativeProofStage.STRUCTURED_JSON_OUTPUT,
            modelFile = fixture.file,
            modelSha256 = fixture.sha256,
            prompt = "Camera kholo.",
            maxTokens = 24
        )

        assertSuccessfulStage(
            label = "fullModelHandlesHindiOrHinglishCommand",
            result = result,
            fixture = fixture
        )
        assertEquals("json_complete", result.stageReached)
        assertTrue("Hinglish structured output must compute logits", result.logitsComputed)
        assertTrue("Hinglish structured output must report finite logits", result.logitsFinite)
        assertTrue("Hinglish structured output must sample from model logits", result.sampledFromModelLogits)
        assertTrue("Hinglish structured output must emit generated tokens", result.generatedTokenIds.isNotEmpty())

        val action: BrainAction = requireNotNull(jsonCodec.decode(result.decodedText.orEmpty())) {
            "Hinglish output did not decode into BrainAction JSON: ${result.decodedText}"
        }

        assertEquals(BrainActionType.OPEN_CAMERA, action.actionType)
        assertTrue("Hinglish intent must identify camera opening", action.intent.equals("OPEN_CAMERA", ignoreCase = true))
        assertEquals(BrainRiskLevel.LOW, action.riskLevel)
        assertFalse(action.requiresConfirmation)
        assertTrue("Validator must accept the Hinglish structured JSON action", validator.isAcceptable(action))
        assertTrue("Structured JSON must not come from an error source", action.source != BrainActionSource.ERROR)
        assertTokenIdsWithinVocab("fullModelHandlesHindiOrHinglishCommand", result)
    }

    @Test
    fun fullModelCanRunRepeatedly() {
        val fixture = loadFullModelFixture()
        val prompts = listOf(
            "Reply with exactly: NOVA_BRAIN_OK",
            "Reply with exactly: NOVA_BRAIN_OK",
            "Reply with exactly: NOVA_BRAIN_OK",
            "Reply with exactly: NOVA_BRAIN_OK",
            "Reply with exactly: NOVA_BRAIN_OK"
        )

        var lastResult: NativeProofStageResult? = null
        prompts.forEachIndexed { index, prompt ->
            val result = runProof(
                stage = NativeProofStage.READABLE_OUTPUT,
                modelFile = fixture.file,
                modelSha256 = fixture.sha256,
                prompt = prompt,
                maxTokens = 12
            )

            assertSuccessfulStage(
                label = "fullModelCanRunRepeatedly#$index",
                result = result,
                fixture = fixture
            )
            assertEquals("generation_complete", result.stageReached)
            assertTrue("Repeated run must compute logits", result.logitsComputed)
            assertTrue("Repeated run must report finite logits", result.logitsFinite)
            assertTrue("Repeated run must sample from model logits", result.sampledFromModelLogits)
            assertTrue("Repeated run must emit generated tokens", result.generatedTokenIds.isNotEmpty())
            assertTrue("Repeated run must return decoded text", result.decodedText.orEmpty().contains("NOVA_BRAIN_OK"))
            assertTokenIdsWithinVocab("fullModelCanRunRepeatedly#$index", result)
            lastResult = result
        }

        val finalResult: NativeProofStageResult = requireNotNull(lastResult) {
            "Repeated proof did not return a final result"
        }
        assertTrue("Final repeated result must remain a real inference run", finalResult.realForwardPass)
        assertTrue("Final repeated result must remain sampled from model logits", finalResult.sampledFromModelLogits)
    }

    @Test
    fun watchdogKillsRealHang() {
        val fixture = loadFullModelFixture()
        val result = runProof(
            stage = NativeProofStage.CONTROLLED_HANG,
            modelFile = fixture.file,
            modelSha256 = fixture.sha256,
            prompt = "debug controlled hang",
            maxTokens = 1,
            timeoutMs = 4_000L
        )

        assertFalse("Controlled hang must not succeed", result.success)
        assertEquals("timeout:CONTROLLED_HANG", result.stageReached)
        assertEquals("STAGE_TIMEOUT", result.error)
        assertFalse("Controlled hang must not simulate success", result.simulation)
        assertTrue("Controlled hang must not generate tokens", result.generatedTokenIds.isEmpty())
        assertTrue("Controlled hang must not claim a forward pass", !result.realForwardPass)
        assertTrue("Controlled hang must identify the isolated service process", result.servicePid > 0)
    }

    private fun loadFullModelFixture(): ModelFixture {
        val modelFile = File(context.filesDir, "model_install/models/full/qwen2.5-1.5b-instruct-q4_k_m.gguf")
        logStage("fullModelFixture", "model path verification", "begin", modelFile.absolutePath)
        assertTrue("Missing model file: ${modelFile.absolutePath}", modelFile.exists())
        assertTrue("Unreadable model file: ${modelFile.absolutePath}", modelFile.canRead())
        val sha256 = Sha256ModelVerifier().digestHex(modelFile)
        assertEquals(EXPECTED_FULL_SHA256, sha256)
        logStage("fullModelFixture", "model path verification", "end", modelFile.absolutePath)
        logStage("fullModelFixture", "model sha256", "end", sha256)
        return ModelFixture(modelFile, sha256)
    }

    private fun runProof(
        stage: NativeProofStage,
        modelFile: File,
        modelSha256: String,
        prompt: String,
        maxTokens: Int,
        timeoutMs: Long = DEFAULT_STAGE_TIMEOUT_MS
    ): NativeProofStageResult {
        logStage(stage.name, "controller", "begin", "prompt=${prompt.take(64)}")
        val result = controller.runStage(
            stage = stage,
            modelId = MODEL_ID_FULL,
            debugOverridePath = modelFile.absolutePath,
            modelSha256 = modelSha256,
            prompt = prompt,
            maxTokens = maxTokens,
            stageTimeoutMs = timeoutMs
        )
        logStage(stage.name, "controller", "end", stageSummary(result))
        return result
    }

    private fun assertSuccessfulStage(
        label: String,
        result: NativeProofStageResult,
        fixture: ModelFixture
    ) {
        assertTrue("Stage must succeed for $label: ${result.message}", result.success)
        assertEquals("Model path mismatch for $label", fixture.file.absolutePath, result.modelPath)
        assertEquals("Model SHA mismatch for $label", fixture.sha256, result.modelSha256)
        assertTrue("Simulation must remain false for $label", !result.simulation)
        assertTrue("Service pid must be positive for $label", result.servicePid > 0)
        assertTrue("Controller pid must be positive for $label", result.controllerPid > 0)
        assertTrue("Context size must be positive for $label", result.contextSize > 0)
        assertTrue("Batch size must be positive for $label", result.batchSize > 0)
        assertTrue("Thread count must be positive for $label", result.threadCount > 0)
        assertTrue("Vocabulary size must be positive for $label", result.vocabSize > 0)
        assertTrue("Load duration must be non-negative for $label", result.loadMs >= 0)
        assertTrue("Prompt evaluation duration must be non-negative for $label", result.promptEvalMs >= 0)
        assertTrue("Generation duration must be non-negative for $label", result.generationMs >= 0)
        assertTrue("Token ids must stay within the vocabulary for $label", result.promptTokenIds.all { it >= 0 && it < result.vocabSize })
    }

    private fun assertTokenIdsWithinVocab(label: String, result: NativeProofStageResult) {
        assertTrue("Vocabulary size must be positive for $label", result.vocabSize > 0)
        result.promptTokenIds.forEach { token ->
            assertTrue("Prompt token $token must be within vocab for $label", token >= 0 && token < result.vocabSize)
        }
        result.generatedTokenIds.forEach { token ->
            assertTrue("Generated token $token must be within vocab for $label", token >= 0 && token < result.vocabSize)
        }
    }

    private fun stageSummary(result: NativeProofStageResult): String {
        return buildString {
            append("success=").append(result.success)
            append(", stageReached=").append(result.stageReached)
            append(", loadMs=").append(result.loadMs)
            append(", promptEvalMs=").append(result.promptEvalMs)
            append(", generationMs=").append(result.generationMs)
            append(", tokensGenerated=").append(result.tokensGenerated)
            append(", promptTokenIds=").append(result.promptTokenIds)
            append(", generatedTokenIds=").append(result.generatedTokenIds)
            append(", decodedText=").append(result.decodedText ?: "none")
            append(", realForwardPass=").append(result.realForwardPass)
            append(", logitsComputed=").append(result.logitsComputed)
            append(", logitsFinite=").append(result.logitsFinite)
            append(", logitsPreview=").append(result.logitsPreview ?: "none")
            append(", sampledFromModelLogits=").append(result.sampledFromModelLogits)
            append(", contextSize=").append(result.contextSize)
            append(", batchSize=").append(result.batchSize)
            append(", threadCount=").append(result.threadCount)
            append(", vocabSize=").append(result.vocabSize)
            append(", modelArch=").append(result.modelArch ?: "unknown")
            append(", diagnostics=").append(result.diagnostics ?: "none")
        }
    }

    private fun logStage(label: String, stage: String, event: String, detail: String? = null) {
        val suffix = detail?.let { " | $it" } ?: ""
        Log.i(TAG, "label=$label | stage=$stage | event=$event$suffix")
    }

    private data class ModelFixture(
        val file: File,
        val sha256: String
    )

    private companion object {
        private const val TAG = "NativeLlamaProof"
        private const val MODEL_ID_FULL = "full"
        private const val EXPECTED_FULL_SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
        private const val DEFAULT_STAGE_TIMEOUT_MS = 120_000L
    }
}
