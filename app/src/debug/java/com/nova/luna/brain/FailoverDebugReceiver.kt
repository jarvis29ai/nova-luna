package com.nova.luna.brain
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nova.luna.model.BrainModelRole
import java.io.File
class FailoverDebugReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FORCE_UNAVAILABLE = "com.nova.luna.brain.FORCE_UNAVAILABLE"
        const val EXTRA_ROLE = "role"
        const val EXTRA_AVAILABLE = "available"
        const val ACTION_RESET_ALL = "com.nova.luna.brain.RESET_ALL"
        const val ACTION_CHECK_STATUS = "com.nova.luna.brain.CHECK_STATUS"
        const val ACTION_PROBE_CURRENT_STATE = "com.nova.luna.brain.PROBE_CURRENT_STATE"
        const val ACTION_FORCE_AVAILABLE = "com.nova.luna.brain.FORCE_AVAILABLE"
    }
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("FailoverDebug", "Received action: $action")
        try {
            when (action) {
                ACTION_FORCE_UNAVAILABLE -> {
                    forceUnavailable(context, intent)
                }
                ACTION_FORCE_AVAILABLE -> {
                    forceAvailable(context, intent)
                }
                ACTION_RESET_ALL -> {
                    resetAll(context)
                }
                ACTION_CHECK_STATUS -> {
                    checkStatus(context)
                }
                ACTION_PROBE_CURRENT_STATE -> {
                    probeCurrentState(context)
                }
                else -> {
                    Log.w("FailoverDebug", "Unknown action: $action")
                }
            }
        } catch (e: Exception) {
            Log.e("FailoverDebug", "Error processing action: $action", e)
        }
    }
    private fun forceUnavailable(context: Context, intent: Intent) {
        val roleWire = intent.getStringExtra(EXTRA_ROLE) ?: return
        val available = intent.getBooleanExtra(EXTRA_AVAILABLE, false)
        Log.i("FailoverDebug", "Forcing role=$roleWire, available=$available")
        try {
            val role = BrainModelRole.fromWireValue(roleWire)
            val markerName = role?.let { FailoverOverrideMarkers.markerFileName(it) }
            if (markerName == null) {
                Log.w("FailoverDebug", "Role $roleWire does not support failover override")
                return
            }
            val filesDir = context.filesDir
            val markerFile = File(filesDir, markerName)
            when {
                available -> {
                    if (markerFile.exists()) {
                        markerFile.delete()
                        Log.i("FailoverDebug", "Deleted marker file for role: $roleWire")
                    }
                }
                else -> {
                    markerFile.writeText("")
                    Log.i("FailoverDebug", "Created marker file for role: $roleWire")
                }
            }
        } catch (e: Exception) {
            Log.e("FailoverDebug", "Error forcing unavailable for role: $roleWire", e)
        }
    }
    private fun forceAvailable(context: Context, intent: Intent) {
        val role = intent.getStringExtra(EXTRA_ROLE) ?: return
        Log.i("FailoverDebug", "Forcing role=$role available")
        forceUnavailable(context, Intent().apply {
            action = ACTION_FORCE_UNAVAILABLE
            putExtra(EXTRA_ROLE, role)
            putExtra(EXTRA_AVAILABLE, true)
        })
    }
    private fun resetAll(context: Context) {
        Log.i("FailoverDebug", "Resetting all phase 35 controls")
        try {
            val filesDir = context.filesDir
            val markers = FailoverOverrideMarkers.allMarkerFileNames()
            markers.forEach { markerName ->
                val markerFile = File(filesDir, markerName)
                if (markerFile.exists()) {
                    markerFile.delete()
                    Log.i("FailoverDebug", "Deleted marker file: $markerName")
                }
            }
            Log.i("FailoverDebug", "All phase 35 controls reset")
        } catch (e: Exception) {
            Log.e("FailoverDebug", "Error resetting all controls", e)
        }
    }
    private fun checkStatus(context: Context) {
        Log.i("FailoverDebug", "Checking phase 35 status")
        try {
            val brain = CommandBrain(context.applicationContext)
            Log.i("FailoverDebug", "CommandBrain initialized for status check")
            val status = brain.process("status")
            Log.i("FailoverDebug", "Status check completed: ${status.message}")
            Log.i("FailoverDebug", "Status domain: ${status.domain}")
        } catch (e: Exception) {
            Log.e("FailoverDebug", "Error during status check", e)
        }
    }
    private fun probeCurrentState(context: Context) {
        Log.i("FailoverDebug", "Probing current model states")
        try {
            val brain = CommandBrain(context.applicationContext)
            listOf(
                "what is a transformer",
                "gemma explain this",
                "kya karu please explain in hindi"
            ).forEach { command ->
                Log.i("FailoverDebug", "Probing with: '$command'")
                val result = brain.process(command)
                Log.i("FailoverDebug", "Result: ${result.message}")
                Log.i("FailoverDebug", "Domain: ${result.domain}")
            }
        } catch (e: Exception) {
            Log.e("FailoverDebug", "Error during current state probe", e)
        }
    }
}