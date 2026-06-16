package com.nova.luna.diagnostics

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nova.luna.brain.LlamaCppJni
import com.nova.luna.brain.NativeLlamaResult
import com.nova.luna.modelinstall.ModelInstallReason
import com.nova.luna.modelinstall.ModelInstallService
import com.nova.luna.modelinstall.ModelInstallVerifier
import com.nova.luna.modelinstall.ModelPackId
import com.nova.luna.modelinstall.ModelPathResolver
import com.nova.luna.modelinstall.ModelRuntimeStateStore
import com.nova.luna.modelinstall.PrivateAppModelStorage
import com.nova.luna.service.NotificationHelper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only proof service that runs native inference in a separate Android process.
 *
 * The controller binds to this service and waits for a reply. If JNI hangs, the
 * instrumentation process can force-stop the target app package without being blocked
 * by the native call.
 */
class NativeInferenceProofProcessService : Service() {

    private val handler = Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            NativeProofContract.MSG_RUN_STAGE -> {
                val replyTo = message.replyTo
                val request = message.data ?: Bundle.EMPTY
                val result = runCatching { handleRequest(request, replyTo) }.getOrElse { throwable ->
                    buildErrorBundle(
                        stage = request.getString(NativeProofContract.EXTRA_STAGE) ?: NativeProofStage.LOAD_ONLY.name,
                        controllerPid = request.getInt(NativeProofContract.EXTRA_CONTROLLER_PID, -1),
                        message = throwable.message ?: throwable::class.java.simpleName,
                        diagnostics = "service_pid=${Process.myPid()}, error=${throwable.message ?: throwable::class.java.simpleName}"
                    )
                }

                if (replyTo != null) {
                    runCatching {
                        replyTo.send(Message.obtain(null, NativeProofContract.MSG_RESULT).apply {
                            data = result
                        })
                    }.onFailure {
                        Log.e(TAG, "Failed to send proof result: ${it.message}", it)
                    }
                } else {
                    Log.w(TAG, "Missing replyTo messenger for proof request")
                }
                true
            }

            else -> false
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handleRequest(request: Bundle, replyTo: Messenger?): Bundle {
        val stage = runCatching {
            NativeProofStage.valueOf(
                request.getString(NativeProofContract.EXTRA_STAGE) ?: NativeProofStage.LOAD_ONLY.name
            )
        }.getOrElse { NativeProofStage.LOAD_ONLY }
        val modelId = request.getString(NativeProofContract.EXTRA_MODEL_ID) ?: ModelPackId.LITE.wireValue
        val debugOverridePath = request.getString(NativeProofContract.EXTRA_MODEL_PATH)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val requestSha256 = request.getString(NativeProofContract.EXTRA_MODEL_SHA256)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val prompt = request.getString(NativeProofContract.EXTRA_PROMPT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PROMPT
        val maxTokens = request.getInt(NativeProofContract.EXTRA_MAX_TOKENS, 1).coerceAtLeast(1)
        val timeoutMs = request.getLong(NativeProofContract.EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
        val controllerPid = request.getInt(NativeProofContract.EXTRA_CONTROLLER_PID, -1)
        val servicePid = Process.myPid()
        val startMs = wallClockMs()
        val completed = AtomicBoolean(false)
        val watchdogThread = HandlerThread("native-proof-watchdog-${stage.name.lowercase()}").apply { start() }
        val watchdogHandler = Handler(watchdogThread.looper)
        startProofForeground(stage)
        val timeoutRunnable = Runnable {
            if (!completed.compareAndSet(false, true)) {
                return@Runnable
            }

            val endMs = wallClockMs()
            logStage(stage, "watchdog timeout", "fired", "timeoutMs=$timeoutMs")
            val timeoutBundle = buildErrorBundle(
                stage = stage.name,
                stageReached = "timeout:${stage.name}",
                controllerPid = controllerPid,
                message = "Stage ${stage.name} timed out after ${timeoutMs}ms",
                diagnostics = "service_pid=$servicePid, timeout_ms=$timeoutMs, stage=${stage.name}",
                modelPath = null,
                modelSha256 = null,
                startMs = startMs,
                endMs = endMs
            )
            replyTo?.let {
                runCatching {
                    it.send(Message.obtain(null, NativeProofContract.MSG_RESULT).apply {
                        data = timeoutBundle
                    })
                }.onFailure { throwable ->
                    Log.e(TAG, "Failed to send timeout bundle: ${throwable.message}", throwable)
                }
            }
            logStage(stage, "watchdog timeout", "sent", "killing pid=$servicePid")
            Process.killProcess(servicePid)
        }
        watchdogHandler.postDelayed(timeoutRunnable, timeoutMs)

        logStage(stage, "setup", "begin", "service_pid=$servicePid, controller_pid=$controllerPid, model_id=$modelId")

        val storage = PrivateAppModelStorage.from(applicationContext)
        val runtimeStateStore = ModelRuntimeStateStore(storage)
        val modelInstallService = ModelInstallService(
            pathResolver = ModelPathResolver(applicationContext, storage, runtimeStateStore),
            verifier = ModelInstallVerifier(),
            runtimeStateStore = runtimeStateStore,
            storage = storage
        )

        val installState = modelInstallService.getInstallState(modelId, debugOverridePath)
        val modelPath = installState.resolvedPath
            ?: debugOverridePath
            ?: File(storage.modelsDir(ModelPackId.fromWireValue(modelId) ?: ModelPackId.LITE), installState.expectedFileName).absolutePath
        val modelFile = File(modelPath)
        val modelSha256 = installState.sha256Actual ?: installState.sha256Expected ?: requestSha256

        logStage(
            stage,
            "model path verification",
            "begin",
            "path=${modelFile.absolutePath}, exists=${modelFile.exists()}, readable=${modelFile.canRead()}, ready=${installState.ready}, reason=${installState.reason}, sha256=${modelSha256.orEmpty()}"
        )
        if (!installState.ready || !modelFile.exists() || !modelFile.canRead()) {
            val endMs = wallClockMs()
            logStage(stage, "model path verification", "end", "ready=false")
            return buildErrorBundle(
                stage = stage.name,
                controllerPid = controllerPid,
                message = "Model is not ready: ${installState.reason}",
                diagnostics = "service_pid=$servicePid, model_path=${modelFile.absolutePath}, sha256=${modelSha256.orEmpty()}, reason=${installState.reason}",
                modelPath = modelFile.absolutePath,
                modelSha256 = modelSha256,
                startMs = startMs,
                endMs = endMs
            )
        }
        logStage(stage, "model path verification", "end", "path=${modelFile.absolutePath}")

        if (stage == NativeProofStage.CONTROLLED_HANG) {
            logStage(stage, "controlled hang", "begin", "debug_only=true, stall_ms=${timeoutMs + 60_000L}")
            runCatching { Thread.sleep(timeoutMs + 60_000L) }
            logStage(stage, "controlled hang", "end", "unexpected_return=true")
        }

        val runtime = LlamaCppJni()
        logStage(stage, "native library load", "begin", "pid=$servicePid")
        logStage(stage, "native library load", "end", runtime.diagnostics())

        return try {
            when (stage) {
                NativeProofStage.LOAD_ONLY -> {
                    logStage(stage, "model initialization", "begin", modelFile.absolutePath)
                }

                NativeProofStage.CREATE_CONTEXT_ONLY,
                NativeProofStage.TOKENIZE_ONLY -> {
                    logStage(stage, "model initialization", "begin", modelFile.absolutePath)
                    logStage(stage, "tokenizer initialization", "begin", modelFile.absolutePath)
                }

                NativeProofStage.CONTROLLED_HANG -> {
                    logStage(stage, "controlled hang", "begin", "should_not_reach_runtime")
                }

                NativeProofStage.DECODE_ONE_PROMPT_TOKEN,
                NativeProofStage.GENERATE_ONE_TOKEN,
                NativeProofStage.READABLE_OUTPUT,
                NativeProofStage.STRUCTURED_JSON_OUTPUT -> {
                    logStage(stage, "model initialization", "begin", modelFile.absolutePath)
                    logStage(stage, "tokenizer initialization", "begin", modelFile.absolutePath)
                    logStage(stage, "generation start", "begin", "maxTokens=$maxTokens, timeoutMs=$timeoutMs")
                    logStage(stage, "first forward pass", "begin")
                }
            }

            val result = runtime.runProofStage(
                stage = stage,
                modelFile = modelFile,
                prompt = prompt,
                timeoutMs = timeoutMs,
                maxTokens = maxTokens
            )

            if (!completed.compareAndSet(false, true)) {
                throw IllegalStateException("Stage ${stage.name} timed out after ${timeoutMs}ms")
            }
            watchdogHandler.removeCallbacks(timeoutRunnable)

            when (stage) {
                NativeProofStage.LOAD_ONLY -> {
                    logStage(stage, "model initialization", "end", "loaded=${result.success}")
                }

                NativeProofStage.CREATE_CONTEXT_ONLY,
                NativeProofStage.TOKENIZE_ONLY -> {
                    logStage(stage, "model initialization", "end", "loaded=${result.success}")
                    logStage(stage, "tokenizer initialization", "end", "vocab=${result.vocabSize}, tokens=${result.promptTokenIdsSample.size}")
                }

                NativeProofStage.CONTROLLED_HANG -> {
                    logStage(stage, "controlled hang", "end", "unexpected_result=${result.success}")
                }

                NativeProofStage.DECODE_ONE_PROMPT_TOKEN,
                NativeProofStage.GENERATE_ONE_TOKEN,
                NativeProofStage.READABLE_OUTPUT,
                NativeProofStage.STRUCTURED_JSON_OUTPUT -> {
                    logStage(stage, "model initialization", "end", "loaded=${result.success}")
                    logStage(stage, "tokenizer initialization", "end", "vocab=${result.vocabSize}, tokens=${result.promptTokenIdsSample.size}")
                    logStage(stage, "generation start", "end", "success=${result.success}")
                    logStage(stage, "first forward pass", "end", "count=${result.nativeForwardPassCount}")
                    val decoded = result.decodedText?.trim()?.takeIf { it.isNotBlank() }
                        ?: result.text?.trim()?.takeIf { it.isNotBlank() }
                    logStage(stage, "decoding", "begin", "tokens=${result.tokensGenerated}")
                    logStage(stage, "decoding", "end", decoded ?: "<blank>")
                }
            }

            val reached = result.proofStageReached ?: stage.name
            logStage(stage, "assertion", "begin", "reached=$reached, success=${result.success}")
            val endMs = wallClockMs()
            logStage(stage, "assertion", "end", reached)

            buildStageBundle(
                stage = stage.name,
                stageReached = reached,
                controllerPid = controllerPid,
                servicePid = servicePid,
                modelPath = modelFile.absolutePath,
                modelSha256 = modelSha256,
                startMs = startMs,
                endMs = endMs,
                result = result,
                diagnostics = runtime.diagnostics()
            )
        } catch (throwable: Throwable) {
            val endMs = wallClockMs()
            buildErrorBundle(
                stage = stage.name,
                stageReached = if (completed.get()) stage.name else "timeout:${stage.name}",
                controllerPid = controllerPid,
                message = throwable.message ?: throwable::class.java.simpleName,
                diagnostics = runtime.diagnostics(),
                modelPath = modelFile.absolutePath,
                modelSha256 = modelSha256,
                startMs = startMs,
                endMs = endMs
            )
        } finally {
            watchdogHandler.removeCallbacks(timeoutRunnable)
            watchdogThread.quitSafely()
            logStage(stage, "model release", "begin", "pid=$servicePid")
            runCatching { runtime.unload() }
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            logStage(stage, "model release", "end", runtime.diagnostics())
        }
    }

    private fun startProofForeground(stage: NativeProofStage) {
        runCatching {
            NotificationHelper.ensureChannel(this)
            val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Nova Luna native proof")
                .setContentText("Running ${stage.name.lowercase().replace('_', ' ')}")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
            startForeground(NOTIFICATION_ID, notification)
            Log.i(TAG, "Foreground proof service started for stage=${stage.name}")
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to start foreground proof service: ${throwable.message}", throwable)
        }
    }

    private fun buildStageBundle(
        stage: String,
        stageReached: String,
        controllerPid: Int,
        servicePid: Int,
        modelPath: String,
        modelSha256: String?,
        diagnostics: String,
        startMs: Long,
        endMs: Long,
        result: NativeLlamaResult
    ): Bundle {
        return Bundle().apply {
            putString(NativeProofContract.RESULT_STAGE, stage)
            putString(NativeProofContract.RESULT_STAGE_REACHED, stageReached)
            putBoolean(NativeProofContract.RESULT_SUCCESS, result.success)
            putString(NativeProofContract.RESULT_MESSAGE, result.message ?: result.errorMessage)
            putString(NativeProofContract.RESULT_MODEL_PATH, modelPath)
            putString(NativeProofContract.RESULT_MODEL_SHA256, modelSha256)
            putString(NativeProofContract.RESULT_MODEL_ARCH, result.modelArch)
            putInt(NativeProofContract.RESULT_CONTEXT_SIZE, result.contextSize)
            putInt(NativeProofContract.RESULT_BATCH_SIZE, result.batchSize)
            putInt(NativeProofContract.RESULT_THREAD_COUNT, result.threadsUsed)
            putInt(NativeProofContract.RESULT_VOCAB_SIZE, result.vocabSize)
            putLong(NativeProofContract.RESULT_LOAD_MS, result.loadMs)
            putLong(NativeProofContract.RESULT_PROMPT_EVAL_MS, result.promptEvalMs)
            putLong(NativeProofContract.RESULT_GENERATION_MS, result.generationMs)
            putDouble(NativeProofContract.RESULT_TOKENS_PER_SECOND, result.tokensPerSecond)
            putInt(NativeProofContract.RESULT_TOKENS_GENERATED, result.tokensGenerated)
            putIntArray(NativeProofContract.RESULT_PROMPT_TOKEN_IDS, result.promptTokenIdsSample.toIntArray())
            putIntArray(NativeProofContract.RESULT_GENERATED_TOKEN_IDS, result.generatedTokenIdsSample.toIntArray())
            putString(NativeProofContract.RESULT_DECODED_TEXT, result.decodedText ?: result.text)
            putBoolean(NativeProofContract.RESULT_REAL_FORWARD_PASS, result.realForwardPass)
            putInt(NativeProofContract.RESULT_NATIVE_FORWARD_PASS_COUNT, result.nativeForwardPassCount)
            putBoolean(NativeProofContract.RESULT_LOGITS_COMPUTED, result.logitsComputed)
            putBoolean(NativeProofContract.RESULT_LOGITS_FINITE, result.logitsFinite)
            putString(NativeProofContract.RESULT_LOGITS_PREVIEW, result.logitsPreview)
            putBoolean(NativeProofContract.RESULT_SAMPLED_FROM_MODEL_LOGITS, result.sampledFromModelLogits)
            putBoolean(NativeProofContract.RESULT_SIMULATION, result.simulation)
            putInt(NativeProofContract.RESULT_SERVICE_PID, servicePid)
            putInt(NativeProofContract.RESULT_CONTROLLER_PID, controllerPid)
            putLong(NativeProofContract.RESULT_START_MS, startMs)
            putLong(NativeProofContract.RESULT_END_MS, endMs)
            putString(NativeProofContract.RESULT_DIAGNOSTICS, diagnostics)
            putString(NativeProofContract.RESULT_ERROR, if (result.success) null else result.errorCode ?: result.errorMessage ?: result.message)
        }
    }

    private fun buildGenerateBundle(
        stage: String,
        stageReached: String,
        controllerPid: Int,
        servicePid: Int,
        modelPath: String,
        modelSha256: String?,
        startMs: Long,
        endMs: Long,
        result: NativeLlamaResult,
        modelArch: String?,
        contextSize: Int,
        batchSize: Int,
        threadCount: Int,
        vocabSize: Int
    ): Bundle {
        return Bundle().apply {
            putString(NativeProofContract.RESULT_STAGE, stage)
            putString(NativeProofContract.RESULT_STAGE_REACHED, stageReached)
            putBoolean(NativeProofContract.RESULT_SUCCESS, result.success)
            putString(NativeProofContract.RESULT_MESSAGE, result.message ?: result.errorMessage)
            putString(NativeProofContract.RESULT_MODEL_PATH, modelPath)
            putString(NativeProofContract.RESULT_MODEL_SHA256, modelSha256)
            putString(NativeProofContract.RESULT_MODEL_ARCH, modelArch)
            putInt(NativeProofContract.RESULT_CONTEXT_SIZE, contextSize)
            putInt(NativeProofContract.RESULT_BATCH_SIZE, batchSize)
            putInt(NativeProofContract.RESULT_THREAD_COUNT, threadCount)
            putInt(NativeProofContract.RESULT_VOCAB_SIZE, vocabSize)
            putLong(NativeProofContract.RESULT_LOAD_MS, result.loadMs)
            putLong(NativeProofContract.RESULT_PROMPT_EVAL_MS, result.promptEvalMs)
            putLong(NativeProofContract.RESULT_GENERATION_MS, result.generationMs)
            putDouble(NativeProofContract.RESULT_TOKENS_PER_SECOND, result.tokensPerSecond)
            putInt(NativeProofContract.RESULT_TOKENS_GENERATED, result.tokensGenerated)
            putIntArray(
                NativeProofContract.RESULT_PROMPT_TOKEN_IDS,
                result.promptTokenIdsSample.toIntArray()
            )
            putIntArray(
                NativeProofContract.RESULT_GENERATED_TOKEN_IDS,
                result.generatedTokenIdsSample.toIntArray()
            )
            putString(NativeProofContract.RESULT_DECODED_TEXT, result.decodedText ?: result.text)
            putBoolean(NativeProofContract.RESULT_REAL_FORWARD_PASS, result.realForwardPass)
            putInt(NativeProofContract.RESULT_NATIVE_FORWARD_PASS_COUNT, result.nativeForwardPassCount)
            putBoolean(NativeProofContract.RESULT_LOGITS_COMPUTED, result.logitsComputed)
            putBoolean(NativeProofContract.RESULT_LOGITS_FINITE, result.logitsFinite)
            putString(NativeProofContract.RESULT_LOGITS_PREVIEW, result.logitsPreview)
            putBoolean(NativeProofContract.RESULT_SAMPLED_FROM_MODEL_LOGITS, result.sampledFromModelLogits)
            putBoolean(NativeProofContract.RESULT_SIMULATION, result.simulation)
            putInt(NativeProofContract.RESULT_SERVICE_PID, servicePid)
            putInt(NativeProofContract.RESULT_CONTROLLER_PID, controllerPid)
            putLong(NativeProofContract.RESULT_START_MS, startMs)
            putLong(NativeProofContract.RESULT_END_MS, endMs)
            putString(NativeProofContract.RESULT_DIAGNOSTICS, "success=${result.success}, ${result.message ?: result.errorMessage ?: "none"}")
            putString(NativeProofContract.RESULT_ERROR, if (result.success) null else result.errorCode ?: result.errorMessage ?: result.message)
        }
    }

    private fun buildErrorBundle(
        stage: String,
        stageReached: String = stage,
        controllerPid: Int,
        message: String,
        diagnostics: String,
        modelPath: String? = null,
        modelSha256: String? = null,
        startMs: Long = wallClockMs(),
        endMs: Long = wallClockMs()
    ): Bundle {
        return Bundle().apply {
            putString(NativeProofContract.RESULT_STAGE, stage)
            putString(NativeProofContract.RESULT_STAGE_REACHED, stageReached)
            putBoolean(NativeProofContract.RESULT_SUCCESS, false)
            putString(NativeProofContract.RESULT_MESSAGE, message)
            putString(NativeProofContract.RESULT_MODEL_PATH, modelPath)
            putString(NativeProofContract.RESULT_MODEL_SHA256, modelSha256)
            putString(NativeProofContract.RESULT_MODEL_ARCH, null)
            putInt(NativeProofContract.RESULT_CONTEXT_SIZE, 0)
            putInt(NativeProofContract.RESULT_BATCH_SIZE, 0)
            putInt(NativeProofContract.RESULT_THREAD_COUNT, 0)
            putInt(NativeProofContract.RESULT_VOCAB_SIZE, 0)
            putLong(NativeProofContract.RESULT_LOAD_MS, 0L)
            putLong(NativeProofContract.RESULT_PROMPT_EVAL_MS, 0L)
            putLong(NativeProofContract.RESULT_GENERATION_MS, 0L)
            putDouble(NativeProofContract.RESULT_TOKENS_PER_SECOND, 0.0)
            putInt(NativeProofContract.RESULT_TOKENS_GENERATED, 0)
            putIntArray(NativeProofContract.RESULT_PROMPT_TOKEN_IDS, intArrayOf())
            putIntArray(NativeProofContract.RESULT_GENERATED_TOKEN_IDS, intArrayOf())
            putString(NativeProofContract.RESULT_DECODED_TEXT, null)
            putBoolean(NativeProofContract.RESULT_REAL_FORWARD_PASS, false)
            putInt(NativeProofContract.RESULT_NATIVE_FORWARD_PASS_COUNT, 0)
            putBoolean(NativeProofContract.RESULT_LOGITS_COMPUTED, false)
            putBoolean(NativeProofContract.RESULT_LOGITS_FINITE, false)
            putString(NativeProofContract.RESULT_LOGITS_PREVIEW, null)
            putBoolean(NativeProofContract.RESULT_SAMPLED_FROM_MODEL_LOGITS, false)
            putBoolean(NativeProofContract.RESULT_SIMULATION, false)
            putInt(NativeProofContract.RESULT_SERVICE_PID, Process.myPid())
            putInt(NativeProofContract.RESULT_CONTROLLER_PID, controllerPid)
            putLong(NativeProofContract.RESULT_START_MS, startMs)
            putLong(NativeProofContract.RESULT_END_MS, endMs)
            putString(NativeProofContract.RESULT_DIAGNOSTICS, diagnostics)
            putString(NativeProofContract.RESULT_ERROR, message)
        }
    }

    private fun logStage(stage: NativeProofStage, subStage: String, event: String, detail: String? = null) {
        val suffix = detail?.let { " | $it" } ?: ""
        Log.i(
            TAG,
            "ts=${wallClockMs()} | pid=${Process.myPid()} | tid=${Process.myTid()} | stage=${stage.name} | substage=$subStage | event=$event$suffix"
        )
    }

    private fun wallClockMs(): Long = System.currentTimeMillis()

    private companion object {
        private const val TAG = "NativeProofService"
        private const val NOTIFICATION_ID = 20240615
        private const val DEFAULT_PROMPT = "Reply with the single word OK."
        private const val DEFAULT_TIMEOUT_MS = 900_000L
    }
}
