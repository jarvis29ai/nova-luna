package com.nova.luna.diagnostics

import android.app.Instrumentation
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class NativeProofStageResult(
    val stage: NativeProofStage,
    val stageReached: String,
    val success: Boolean,
    val message: String?,
    val modelPath: String?,
    val modelSha256: String?,
    val modelArch: String?,
    val contextSize: Int,
    val batchSize: Int,
    val threadCount: Int,
    val vocabSize: Int,
    val loadMs: Long,
    val promptEvalMs: Long,
    val generationMs: Long,
    val tokensPerSecond: Double,
    val tokensGenerated: Int,
    val promptTokenIds: List<Int>,
    val generatedTokenIds: List<Int>,
    val decodedText: String?,
    val realForwardPass: Boolean,
    val nativeForwardPassCount: Int,
    val logitsComputed: Boolean,
    val logitsFinite: Boolean,
    val logitsPreview: String?,
    val sampledFromModelLogits: Boolean,
    val simulation: Boolean,
    val servicePid: Int,
    val controllerPid: Int,
    val startMs: Long,
    val endMs: Long,
    val diagnostics: String?,
    val error: String?
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    companion object {
        fun fromBundle(stage: NativeProofStage, bundle: Bundle): NativeProofStageResult {
            return NativeProofStageResult(
                stage = stage,
                stageReached = bundle.getString(NativeProofContract.RESULT_STAGE_REACHED) ?: stage.name,
                success = bundle.getBoolean(NativeProofContract.RESULT_SUCCESS, false),
                message = bundle.getString(NativeProofContract.RESULT_MESSAGE),
                modelPath = bundle.getString(NativeProofContract.RESULT_MODEL_PATH),
                modelSha256 = bundle.getString(NativeProofContract.RESULT_MODEL_SHA256),
                modelArch = bundle.getString(NativeProofContract.RESULT_MODEL_ARCH),
                contextSize = bundle.getInt(NativeProofContract.RESULT_CONTEXT_SIZE, 0),
                batchSize = bundle.getInt(NativeProofContract.RESULT_BATCH_SIZE, 0),
                threadCount = bundle.getInt(NativeProofContract.RESULT_THREAD_COUNT, 0),
                vocabSize = bundle.getInt(NativeProofContract.RESULT_VOCAB_SIZE, 0),
                loadMs = bundle.getLong(NativeProofContract.RESULT_LOAD_MS, 0L),
                promptEvalMs = bundle.getLong(NativeProofContract.RESULT_PROMPT_EVAL_MS, 0L),
                generationMs = bundle.getLong(NativeProofContract.RESULT_GENERATION_MS, 0L),
                tokensPerSecond = bundle.getDouble(NativeProofContract.RESULT_TOKENS_PER_SECOND, 0.0),
                tokensGenerated = bundle.getInt(NativeProofContract.RESULT_TOKENS_GENERATED, 0),
                promptTokenIds = bundle.getIntArray(NativeProofContract.RESULT_PROMPT_TOKEN_IDS)?.toList().orEmpty(),
                generatedTokenIds = bundle.getIntArray(NativeProofContract.RESULT_GENERATED_TOKEN_IDS)?.toList().orEmpty(),
                decodedText = bundle.getString(NativeProofContract.RESULT_DECODED_TEXT),
                realForwardPass = bundle.getBoolean(NativeProofContract.RESULT_REAL_FORWARD_PASS, false),
                nativeForwardPassCount = bundle.getInt(NativeProofContract.RESULT_NATIVE_FORWARD_PASS_COUNT, 0),
                logitsComputed = bundle.getBoolean(NativeProofContract.RESULT_LOGITS_COMPUTED, false),
                logitsFinite = bundle.getBoolean(NativeProofContract.RESULT_LOGITS_FINITE, false),
                logitsPreview = bundle.getString(NativeProofContract.RESULT_LOGITS_PREVIEW),
                sampledFromModelLogits = bundle.getBoolean(NativeProofContract.RESULT_SAMPLED_FROM_MODEL_LOGITS, false),
                simulation = bundle.getBoolean(NativeProofContract.RESULT_SIMULATION, false),
                servicePid = bundle.getInt(NativeProofContract.RESULT_SERVICE_PID, -1),
                controllerPid = bundle.getInt(NativeProofContract.RESULT_CONTROLLER_PID, -1),
                startMs = bundle.getLong(NativeProofContract.RESULT_START_MS, 0L),
                endMs = bundle.getLong(NativeProofContract.RESULT_END_MS, 0L),
                diagnostics = bundle.getString(NativeProofContract.RESULT_DIAGNOSTICS),
                error = bundle.getString(NativeProofContract.RESULT_ERROR)
            )
        }
    }
}

class NativeProofProcessController(
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext,
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    private val timeoutMs: Long = 120_000L
) : Closeable {

    fun runStage(
        stage: NativeProofStage,
        modelId: String,
        debugOverridePath: String? = null,
        modelSha256: String? = null,
        prompt: String? = null,
        maxTokens: Int = 1,
        stageTimeoutMs: Long = timeoutMs
    ): NativeProofStageResult {
        val bindLatch = CountDownLatch(1)
        val serviceMessengerRef = AtomicReference<Messenger?>()
        val servicePidRef = AtomicReference<Int?>(null)
        val completed = AtomicBoolean(false)
        val resultRef = AtomicReference<NativeProofStageResult?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.i(TAG, "Service connected for stage=${stage.name}")
                serviceMessengerRef.set(service?.let { Messenger(it) })
                bindLatch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.i(TAG, "Service disconnected for stage=${stage.name}")
                serviceMessengerRef.set(null)
            }
        }

        val intent = Intent().setClassName(context.packageName, NativeProofContract.SERVICE_CLASS_NAME)
        Log.i(TAG, "Binding native proof service for stage=${stage.name}")
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            throw IllegalStateException("Failed to bind native proof service")
        }

        var replyThread: HandlerThread? = null
        var timeoutThread: Thread? = null
        try {
            Log.i(TAG, "Awaiting service bind for stage=${stage.name}")
            if (!bindLatch.await(30, TimeUnit.SECONDS)) {
                terminateIsolatedProofProcess(emptyList(), "service bind timed out for stage=${stage.name}")
                throw IllegalStateException("Timed out binding native proof service")
            }
            Log.i(TAG, "Service bind complete for stage=${stage.name}")

            servicePidRef.set(
                runCatching { resolveRunningProcessPids("${context.packageName}:native_proof").firstOrNull() }
                    .getOrNull()
            )
            Log.i(TAG, "Isolated proof pid for stage=${stage.name}: ${servicePidRef.get() ?: -1}")

            val resultLatch = CountDownLatch(1)
            val stageStartMs = SystemClock.elapsedRealtime()
            val deadlineMs = SystemClock.elapsedRealtime() + stageTimeoutMs.coerceAtLeast(1L)

            replyThread = HandlerThread("native-proof-reply-${stage.name.lowercase()}").apply { start() }
            val replyMessenger = Messenger(object : Handler(replyThread.looper) {
                override fun handleMessage(msg: Message) {
                    if (msg.what == NativeProofContract.MSG_RESULT) {
                        val result = NativeProofStageResult.fromBundle(stage, msg.data)
                        if (completed.compareAndSet(false, true)) {
                            resultRef.set(result)
                            resultLatch.countDown()
                            Log.i(TAG, "Proof stage result received for ${stage.name}")
                        } else {
                            Log.w(TAG, "Ignoring late proof result for ${stage.name}: ${result.stageReached}")
                        }
                    }
                }
            })

            timeoutThread = Thread({
                Log.i(TAG, "Controller watchdog started for stage=${stage.name} deadlineMs=$stageTimeoutMs")
                while (true) {
                    if (completed.get()) {
                        return@Thread
                    }

                    val remainingMs = deadlineMs - SystemClock.elapsedRealtime()
                    if (remainingMs <= 0L) {
                        break
                    }

                    runCatching {
                        Thread.sleep(minOf(remainingMs, WATCHDOG_POLL_MS))
                    }.onFailure { throwable ->
                        if (throwable !is InterruptedException) {
                            Log.e(TAG, "Controller watchdog interrupted for stage=${stage.name}: ${throwable.message}", throwable)
                        }
                    }
                }

                if (!completed.compareAndSet(false, true)) {
                    return@Thread
                }

                val knownPids = resolveRunningProcessPids("${context.packageName}:native_proof")
                val servicePid = knownPids.firstOrNull() ?: servicePidRef.get() ?: -1
                val timeoutResult = NativeProofStageResult(
                    stage = stage,
                    stageReached = "timeout:${stage.name}",
                    success = false,
                    message = "Stage '${stage.name}' timed out after ${stageTimeoutMs}ms",
                    modelPath = debugOverridePath,
                    modelSha256 = modelSha256,
                    modelArch = null,
                    contextSize = 0,
                    batchSize = 0,
                    threadCount = 0,
                    vocabSize = 0,
                    loadMs = 0L,
                    promptEvalMs = 0L,
                    generationMs = 0L,
                    tokensPerSecond = 0.0,
                    tokensGenerated = 0,
                    promptTokenIds = emptyList(),
                    generatedTokenIds = emptyList(),
                    decodedText = null,
                    realForwardPass = false,
                    nativeForwardPassCount = 0,
                    logitsComputed = false,
                    logitsFinite = false,
                    logitsPreview = null,
                    sampledFromModelLogits = false,
                    simulation = false,
                    servicePid = servicePid,
                    controllerPid = Process.myPid(),
                    startMs = stageStartMs,
                    endMs = SystemClock.elapsedRealtime(),
                    diagnostics = "timeout=true, deadlineMs=$deadlineMs, knownPids=$knownPids, modelPath=${debugOverridePath.orEmpty()}",
                    error = "STAGE_TIMEOUT"
                )
                resultRef.set(timeoutResult)
                resultLatch.countDown()
                Log.e(
                    TAG,
                    "Controller watchdog firing for stage=${stage.name} | servicePid=$servicePid | knownPids=$knownPids"
                )
                terminateIsolatedProofProcess(knownPids, "stage ${stage.name} timed out after ${stageTimeoutMs}ms")
            }, "native-proof-watchdog-${stage.name.lowercase()}").apply {
                isDaemon = true
                start()
            }

            val request = Bundle().apply {
                putString(NativeProofContract.EXTRA_STAGE, stage.name)
                putString(NativeProofContract.EXTRA_MODEL_ID, modelId)
                putString(NativeProofContract.EXTRA_MODEL_PATH, debugOverridePath)
                putString(NativeProofContract.EXTRA_MODEL_SHA256, modelSha256)
                putString(NativeProofContract.EXTRA_PROMPT, prompt)
                putInt(NativeProofContract.EXTRA_MAX_TOKENS, maxTokens)
                putLong(NativeProofContract.EXTRA_TIMEOUT_MS, stageTimeoutMs)
                putInt(NativeProofContract.EXTRA_CONTROLLER_PID, Process.myPid())
            }

            val message = Message.obtain(null, NativeProofContract.MSG_RUN_STAGE).apply {
                data = request
                replyTo = replyMessenger
            }

            serviceMessengerRef.get()
                ?: throw IllegalStateException("Native proof service messenger was not available")
            Log.i(TAG, "Starting proof stage ${stage.name} for modelId=$modelId timeoutMs=$stageTimeoutMs")
            serviceMessengerRef.get()!!.send(message)
            Log.i(TAG, "Sent proof stage ${stage.name} to isolated process")

            Log.i(TAG, "Awaiting proof stage result ${stage.name} for up to ${stageTimeoutMs}ms")
            if (!resultLatch.await(stageTimeoutMs + WATCHDOG_GRACE_MS, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Proof stage ${stage.name} failed to produce any result before fallback timeout")
                val knownPids = resolveRunningProcessPids("${context.packageName}:native_proof")
                terminateIsolatedProofProcess(knownPids, "stage ${stage.name} fallback wait timed out after ${stageTimeoutMs + WATCHDOG_GRACE_MS}ms")
                throw IllegalStateException("Stage '${stage.name}' timed out after ${stageTimeoutMs / 1000} seconds")
            }

            val result = resultRef.get() ?: throw IllegalStateException("Native proof service returned no result bundle")
            Log.i(TAG, "Completed proof stage ${stage.name} for modelId=$modelId")
            return result
        } finally {
            runCatching { context.unbindService(connection) }
            timeoutThread?.interrupt()
            replyThread?.quitSafely()
        }
    }

    private fun terminateIsolatedProofProcess(knownPids: List<Int>, reason: String) {
        val processName = "${context.packageName}:native_proof"
        Log.e(TAG, "Terminating isolated proof process $processName: $reason")
        val pids = if (knownPids.isNotEmpty()) knownPids else resolveRunningProcessPids(processName)
        if (pids.isEmpty()) {
            Log.e(TAG, "No running isolated proof process found for $processName")
            return
        }

        pids.forEach { pid ->
            Log.e(TAG, "Killing isolated proof pid=$pid for $processName")
            runCatching {
                Process.killProcess(pid)
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to kill isolated proof pid=$pid: ${throwable.message}", throwable)
            }
            runCatching {
                val shellOutput = executeShellCommand("run-as ${context.packageName} kill -9 $pid")
                if (shellOutput.isNotBlank()) {
                    Log.e(TAG, "run-as kill output for pid=$pid: ${shellOutput.trim()}")
                }
            }.onFailure { throwable ->
                Log.e(TAG, "Shell kill failed for isolated proof pid=$pid: ${throwable.message}", throwable)
            }
        }

        val remainingPids = resolveRunningProcessPids(processName)
        Log.e(TAG, "Remaining isolated proof pids after timeout handling: $remainingPids")
    }

    private fun resolveRunningProcessPids(processName: String): List<Int> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val fromActivityManager = activityManager?.runningAppProcesses
            ?.asSequence()
            ?.filter { it.processName == processName }
            ?.map { it.pid }
            ?.toList()
            .orEmpty()

        if (fromActivityManager.isNotEmpty()) {
            return fromActivityManager
        }

        val shellOutput = runCatching {
            executeShellCommand("pidof $processName").trim()
        }.getOrNull().orEmpty()

        return shellOutput
            .split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull() }
            .distinct()
    }

    private fun executeShellCommand(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    override fun close() {
        // No persistent resources beyond per-stage bindings.
    }

    private companion object {
        private const val TAG = "NativeProofController"
        private const val WATCHDOG_POLL_MS = 1_000L
        private const val WATCHDOG_GRACE_MS = 5_000L
    }
}
