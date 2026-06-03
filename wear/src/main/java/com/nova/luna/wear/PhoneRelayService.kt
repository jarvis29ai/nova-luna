package com.nova.luna.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.nova.luna.shared.MessageChannels

class PhoneRelayService(private val context: Context) {
    fun sendCommand(command: String, onResult: (Boolean, String) -> Unit) {
        sendToFirstConnectedNode(
            path = MessageChannels.COMMAND,
            payload = command,
            onResult = onResult
        )
    }

    fun sendReply(reply: String, onResult: (Boolean, String) -> Unit) {
        sendToFirstConnectedNode(
            path = MessageChannels.REPLY,
            payload = reply,
            onResult = onResult
        )
    }

    private fun sendToFirstConnectedNode(
        path: String,
        payload: String,
        onResult: (Boolean, String) -> Unit
    ) {
        // Scaffold note: in a fuller version you may cache the phone node id after pairing.
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                val node = nodes.firstOrNull()
                if (node == null) {
                    onResult(false, "No connected phone node was found.")
                    return@addOnSuccessListener
                }

                Wearable.getMessageClient(context)
                    .sendMessage(node.id, path, payload.toByteArray(Charsets.UTF_8))
                    .addOnSuccessListener {
                        onResult(true, "Sent to ${node.displayName}.")
                    }
                    .addOnFailureListener { throwable ->
                        onResult(false, throwable.localizedMessage ?: "Unknown relay failure.")
                    }
            }
            .addOnFailureListener { throwable ->
                Log.e(TAG, "Failed to query connected nodes", throwable)
                onResult(false, throwable.localizedMessage ?: "Unable to query connected nodes.")
            }
    }

    companion object {
        private const val TAG = "PhoneRelayService"
    }
}

