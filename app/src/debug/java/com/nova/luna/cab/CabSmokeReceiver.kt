package com.nova.luna.cab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nova.luna.brain.CommandBrain
import com.nova.luna.service.NovaAccessibilityService
import com.nova.luna.util.PermissionUtils

class CabSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN_CAB_SMOKE) {
            return
        }

        CabLogger.i(
            "smoke_receiver_received",
            mapOf(
                "action" to intent.action,
                "component" to intent.component?.flattenToString()
            )
        )

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        Thread {
            try {
                runSmoke(appContext)
            } catch (throwable: Throwable) {
                CabLogger.e(
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

    private fun runSmoke(context: Context) {
        val accessibilityService = CabAccessibilityService()
        resetUiForSmoke("preflight")
        val preflightSnapshot = waitForSnapshot(accessibilityService)
        CabLogger.i(
            "smoke_preflight",
            mapOf(
                "packageName" to preflightSnapshot?.sourcePackageName,
                "inspectable" to preflightSnapshot?.sourcePackageName?.let(accessibilityService::isInspectableCabPackage),
                "manualActionReason" to preflightSnapshot?.let(accessibilityService::detectManualActionRequired),
                "hasAccessibilityPermission" to PermissionUtils.hasAccessibilityPermission(context),
                "hasLocationPermission" to PermissionUtils.hasLocationPermission(context)
            )
        )

        val installedProviders = CabProviderRegistry(context.packageManager)
            .installedProviders()
            .toSet()
        CabLogger.i(
            "smoke_installed_providers",
            mapOf(
                "providers" to installedProviders.joinToString(separator = ",") { it.name }
            )
        )

        val scenarios = buildScenarios(installedProviders)
        if (scenarios.isEmpty()) {
            CabLogger.w("smoke_no_scenarios", emptyMap())
            return
        }

        scenarios.forEach { scenario ->
            resetUiForSmoke("before_${scenario.name}")
            runScenario(context, scenario)
        }

        CabLogger.i(
            "smoke_complete",
            mapOf("scenarioCount" to scenarios.size)
        )
    }

    private fun runScenario(context: Context, scenario: SmokeScenario) {
        CabLogger.i(
            "smoke_scenario_start",
            mapOf(
                "scenario" to scenario.name,
                "commands" to scenario.commands.joinToString(separator = " | ")
            )
        )

        val brain = CommandBrain(context)
        scenario.commands.forEachIndexed { index, command ->
            val result = brain.process(command)
            CabLogger.i(
                "smoke_step_result",
                mapOf(
                    "scenario" to scenario.name,
                    "step" to (index + 1).toString(),
                    "command" to command,
                    "success" to result.success,
                    "message" to result.message,
                    "intentType" to result.intentType.name,
                    "actionType" to result.actionType.name,
                    "cabState" to result.entities["cabState"],
                    "currentState" to result.entities["currentState"],
                    "selectedProvider" to result.entities["selectedProviderName"],
                    "selectedFareText" to result.entities["selectedFareText"],
                    "selectedFareAmount" to result.entities["selectedFareAmount"],
                    "selectedEtaText" to result.entities["selectedEtaText"],
                    "selectedCouponText" to result.entities["selectedCouponText"],
                    "selectedDiscountText" to result.entities["selectedDiscountText"],
                    "manualActionReason" to result.entities["manualActionReason"]
                )
            )
        }

        CabLogger.i(
            "smoke_scenario_end",
            mapOf("scenario" to scenario.name)
        )
    }

    private fun buildScenarios(installedProviders: Set<CabProvider>): List<SmokeScenario> {
        val scenarios = mutableListOf<SmokeScenario>()

        scenarios += SmokeScenario(
            name = "generic_cheapest",
            commands = listOf(
                "book cab from current location to DB Mall",
                "mini",
                "cheapest",
                "cancel cab booking"
            )
        )

        if (CabProvider.UBER in installedProviders) {
            scenarios += SmokeScenario(
                name = "uber_current_location",
                commands = listOf(
                    "book Uber from current location to DB Mall",
                    "mini",
                    "Uber",
                    "cancel cab booking"
                )
            )
        }

        if (CabProvider.RAPIDO in installedProviders) {
            scenarios += SmokeScenario(
                name = "rapido_current_location",
                commands = listOf(
                    "book Rapido from current location to DB Mall",
                    "mini",
                    "Rapido",
                    "cancel cab booking"
                )
            )
        }

        if (CabProvider.OLA in installedProviders) {
            scenarios += SmokeScenario(
                name = "ola_current_location",
                commands = listOf(
                    "book Ola from current location to DB Mall",
                    "mini",
                    "Ola",
                    "cancel cab booking"
                )
            )
        }

        if (CabProvider.INDRIVE in installedProviders) {
            scenarios += SmokeScenario(
                name = "indrive_current_location",
                commands = listOf(
                    "book inDrive from current location to DB Mall",
                    "mini",
                    "inDrive",
                    "cancel cab booking"
                )
            )
        }

        return scenarios
    }

    private fun waitForSnapshot(
        accessibilityService: CabAccessibilityService,
        attempts: Int = 10,
        delayMs: Long = 200L
    ): CabScreenSnapshot? {
        repeat(attempts) { attempt ->
            val snapshot = accessibilityService.captureScreenSnapshot()
            if (snapshot != null) {
                return snapshot
            }

            if (attempt < attempts - 1) {
                runCatching { Thread.sleep(delayMs) }
            }
        }

        return null
    }

    private fun resetUiForSmoke(reason: String) {
        val homePressed = NovaAccessibilityService.instance?.goHome() == true
        CabLogger.d(
            "smoke_reset_ui",
            mapOf(
                "reason" to reason,
                "homePressed" to homePressed
            )
        )
        runCatching { Thread.sleep(500L) }
    }

    private data class SmokeScenario(
        val name: String,
        val commands: List<String>
    )

    companion object {
        const val ACTION_RUN_CAB_SMOKE = "com.nova.luna.debug.ACTION_RUN_CAB_SMOKE"
    }
}
