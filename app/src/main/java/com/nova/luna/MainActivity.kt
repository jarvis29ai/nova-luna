package com.nova.luna

import android.Manifest
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
import com.nova.luna.util.PermissionUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var profileSpinner: Spinner
    private lateinit var permissionStatusText: TextView
    private lateinit var serviceStatusText: TextView
    private lateinit var profileAdapter: ArrayAdapter<String>

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

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun setupButtons() {
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
