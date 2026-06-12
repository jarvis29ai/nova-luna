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
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel

class MainActivity : AppCompatActivity(), AssistantSession.SessionListener {
    private lateinit var preferencesManager: PreferencesManager
    
    // Logic Controllers
    lateinit var voiceInputController: VoiceInputController
    lateinit var voiceOutputController: VoiceOutputController
    lateinit var voiceCommandOrchestrator: VoiceCommandOrchestrator
    lateinit var assistantSession: AssistantSession
    lateinit var voiceResponseManager: VoiceResponseManager
    lateinit var popupController: AssistantPopupController
    lateinit var readinessChecker: PrototypeReadinessChecker
    lateinit var healthMonitor: RuntimeHealthMonitor
    lateinit var onboardingController: OnboardingController
    lateinit var settingsController: SettingsController
    lateinit var modelManager: DefaultModelManager
    lateinit var brainDownloadPresenter: ModelBrainDownloadPresenter
    
    // Phase 26 Bridge
    lateinit var assistantUiBridge: AssistantUiBridge
    private var flutterEngine: FlutterEngine? = null

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
        
        // Phase 27: Voice Flow initialization
        voiceInputController = AndroidSpeechRecognizerVoiceInputController(this)
        voiceOutputController = AndroidTextToSpeechVoiceOutputController(this)

        val personalMemoryStore = com.nova.luna.memory.LocalPersonalMemoryStore(this)
        val personalMemoryManager = com.nova.luna.memory.PersonalMemoryManager(personalMemoryStore)
        assistantSession = AssistantSession(
            commandBrain = CommandBrain(this, personalMemoryStore = personalMemoryStore),
            responseManager = voiceResponseManager,
            memoryManager = personalMemoryManager
        )
        assistantSession.addSessionListener(this)

        voiceCommandOrchestrator = VoiceCommandOrchestrator(
            voiceInput = voiceInputController,
            voiceOutput = voiceOutputController,
            assistantSession = assistantSession
        )

        readinessChecker = PrototypeReadinessChecker(this)
        healthMonitor = RuntimeHealthMonitor(this)
        onboardingController = OnboardingController(this)
        settingsController = SettingsController(assistantSession, personalMemoryManager)
        modelManager = DefaultModelManager(PrivateAppModelStorage.from(this))
        brainDownloadPresenter = ModelBrainDownloadPresenter(modelManager)

        popupController = AssistantPopupController(findViewById(R.id.popupContainer), assistantSession) { event ->
            when (event) {
                AssistantPopupEvent.MIC_TAPPED -> {
                    val personality = assistantUiBridge.getAssistantState().personality
                    val mapped = if (personality == AssistantPersonality.LUNA) AssistantPersonality.LUNA else com.nova.luna.ui.AssistantPersonality.NOVA
                    voiceCommandOrchestrator.startListening(mapped)
                }
                AssistantPopupEvent.CONTINUE_TAPPED -> assistantSession.confirmPendingAction()
                else -> {}
            }
        }
        assistantSession.addSessionListener(popupController)

        // Phase 26 Bridge Initialization
        assistantUiBridge = AssistantUiBridge(this, assistantSession)
        voiceCommandOrchestrator.setListener(assistantUiBridge)
        setupFlutter()

        setupNavigation()

        if (!onboardingController.isComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    private fun setupFlutter() {
        flutterEngine = FlutterEngine(this)
        flutterEngine?.dartExecutor?.executeDartEntrypoint(
            DartExecutor.DartEntrypoint.createDefault()
        )
        FlutterEngineCache.getInstance().put("assistant_engine", flutterEngine)

        val channel = MethodChannel(flutterEngine?.dartExecutor?.binaryMessenger!!, "com.nova.luna/assistant_ui_phase26")
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "submitTextCommand" -> {
                    val command = call.argument<String>("command") ?: ""
                    val personality = call.argument<String>("personality") ?: "LUNA"
                    val requestId = assistantUiBridge.submitTextCommand(command, personality)
                    result.success(requestId)
                }
                "startVoiceListening" -> {
                    val personalityStr = call.argument<String>("personality") ?: "LUNA"
                    val personality = if (personalityStr.uppercase() == "LUNA") AssistantPersonality.LUNA else com.nova.luna.ui.AssistantPersonality.NOVA
                    voiceCommandOrchestrator.startListening(personality)
                    result.success(null)
                }
                "stopVoiceListening" -> {
                    voiceCommandOrchestrator.stopListening()
                    result.success(null)
                }
                "cancelVoiceListening" -> {
                    voiceCommandOrchestrator.cancelListening()
                    result.success(null)
                }
                "getAssistantState" -> {
                    val state = assistantUiBridge.getAssistantState()
                    result.success(stateToMap(state))
                }
                "getCommandHistory" -> {
                    val history = assistantUiBridge.getCommandHistory()
                    result.success(history.map { resultToMap(it) })
                }
                "setPersonality" -> {
                    val personalityStr = call.argument<String>("personality") ?: "LUNA"
                    val personality = try {
                        AssistantPersonality.valueOf(personalityStr.uppercase())
                    } catch (e: Exception) {
                        AssistantPersonality.LUNA
                    }
                    assistantUiBridge.setPersonality(personality)
                    result.success(null)
                }
                "getPhase26Diagnostics" -> {
                    result.success(assistantUiBridge.getPhase26Diagnostics())
                }
                else -> result.notImplemented()
            }
        }

        assistantUiBridge.onStateChanged = { state ->
            runOnUiThread {
                channel.invokeMethod("onStateChanged", stateToMap(state))
            }
        }
    }

    private fun stateToMap(state: AssistantUiState): Map<String, Any?> {
        return mapOf(
            "personality" to state.personality.name,
            "status" to state.status.name,
            "progressMessage" to state.progressMessage,
            "lastCommand" to state.lastCommand,
            "lastResult" to state.lastResult?.let { resultToMap(it) },
            "partialTranscript" to state.partialTranscript,
            "isListening" to state.isListening,
            "isSpeaking" to state.isSpeaking,
            "voiceError" to state.voiceError
        )
    }

    private fun resultToMap(res: AssistantUiResult): Map<String, Any?> {
        return mapOf(
            "requestId" to res.requestId,
            "personality" to res.personality.name,
            "commandText" to res.commandText,
            "status" to res.status.name,
            "progressMessage" to res.progressMessage,
            "resultTitle" to res.resultTitle,
            "resultMessage" to res.resultMessage,
            "actionType" to res.actionType,
            "riskLevel" to res.riskLevel,
            "safetyDecision" to res.safetyDecision,
            "errorCode" to res.errorCode,
            "errorMessage" to res.errorMessage,
            "timestampMs" to res.timestampMs
        )
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
