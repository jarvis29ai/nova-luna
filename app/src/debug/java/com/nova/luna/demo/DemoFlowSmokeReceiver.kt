package com.nova.luna.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nova.luna.brain.CommandBrain
import com.nova.luna.brain.UnifiedDomain
import com.nova.luna.service.NovaAccessibilityService
import java.io.File

class DemoFlowSmokeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN_DEMO_SMOKE) return

        val flowIdStr = intent.getStringExtra(EXTRA_FLOW_ID)
        val flowsToRun = if (flowIdStr == null || flowIdStr == "ALL") {
            DemoFlowId.values().toList()
        } else {
            listOf(DemoFlowId.valueOf(flowIdStr))
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        Thread {
            try {
                runDemoFlows(appContext, flowsToRun)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Demo smoke failed", throwable)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun runDemoFlows(context: Context, flows: List<DemoFlowId>) {
        val results = mutableListOf<DemoFlowResult>()

        flows.forEach { flowId ->
            Log.i(TAG, "Starting flow: $flowId")
            resetUi()

            val brain = CommandBrain(context)
            val command = getCommandForFlow(flowId)
            val expectedDomain = getExpectedDomainForFlow(flowId)

            val result = brain.process(command)
            
            val demoResult = DemoFlowResult(
                flowId = flowId,
                commandUsed = command,
                expectedDomain = expectedDomain,
                actualDomain = result.domain,
                expectedOutcome = "Success/Handled",
                actualOutcome = result.message,
                safetyConfirmationShown = result.awaitingConfirmation,
                passStatus = if (result.success || result.awaitingConfirmation) DemoFlowStatus.PASS else DemoFlowStatus.FAIL
            )
            results.add(demoResult)
            Log.i(TAG, "Result for $flowId: ${demoResult.passStatus}")
        }

        writeReport(context, results)
    }

    private fun resetUi() {
        NovaAccessibilityService.instance?.goHome()
        Thread.sleep(1000L)
    }

    private fun getCommandForFlow(flowId: DemoFlowId): String {
        return when (flowId) {
            DemoFlowId.OPEN_APP -> "Luna open YouTube"
            DemoFlowId.PLAY_MUSIC -> "Luna play Arijit Singh"
            DemoFlowId.YOUTUBE_SEARCH -> "Luna search YouTube for MrBeast"
            DemoFlowId.SCROLL_SELECT_MEDIA -> "Luna scroll down"
            DemoFlowId.READ_SUMMARIZE_MESSAGE -> "Luna summarize my messages"
            DemoFlowId.CONTENT_PROMPT -> "Luna create PPT on AI"
            DemoFlowId.FOOD_ORDER -> "Luna order pizza"
            DemoFlowId.GROCERY_COMPARE -> "Luna compare milk prices"
            DemoFlowId.CAB_BOOKING -> "Luna book cab to airport"
            DemoFlowId.SHOPPING_COMPARE -> "Luna buy phone under 30000"
        }
    }

    private fun getExpectedDomainForFlow(flowId: DemoFlowId): UnifiedDomain {
        return when (flowId) {
            DemoFlowId.OPEN_APP -> UnifiedDomain.PHONE_CONTROL
            DemoFlowId.PLAY_MUSIC -> UnifiedDomain.MUSIC
            DemoFlowId.YOUTUBE_SEARCH -> UnifiedDomain.MEDIA
            DemoFlowId.SCROLL_SELECT_MEDIA -> UnifiedDomain.PHONE_CONTROL
            DemoFlowId.READ_SUMMARIZE_MESSAGE -> UnifiedDomain.COMMUNICATION
            DemoFlowId.CONTENT_PROMPT -> UnifiedDomain.CONTENT
            DemoFlowId.FOOD_ORDER -> UnifiedDomain.FOOD
            DemoFlowId.GROCERY_COMPARE -> UnifiedDomain.GROCERY
            DemoFlowId.CAB_BOOKING -> UnifiedDomain.CAB
            DemoFlowId.SHOPPING_COMPARE -> UnifiedDomain.SHOPPING
        }
    }

    private fun writeReport(context: Context, results: List<DemoFlowResult>) {
        val reportFile = File(context.cacheDir, "phase-6-demo-results.txt")
        val sb = StringBuilder()
        sb.append("Phase 6 Demo Flow Results\n")
        sb.append("=========================\n\n")
        results.forEach { res ->
            sb.append("Flow: ${res.flowId}\n")
            sb.append("Command: ${res.commandUsed}\n")
            sb.append("Domain: Expected=${res.expectedDomain}, Actual=${res.actualDomain}\n")
            sb.append("Status: ${res.passStatus}\n")
            sb.append("Message: ${res.actualOutcome}\n")
            sb.append("Safety Confirmation: ${res.safetyConfirmationShown}\n")
            sb.append("-------------------------\n")
        }
        reportFile.writeText(sb.toString())
        Log.i(TAG, "Report written to ${reportFile.absolutePath}")
    }

    companion object {
        private const val TAG = "DemoFlowSmoke"
        const val ACTION_RUN_DEMO_SMOKE = "com.nova.luna.debug.ACTION_RUN_DEMO_SMOKE"
        const val EXTRA_FLOW_ID = "com.nova.luna.debug.extra.FLOW_ID"
    }
}
