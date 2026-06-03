package com.nova.luna.wear

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class WearMainActivity : ComponentActivity() {
    private lateinit var voiceHandler: WearVoiceHandler
    private lateinit var relayService: PhoneRelayService
    private lateinit var statusText: TextView
    private lateinit var commandInput: EditText

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMicRelay()
        } else {
            showStatus("Microphone permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear_main)

        voiceHandler = WearVoiceHandler(this)
        relayService = PhoneRelayService(this)
        statusText = findViewById(R.id.statusText)
        commandInput = findViewById(R.id.quickCommandInput)

        findViewById<Button>(R.id.micButton).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                startMicRelay()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val command = commandInput.text?.toString().orEmpty().trim()
            if (command.isBlank()) {
                showStatus("Enter a command first.")
                return@setOnClickListener
            }
            relayService.sendCommand(command) { success, message ->
                runOnUiThread {
                    showStatus(message)
                    if (!success) {
                        toast(message)
                    }
                }
            }
        }

        findViewById<Button>(R.id.homeButton).setOnClickListener {
            relayService.sendCommand("go home") { success, message ->
                runOnUiThread { showStatus(message) }
            }
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            relayService.sendCommand("stop listening") { success, message ->
                runOnUiThread { showStatus(message) }
            }
        }

        showStatus("Wear companion ready.")
    }

    override fun onDestroy() {
        voiceHandler.release()
        super.onDestroy()
    }

    private fun startMicRelay() {
        val started = voiceHandler.startListening { command ->
            relayService.sendCommand(command) { success, message ->
                runOnUiThread {
                    showStatus(message)
                    if (!success) toast(message)
                }
            }
        }
        if (started) {
            showStatus("Listening on watch...")
        } else {
            showStatus("Speech recognition is unavailable.")
        }
    }

    private fun showStatus(message: String) {
        statusText.text = message
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

