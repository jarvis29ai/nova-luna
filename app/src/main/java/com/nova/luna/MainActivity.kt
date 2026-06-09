package com.nova.luna

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nova.luna.history.CommandHistoryActivity
import com.nova.luna.data.PreferencesManager
import com.nova.luna.model.VoiceProfile
import com.nova.luna.service.VoiceCommandService
import com.nova.luna.util.*
import kotlinx.coroutines.launch

import com.nova.luna.brain.AssistantSession
import com.nova.luna.brain.CommandBrain
import com.nova.luna.brain.CommandSource
import com.nova.luna.voice.*
import com.nova.luna.model.CommandResult
import com.nova.luna.ui.*

class MainActivity : AppCompatActivity(), VoiceInputController.VoiceInputListener, AssistantSession.SessionListener {
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var profileSpinner: Spinner
    private lateinit var permissionStatusText: TextView
    private lateinit var serviceStatusText: TextView
    private lateinit var voiceTranscriptText: TextView
    private lateinit var voiceResponseText: TextView
    private lateinit var tapToSpeakButton: Button
    private lateinit var profileAdapter: ArrayAdapter<String>

    private lateinit var voiceInputController: VoiceInputController
    private lateinit var assistantSession: AssistantSession
    private lateinit var voiceResponseManager: VoiceResponseManager
    private lateinit var popupController: AssistantPopupController

    private lateinit var readinessChecker: PrototypeReadinessChecker
    private lateinit var healthMonitor: RuntimeHealthMonitor
    private lateinit var onboardingController: OnboardingController
    private lateinit var settingsController: SettingsController
    private lateinit var diagnosticsReporter: SafeDiagnosticsReporter

    private var suppressProfileCallback = false
    private var pendingStartAfterPermissionGrant = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        refreshPermissionStatus()
        val micGranted = result[Manifest.permission.RECORD_AUDIO] == true
        val notificationsGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
            result[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }

        if (pendingStartAfterPermissionGrant && micGranted && notificationsGranted) {
            pendingStartAfterPermissionGrant = false
            startVoiceService()
        } else if (pendingStartAfterPermissionGrant) {
            pendingStartAfterPermissionGrant = false
            showToast("Grant microphone and notification permissions to start listening.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferencesManager = PreferencesManager(this)
        profileSpinner = findViewById(R.id.voiceProfileSpinner)
        permissionStatusText = findViewById(R.id.permissionStatusText)
        serviceStatusText = findViewById(R.id.serviceStatusText)
        voiceTranscriptText = findViewById(R.id.voiceTranscriptText)
        voiceResponseText = findViewById(R.id.voiceResponseText)
        tapToSpeakButton = findViewById(R.id.tapToSpeakButton)

        voiceResponseManager = VoiceResponseManager(this)
        voiceInputController = VoiceInputController(this)
        voiceInputController.setVoiceInputListener(this)

        val personalMemoryStore = com.nova.luna.memory.LocalPersonalMemoryStore(this)
        val personalMemoryManager = com.nova.luna.memory.PersonalMemoryManager(personalMemoryStore)
        assistantSession = AssistantSession(
            commandBrain = CommandBrain(this, personalMemoryStore = personalMemoryStore),
            responseManager = voiceResponseManager,
            memoryManager = personalMemoryManager
        )
        assistantSession.addSessionListener(this)

        readinessChecker = PrototypeReadinessChecker(this)
        healthMonitor = RuntimeHealthMonitor(this)
        onboardingController = OnboardingController(this)
        settingsController = SettingsController(assistantSession, personalMemoryManager)
        diagnosticsReporter = SafeDiagnosticsReporter(this, readinessChecker, healthMonitor, personalMemoryStore)

        popupController = AssistantPopupController(findViewById(R.id.popupContainer), assistantSession) { event ->
            when (event) {
                AssistantPopupEvent.MIC_TAPPED -> {
                    PerformanceTracker.start("voice_input")
                    voiceInputController.startListening()
                }
                AssistantPopupEvent.CONTINUE_TAPPED -> {
                    PerformanceTracker.start("action_execution")
                    assistantSession.confirmPendingAction()
                }
                else -> {}
            }
        }
        assistantSession.addSessionListener(popupController)

        if (!onboardingController.isComplete()) {
            showOnboarding()
        }

        profileAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            VoiceProfile.values().map { it.displayName }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        profileSpinner.adapter = profileAdapter

        setupButtons()
        observeVoiceProfile()
        refreshPermissionStatus()
        serviceStatusText.text = getString(R.string.service_stopped)
    }

    override fun onDestroy() {
        voiceInputController.destroy()
        voiceResponseManager.release()
        super.onDestroy()
    }

    override fun onStateChanged(state: VoiceInputState) {
        assistantSession.notifyVoiceInputStateChanged(state)
        if (state == VoiceInputState.PROCESSING) {
            PerformanceTracker.stop("voice_input")
            PerformanceTracker.start("command_processing")
        }
        runOnUiThread {
            when (state) {
                VoiceInputState.LISTENING -> {
                    tapToSpeakButton.text = "Listening..."
                    tapToSpeakButton.isEnabled = false
                }
                VoiceInputState.PROCESSING -> {
                    tapToSpeakButton.text = "Processing..."
                    tapToSpeakButton.isEnabled = false
                }
                else -> {
                    tapToSpeakButton.text = "Tap to Speak"
                    tapToSpeakButton.isEnabled = true
                }
            }
        }
    }

    override fun onPartialTranscript(text: String) {
        assistantSession.notifyPartialTranscriptReceived(text)
        runOnUiThread {
            voiceTranscriptText.text = text
        }
    }

    override fun onFinalResult(result: VoiceInputResult) {
        runOnUiThread {
            voiceTranscriptText.text = "Final: ${result.rawTranscript}"
            if (result.shouldSendToBrain) {
                assistantSession.executeCommand(result.cleanedCommand, CommandSource.VOICE)
            } else if (result.rawTranscript.isNotBlank() && !result.wasWakeWordDetected) {
                if (result.cleanedCommand.isBlank()) {
                    showToast("I didn't hear a command after the wake word.")
                }
            }
        }
    }

    override fun onError(error: VoiceInputError, message: String) {
        PerformanceTracker.clear()
        runOnUiThread {
            tapToSpeakButton.text = "Tap to Speak"
            tapToSpeakButton.isEnabled = true
            when (error) {
                VoiceInputError.NO_SPEECH_DETECTED -> voiceTranscriptText.text = "I didn't hear that. Please try again."
                else -> {
                    voiceTranscriptText.text = "Error: $message"
                    showToast(message)
                }
            }
        }
    }

    override fun onCommandResult(result: CommandResult, source: CommandSource) {
        PerformanceTracker.stop("command_processing")
        if (result.success && !result.shouldStopListening) {
             PerformanceTracker.stop("action_execution")
        }
        runOnUiThread {
            showToast("Result: ${result.message}")
        }
    }

    private fun showOnboarding() {
        val steps = onboardingController.getSteps()
        val message = steps.joinToString("\n\n") { "${it.title}: ${it.message}" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Welcome to Nova/Luna")
            .setMessage(message)
            .setPositiveButton("Get Started") { _, _ ->
                onboardingController.markComplete()
                requestPermissionsIfNeededThenStart()
            }
            .setCancelable(false)
            .show()
    }

    private fun showSettings() {
        val summary = settingsController.getSettingsSummary()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Assistant Settings")
            .setMessage(summary)
            .setNeutralButton("Clear Memory") { _, _ ->
                settingsController.clearAllMemory()
                showToast("Memory cleared.")
            }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showDiagnostics() {
        val report: String = diagnosticsReporter.generateReport()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Diagnostics")
            .setMessage(report as CharSequence)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Nova Diagnostics", report)
                clipboard.setPrimaryClip(clip)
                showToast("Copied to clipboard.")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDemoMode() {
        val commands = settingsController.getDemoCommands()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Demo Mode (10 Official Commands)")
            .setItems(commands.toTypedArray()) { _, which ->
                val command = commands[which]
                showToast("Executing: $command")
                assistantSession.executeCommand(command, CommandSource.TEXT)
            }
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onSpeakingStateChanged(isSpeaking: Boolean) {
        runOnUiThread {
            if (isSpeaking) {
                voiceTranscriptText.alpha = 0.5f // Dim transcript while speaking
            } else {
                voiceTranscriptText.alpha = 1.0f
            }
        }
    }

    override fun onVoiceResponseRequested(message: String) {
        runOnUiThread {
            voiceResponseText.text = message
        }
    }

    override fun onThinkingStarted() {
        // MainActivity itself might not need to do anything,
        // as the popup handled by AssistantPopupController will update.
    }

    override fun onActionStarted(label: String) {
        // Optional: show in MainActivity too
    }

    override fun onConfirmationRequired(message: String, actionSummary: String?) {
        // Optional: show in MainActivity too
    }

    override fun onVoiceInputStateChanged(state: VoiceInputState) {
        // handled via onStateChanged bridge
    }

    override fun onPartialTranscriptReceived(transcript: String) {
        // handled via onPartialTranscript bridge
    }

    override fun onDomainRouted(domain: com.nova.luna.brain.UnifiedDomain) {
        // MainActivity might not need to show domain if popup does
    }

    override fun onMemoryOperationComplete(result: com.nova.luna.memory.MemoryOperationResult) {
        runOnUiThread {
            showToast(result.userMessage ?: "Memory operation complete.")
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun setupButtons() {
        tapToSpeakButton.setOnClickListener {
            assistantSession.onVoiceInputStarted()
            voiceInputController.startListening()
        }
        findViewById<Button>(R.id.startListeningButton).setOnClickListener {
            requestPermissionsIfNeededThenStart()
        }
        findViewById<Button>(R.id.stopListeningButton).setOnClickListener {
            stopVoiceService()
        }
        findViewById<Button>(R.id.accessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.usageAccessButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<Button>(R.id.refreshStatusButton).setOnClickListener {
            refreshPermissionStatus()
        }
        findViewById<Button>(R.id.commandHistoryButton).setOnClickListener {
            startActivity(Intent(this, CommandHistoryActivity::class.java))
        }
        findViewById<Button>(R.id.demoModeButton).setOnClickListener {
            showDemoMode()
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            showSettings()
        }
        findViewById<Button>(R.id.diagnosticsButton).setOnClickListener {
            showDiagnostics()
        }

        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (suppressProfileCallback) return
                val selectedProfile = VoiceProfile.values()[position]
                lifecycleScope.launch {
                    preferencesManager.setVoiceProfile(selectedProfile)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun observeVoiceProfile() {
        lifecycleScope.launch {
            preferencesManager.voiceProfileFlow.collect { profile ->
                val profiles = VoiceProfile.values()
                val selectedIndex = profiles.indexOf(profile).coerceAtLeast(0)
                if (profileSpinner.selectedItemPosition != selectedIndex) {
                    suppressProfileCallback = true
                    profileSpinner.setSelection(selectedIndex, false)
                    suppressProfileCallback = false
                }
            }
        }
    }

    private fun requestPermissionsIfNeededThenStart() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            startVoiceService()
            return
        }

        pendingStartAfterPermissionGrant = true
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceCommandService::class.java).apply {
            action = VoiceCommandService.ACTION_START_LISTENING
        }
        ContextCompat.startForegroundService(this, intent)
        serviceStatusText.text = getString(R.string.service_running)
        showToast("Voice service starting.")
        refreshPermissionStatus()
    }

    private fun stopVoiceService() {
        val intent = Intent(this, VoiceCommandService::class.java).apply {
            action = VoiceCommandService.ACTION_STOP_LISTENING
        }
        ContextCompat.startForegroundService(this, intent)
        serviceStatusText.text = getString(R.string.service_stopped)
        showToast("Voice service stopping.")
    }

    private fun refreshPermissionStatus() {
        permissionStatusText.text = buildString {
            appendLine("Microphone: ${status(PermissionUtils.hasRecordAudioPermission(this@MainActivity))}")
            appendLine("Notifications: ${status(PermissionUtils.hasPostNotificationsPermission(this@MainActivity) && PermissionUtils.hasNotificationAccess(this@MainActivity))}")
            appendLine("Accessibility: ${status(PermissionUtils.hasAccessibilityPermission(this@MainActivity))}")
            appendLine("Usage access: ${status(PermissionUtils.hasUsageAccess(this@MainActivity))}")
            appendLine("Biometric: ${PermissionUtils.biometricHardwareStatus(this@MainActivity)}")
        }.trim()
    }

    private fun status(granted: Boolean): String {
        return if (granted) "Granted" else "Missing"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
