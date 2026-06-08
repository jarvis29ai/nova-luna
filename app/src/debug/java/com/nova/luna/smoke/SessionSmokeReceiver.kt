package com.nova.luna.smoke

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.nova.luna.brain.AssistantSession
import com.nova.luna.brain.CommandBrain
import com.nova.luna.brain.CommandSource
import com.nova.luna.memory.LocalPersonalMemoryStore
import com.nova.luna.memory.PersonalMemoryManager
import com.nova.luna.model.CommandResult
import com.nova.luna.voice.VoiceResponseManager
import java.io.File
import java.util.Locale

class SessionSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN_SESSION_SMOKE) {
            return
        }

        val payload = decodePayload(intent)
        val steps = parseSteps(payload)
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        Log.i(TAG, "session_smoke_received steps=${steps.size}")

        Thread {
            val responseManager = VoiceResponseManager(appContext)
            val memoryManager = PersonalMemoryManager(LocalPersonalMemoryStore(appContext))
            val session = AssistantSession(
                commandBrain = CommandBrain(appContext),
                responseManager = responseManager,
                memoryManager = memoryManager
            )
            val collector = SessionSmokeCollector()
            session.addSessionListener(collector)

            try {
                runSmoke(appContext, session, collector, steps)
            } catch (throwable: Throwable) {
                Log.e(TAG, "session_smoke_failed", throwable)
            } finally {
                runCatching { responseManager.release() }
                pendingResult.finish()
            }
        }.start()
    }

    private fun runSmoke(
        context: Context,
        session: AssistantSession,
        collector: SessionSmokeCollector,
        steps: List<SessionSmokeStep>
    ) {
        val reportFile = File(context.cacheDir, "session-smoke-results.txt")
        reportFile.writeText("")

        steps.forEachIndexed { index, step ->
            collector.beginStep(index + 1, step)
            when (step) {
                is SessionSmokeStep.Command -> {
                    Log.i(TAG, "step=${index + 1} source=${step.source} command=${step.command}")
                    session.executeCommand(step.command, step.source)
                }
                SessionSmokeStep.Confirm -> {
                    Log.i(TAG, "step=${index + 1} confirm")
                    session.confirmPendingAction()
                }
                SessionSmokeStep.Cancel -> {
                    Log.i(TAG, "step=${index + 1} cancel")
                    session.cancelPendingAction()
                }
                is SessionSmokeStep.Sleep -> {
                    Log.i(TAG, "step=${index + 1} sleep=${step.millis}")
                    runCatching { Thread.sleep(step.millis) }
                }
            }

            val result = collector.endStep()
            val line = formatStepResult(result)
            reportFile.appendText(line + System.lineSeparator())
            Log.i(TAG, line)
        }

        Log.i(TAG, "session_smoke_complete steps=${steps.size}")
        reportFile.appendText("session_smoke_complete | steps=${steps.size}" + System.lineSeparator())
    }

    private fun decodePayload(intent: Intent): String {
        val encoded = intent.getStringExtra(EXTRA_PAYLOAD_B64).orEmpty()
        if (encoded.isBlank()) return ""
        return String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun parseSteps(payload: String): List<SessionSmokeStep> {
        if (payload.isBlank()) {
            return emptyList()
        }

        return payload
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                when {
                    line.equals("CONFIRM", ignoreCase = true) -> SessionSmokeStep.Confirm
                    line.equals("CANCEL", ignoreCase = true) -> SessionSmokeStep.Cancel
                    line.startsWith("SLEEP::", ignoreCase = true) -> line.substringAfter("::").toLongOrNull()?.let { SessionSmokeStep.Sleep(it) }
                    line.startsWith("VOICE::", ignoreCase = true) -> SessionSmokeStep.Command(CommandSource.VOICE, line.substringAfter("::"))
                    line.startsWith("TEXT::", ignoreCase = true) -> SessionSmokeStep.Command(CommandSource.TEXT, line.substringAfter("::"))
                    else -> SessionSmokeStep.Command(CommandSource.TEXT, line)
                }
            }
            .toList()
    }

    private fun formatStepResult(result: SessionSmokeStepResult): String {
        return buildString {
            append("step=").append(result.index)
            append(" type=").append(result.type)
            append(" source=").append(result.source ?: "n/a")
            append(" command=").append(result.command ?: "n/a")
            append(" success=").append(result.commandResult?.success?.toString() ?: "n/a")
            append(" status=").append(result.commandResult?.status?.name ?: "n/a")
            append(" domain=").append(result.commandResult?.domain?.name ?: "n/a")
            append(" intentType=").append(result.commandResult?.intentType?.name ?: "n/a")
            append(" actionType=").append(result.commandResult?.actionType?.name ?: "n/a")
            append(" awaitingConfirmation=").append(result.commandResult?.awaitingConfirmation?.toString() ?: "n/a")
            append(" message=").append(sanitize(result.commandResult?.message ?: result.memoryResult?.userMessage ?: result.confirmationMessage ?: ""))
            append(" voice=").append(sanitize(result.voiceMessages.joinToString(" | ")))
            append(" memoryStatus=").append(result.memoryResult?.status?.name ?: "n/a")
        }
    }

    private fun sanitize(text: String): String {
        val trimmed = text.replace("\n", " ").replace("\r", " ").trim()
        return if (trimmed.isBlank()) "n/a" else trimmed
    }

    companion object {
        const val ACTION_RUN_SESSION_SMOKE = "com.nova.luna.debug.ACTION_RUN_SESSION_SMOKE"
        const val EXTRA_PAYLOAD_B64 = "com.nova.luna.debug.extra.PAYLOAD_B64"
        private const val TAG = "NovaLunaSessionSmoke"
    }
}

private sealed class SessionSmokeStep {
    data class Command(val source: CommandSource, val command: String) : SessionSmokeStep()
    data object Confirm : SessionSmokeStep()
    data object Cancel : SessionSmokeStep()
    data class Sleep(val millis: Long) : SessionSmokeStep()
}

private data class SessionSmokeStepResult(
    val index: Int,
    val type: String,
    val source: String? = null,
    val command: String? = null,
    val commandResult: CommandResult? = null,
    val memoryResult: com.nova.luna.memory.MemoryOperationResult? = null,
    val confirmationMessage: String? = null,
    val voiceMessages: List<String> = emptyList()
)

private class SessionSmokeCollector : AssistantSession.SessionListener {
    private var current: SessionSmokeStepResult = SessionSmokeStepResult(index = 0, type = "idle")

    fun beginStep(index: Int, step: SessionSmokeStep) {
        current = SessionSmokeStepResult(
            index = index,
            type = when (step) {
                is SessionSmokeStep.Command -> "command"
                SessionSmokeStep.Confirm -> "confirm"
                SessionSmokeStep.Cancel -> "cancel"
                is SessionSmokeStep.Sleep -> "sleep"
            },
            source = (step as? SessionSmokeStep.Command)?.source?.name,
            command = (step as? SessionSmokeStep.Command)?.command
        )
    }

    fun endStep(): SessionSmokeStepResult {
        return current
    }

    override fun onCommandResult(result: CommandResult, source: CommandSource) {
        current = current.copy(commandResult = result)
    }

    override fun onSpeakingStateChanged(isSpeaking: Boolean) = Unit

    override fun onVoiceResponseRequested(message: String) {
        current = current.copy(voiceMessages = current.voiceMessages + message)
    }

    override fun onThinkingStarted() = Unit

    override fun onActionStarted(label: String) = Unit

    override fun onConfirmationRequired(message: String, actionSummary: String?) {
        current = current.copy(confirmationMessage = message)
    }

    override fun onVoiceInputStateChanged(state: com.nova.luna.voice.VoiceInputState) = Unit

    override fun onPartialTranscriptReceived(transcript: String) = Unit

    override fun onDomainRouted(domain: com.nova.luna.brain.UnifiedDomain) = Unit

    override fun onMemoryOperationComplete(result: com.nova.luna.memory.MemoryOperationResult) {
        current = current.copy(memoryResult = result)
    }
}
