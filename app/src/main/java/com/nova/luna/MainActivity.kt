package com.nova.luna

import android.app.ActivityManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.StatFs
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
import com.nova.luna.modelinstall.DefaultModelManager
import com.nova.luna.modelinstall.DeviceCapabilitySnapshot
import com.nova.luna.modelinstall.ModelBrainDownloadPresenter
import com.nova.luna.modelinstall.MODEL_SOURCE_NOT_CONFIGURED_MESSAGE
import com.nova.luna.modelinstall.PrivateAppModelStorage
import com.nova.luna.service.VoiceCommandService
import com.nova.luna.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.nova.luna.brain.AssistantSession
import com.nova.luna.brain.CommandBrain
import com.nova.luna.brain.CommandSource
import com.nova.luna.voice.*
import com.nova.luna.model.CommandResult
import com.nova.luna.ui.*

class MainActivity : AppCompatActivity(), VoiceInputController.VoiceInputListener, AssistantSession.SessionListener {
    private lateinit var preferencesManager: PreferencesManager
    
    // Logic Controllers
    lateinit var voiceInputController: VoiceInputController
    lateinit var assistantSession: AssistantSession
    lateinit var voiceResponseManager: VoiceResponseManager
    lateinit var popupController: AssistantPopupController
    lateinit var readinessChecker: PrototypeReadinessChecker
    lateinit var healthMonitor: RuntimeHealthMonitor
    lateinit var onboardingController: OnboardingController
    lateinit var settingsController: SettingsController
    lateinit var modelManager: DefaultModelManager
    lateinit var brainDownloadPresenter: ModelBrainDownloadPresenter

    private lateinit var bottomNavigation: com.google.android.material.bottomnavigation.BottomNavigationView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val micGranted = result[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            startVoiceService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferencesManager = PreferencesManager(this)
        
        // Initialize Logic
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
        modelManager = DefaultModelManager(PrivateAppModelStorage.from(this))
        brainDownloadPresenter = ModelBrainDownloadPresenter(modelManager)

        popupController = AssistantPopupController(findViewById(R.id.popupContainer), assistantSession) { event ->
            when (event) {
                AssistantPopupEvent.MIC_TAPPED -> voiceInputController.startListening()
                AssistantPopupEvent.CONTINUE_TAPPED -> assistantSession.confirmPendingAction()
                else -> {}
            }
        }
        assistantSession.addSessionListener(popupController)

        setupNavigation()

        if (!onboardingController.isComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    private fun setupNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.navigation_home -> HomeFragment()
                R.id.navigation_assistant -> AssistantFragment()
                R.id.navigation_settings -> SettingsFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }
        
        // Load default fragment
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            bottomNavigation.selectedItemId = R.id.navigation_home
        }
    }

    fun startVoiceService() {
        val intent = Intent(this, VoiceCommandService::class.java).apply {
            action = VoiceCommandService.ACTION_START_LISTENING
        }
        ContextCompat.startForegroundService(this, intent)
    }

    fun stopVoiceService() {
        val intent = Intent(this, VoiceCommandService::class.java).apply {
            action = VoiceCommandService.ACTION_STOP_LISTENING
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        voiceInputController.destroy()
        voiceResponseManager.release()
        super.onDestroy()
    }

    override fun onStateChanged(state: VoiceInputState) {
        assistantSession.notifyVoiceInputStateChanged(state)
        // Fragments will observe this via MainActivity or AssistantSession
    }

    override fun onPartialTranscript(text: String) {
        assistantSession.notifyPartialTranscriptReceived(text)
    }

    override fun onFinalResult(result: VoiceInputResult) {
        if (result.shouldSendToBrain) {
            assistantSession.executeCommand(result.cleanedCommand, CommandSource.VOICE)
        }
    }

    override fun onError(error: VoiceInputError, message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onCommandResult(result: CommandResult, source: CommandSource) {
        runOnUiThread { Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show() }
    }

    override fun onSpeakingStateChanged(isSpeaking: Boolean) {}
    override fun onVoiceResponseRequested(message: String) {}
    override fun onThinkingStarted() {}
    override fun onActionStarted(label: String) {}
    override fun onConfirmationRequired(message: String, actionSummary: String?) {}
    override fun onVoiceInputStateChanged(state: VoiceInputState) {}
    override fun onPartialTranscriptReceived(transcript: String) {}
    override fun onDomainRouted(domain: com.nova.luna.brain.UnifiedDomain) {}
    override fun onMemoryOperationComplete(result: com.nova.luna.memory.MemoryOperationResult) {}

    fun buildDeviceCapabilitySnapshot(): DeviceCapabilitySnapshot {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamMb = (memoryInfo.totalMem / (1024 * 1024)).toInt()
        val statFs = StatFs(filesDir.absolutePath)
        val freeStorageMb = (statFs.availableBytes / (1024 * 1024)).toInt()
        
        return DeviceCapabilitySnapshot(
            totalRamMb = totalRamMb,
            freeStorageMb = freeStorageMb,
            androidVersion = Build.VERSION.SDK_INT,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            networkAvailable = true
        )
    }
}
