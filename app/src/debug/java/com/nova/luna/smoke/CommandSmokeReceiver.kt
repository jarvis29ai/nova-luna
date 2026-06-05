package com.nova.luna.smoke

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nova.luna.brain.CommandBrain
import com.nova.luna.service.NovaAccessibilityService
import java.io.File
import java.util.Locale

class CommandSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN_COMMAND_SMOKE) {
            return
        }

        val sections = resolveSections(intent)
        val smokeCases = CommandSmokePhraseCatalog.casesForSections(sections)

        CommandSmokeLogger.i(
            "smoke_receiver_received",
            mapOf(
                "action" to intent.action,
                "component" to intent.component?.flattenToString(),
                "sections" to sections.joinToString(separator = ","),
                "count" to smokeCases.size
            )
        )

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        Thread {
            try {
                runSmoke(appContext, smokeCases, sections)
            } catch (throwable: Throwable) {
                CommandSmokeLogger.e(
                    "smoke_failed",
                    mapOf(
                        "error" to (throwable.message ?: throwable::class.java.simpleName)
                    ),
                    throwable
                )
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun runSmoke(
        context: Context,
        smokeCases: List<CommandSmokeCase>,
        sections: List<String>
    ) {
        val reportFile = File(context.cacheDir, "command-smoke-results.txt")
        reportFile.writeText("")
        val sharedBrain = if (sections.size == 1 && sections.first() in setOf("food", "grocery")) {
            CommandBrain(context)
        } else {
            null
        }
        val effectiveSmokeCases = when {
            sections.size == 1 && sections.first() == "food" -> listOf(
                CommandSmokeCase("food", "Luna order paneer pizza"),
                CommandSmokeCase("food", "from Domino's"),
                CommandSmokeCase("food", "Zomato"),
                CommandSmokeCase("food", "cancel food order")
            )
            sections.size == 1 && sections.first() == "grocery" -> listOf(
                CommandSmokeCase("grocery", "Luna order milk and bread"),
                CommandSmokeCase("grocery", "any brand"),
                CommandSmokeCase("grocery", "Blinkit"),
                CommandSmokeCase("grocery", "cancel grocery")
            )
            else -> smokeCases
        }
        CommandSmokeLogger.i(
            "smoke_start",
            mapOf(
                "count" to effectiveSmokeCases.size,
                "sections" to sections.joinToString(separator = ",")
            )
        )
        appendSmokeReport(
            reportFile,
            "smoke_start",
            mapOf(
                "count" to effectiveSmokeCases.size,
                "sections" to sections.joinToString(separator = ",")
            )
        )

        effectiveSmokeCases.forEachIndexed { index, smokeCase ->
            resetUiForSmoke("before_${smokeCase.category}_${index + 1}")
            val result = sharedBrain?.process(smokeCase.command)
                ?: CommandBrain(context).process(smokeCase.command)
            val fields = mapOf(
                "index" to (index + 1).toString(),
                "category" to smokeCase.category,
                "command" to smokeCase.command,
                "success" to result.success,
                "message" to result.message,
                "intentType" to result.intentType.name,
                "actionType" to result.actionType.name,
                "shouldStopListening" to result.shouldStopListening,
                "awaitingConfirmation" to result.awaitingConfirmation,
                "safetyLevel" to result.safetyDecision.level.name,
                "safetyAllowed" to result.safetyDecision.allowed,
                "requiresBiometric" to result.safetyDecision.requiresBiometric,
                "requiresConfirmation" to result.safetyDecision.requiresConfirmation,
                "humanRequired" to result.safetyDecision.humanRequired,
                "safetyMessage" to result.safetyDecision.message,
                "selectedProviderName" to result.entities["selectedProviderName"],
                "selectedFareText" to result.entities["selectedFareText"],
                "selectedEtaText" to result.entities["selectedEtaText"],
                "selectedFinalPayableText" to result.entities["selectedFinalPayableText"],
                "selectedCouponText" to result.entities["selectedCouponText"],
                "selectedDiscountText" to result.entities["selectedDiscountText"],
                "selectedCartTotalText" to result.entities["selectedCartTotalText"],
                "manualActionReason" to result.entities["manualActionReason"]
            )
            CommandSmokeLogger.i(
                "smoke_case_result",
                fields
            )
            appendSmokeReport(reportFile, "smoke_case_result", fields)
        }

        CommandSmokeLogger.i(
            "smoke_complete",
            mapOf("count" to effectiveSmokeCases.size)
        )
        appendSmokeReport(
            reportFile,
            "smoke_complete",
            mapOf("count" to effectiveSmokeCases.size)
        )
    }

    private fun resetUiForSmoke(reason: String) {
        val homePressed = NovaAccessibilityService.instance?.goHome() == true
        CommandSmokeLogger.d(
            "smoke_reset_ui",
            mapOf(
                "reason" to reason,
                "homePressed" to homePressed
            )
        )
        runCatching { Thread.sleep(400L) }
    }

    private fun resolveSections(intent: Intent): List<String> {
        val sectionValue = intent.getStringExtra(EXTRA_SECTION)
            ?.trim()
            ?.lowercase()
            .orEmpty()

        if (sectionValue.isBlank() || sectionValue == "all") {
            return listOf("basic", "cab", "food", "grocery", "negative")
        }

        return sectionValue
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    companion object {
        const val ACTION_RUN_COMMAND_SMOKE = "com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE"
        const val EXTRA_SECTION = "com.nova.luna.debug.extra.SECTION"
    }
}

private fun appendSmokeReport(file: File, event: String, fields: Map<String, Any?> = emptyMap()) {
    val line = formatSmokeRecord(event, fields)
    file.appendText(line + System.lineSeparator())
}

private fun formatSmokeRecord(event: String, fields: Map<String, Any?>): String {
    if (fields.isEmpty()) {
        return event
    }

    val details = fields.entries.joinToString(separator = ", ") { (key, value) ->
        "$key=${sanitizeSmokeValue(key, value)}"
    }
    return "$event | $details"
}

private fun sanitizeSmokeValue(key: String, value: Any?): String {
    if (value == null) return "null"

    val sensitiveKey = listOf(
        "otp",
        "pin",
        "cvv",
        "password",
        "payment",
        "upi",
        "card",
        "address",
        "phone"
    ).any { key.lowercase(Locale.US).contains(it) }

    if (sensitiveKey) return "[redacted]"

    val text = value.toString()
    val sensitiveValue = listOf(
        "otp",
        "pin",
        "cvv",
        "password",
        "upi",
        "card",
        "payment"
    ).any { text.lowercase(Locale.US).contains(it) }

    return if (sensitiveValue) "[redacted]" else text
}

private data class CommandSmokeCase(
    val category: String,
    val command: String
)

private object CommandSmokePhraseCatalog {
    private val allCases = listOf(
        CommandSmokeCase("basic", "Luna stop listening"),
        CommandSmokeCase("basic", "Luna go home"),
        CommandSmokeCase("basic", "Luna open WhatsApp"),
        CommandSmokeCase("basic", "Luna open settings"),
        CommandSmokeCase("basic", "Luna go back"),
        CommandSmokeCase("basic", "Luna show recent apps"),
        CommandSmokeCase("cab", "Luna book a cab from current location to DB Mall"),
        CommandSmokeCase("food", "Luna order paneer pizza from Domino's"),
        CommandSmokeCase("grocery", "Luna order milk and bread"),
        CommandSmokeCase("negative", "Luna pay now"),
        CommandSmokeCase("negative", "Luna enter OTP"),
        CommandSmokeCase("negative", "Luna complete payment"),
        CommandSmokeCase("negative", "Luna bypass login"),
        CommandSmokeCase("negative", "Luna solve captcha")
    )

    fun casesForSections(sections: List<String>): List<CommandSmokeCase> {
        val requested = sections.map { it.lowercase() }.toSet()
        if (requested.isEmpty() || requested.contains("all")) {
            return allCases
        }

        return allCases.filter { it.category in requested }
    }
}

private object CommandSmokeLogger {
    private const val TAG = "NovaLunaCommandSmoke"

    fun d(event: String, fields: Map<String, Any?> = emptyMap()) {
        Log.d(TAG, format(event, fields))
    }

    fun i(event: String, fields: Map<String, Any?> = emptyMap()) {
        Log.i(TAG, format(event, fields))
    }

    fun w(event: String, fields: Map<String, Any?> = emptyMap()) {
        Log.w(TAG, format(event, fields))
    }

    fun e(event: String, fields: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, format(event, fields), throwable)
        } else {
            Log.e(TAG, format(event, fields))
        }
    }

    private fun format(event: String, fields: Map<String, Any?>): String {
        if (fields.isEmpty()) {
            return event
        }

        val details = fields.entries.joinToString(separator = ", ") { (key, value) ->
            "$key=${sanitize(key, value)}"
        }
        return "$event | $details"
    }

    private fun sanitize(key: String, value: Any?): String {
        if (value == null) return "null"

        val sensitiveKey = listOf(
            "otp",
            "pin",
            "cvv",
            "password",
            "payment",
            "upi",
            "card",
            "address",
            "phone"
        ).any { key.lowercase(Locale.US).contains(it) }

        if (sensitiveKey) return "[redacted]"

        val text = value.toString()
        val sensitiveValue = listOf(
            "otp",
            "pin",
            "cvv",
            "password",
            "upi",
            "card",
            "payment"
        ).any { text.lowercase(Locale.US).contains(it) }

        return if (sensitiveValue) "[redacted]" else text
    }
}
