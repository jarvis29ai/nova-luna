package com.nova.luna.phone

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import java.util.Locale

class AndroidPhoneActionExecutor(
    private val context: Context,
    private val appResolver: AppResolver,
    private val flashlightController: FlashlightController,
    private val navigationController: NavigationController
) : PhoneActionExecutor {

    companion object {
        private const val TAG = "NovaLunaPhase25"
    }

    override fun execute(action: BrainAction): PhoneActionResult {
        Log.i(TAG, "Executing action: ${action.actionType} intent=${action.intent}")

        return when (action.actionType) {
            BrainActionType.OPEN_APP -> openApp(action)
            BrainActionType.OPEN_CAMERA -> openCamera(action)
            BrainActionType.OPEN_SETTINGS -> openSettings(action)
            BrainActionType.SEARCH_WEB, BrainActionType.EXTERNAL_ACTION -> {
                if (action.intent.lowercase(Locale.US).contains("search") || action.intent.lowercase(Locale.US).contains("web")) {
                    searchWeb(action)
                } else {
                    unsupportedAction(action)
                }
            }
            BrainActionType.TOGGLE_FLASHLIGHT -> toggleFlashlight(action)
            BrainActionType.NONE -> {
                when (action.intent.lowercase(Locale.US)) {
                    "go_back", "navigate_back" -> goBack(action)
                    "go_home", "navigate_home" -> goHome(action)
                    "open_recents" -> openRecents(action)
                    "open_notifications" -> openNotifications(action)
                    else -> unsupportedAction(action)
                }
            }
            else -> unsupportedAction(action)
        }
    }

    private fun openApp(action: BrainAction): PhoneActionResult {
        val appName = action.params["appName"] ?: action.params["package"] ?: return PhoneActionResult(
            actionName = "OPEN_APP",
            attempted = false,
            success = false,
            reason = "App name not provided in parameters."
        )

        val appInfo = appResolver.resolveAppInfo(appName) ?: return PhoneActionResult(
            actionName = "OPEN_APP",
            attempted = true,
            success = false,
            packageName = null,
            reason = "App '$appName' not found or not installed.",
            errorCode = "APP_NOT_FOUND"
        )

        val packageName = appInfo.packageName
        val label = appInfo.label

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                logSuccess("OPEN_APP", packageName)
                PhoneActionResult(
                    actionName = "OPEN_APP",
                    attempted = true,
                    success = true,
                    packageName = packageName,
                    label = label,
                    reason = "Opened $label."
                )
            } else {
                logFailure("OPEN_APP", "LAUNCH_INTENT_NOT_FOUND", packageName)
                PhoneActionResult(
                    actionName = "OPEN_APP",
                    attempted = true,
                    success = false,
                    packageName = packageName,
                    reason = "Could not find launch intent for $appName.",
                    errorCode = "LAUNCH_INTENT_NOT_FOUND"
                )
            }
        } catch (e: Exception) {
            logFailure("OPEN_APP", "START_ACTIVITY_FAILED", packageName)
            PhoneActionResult(
                actionName = "OPEN_APP",
                attempted = true,
                success = false,
                packageName = packageName,
                reason = "Failed to start $appName: ${e.message}",
                errorCode = "START_ACTIVITY_FAILED"
            )
        }
    }

    private fun openCamera(action: BrainAction): PhoneActionResult {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            logSuccess("OPEN_CAMERA")
            PhoneActionResult(
                actionName = "OPEN_CAMERA",
                attempted = true,
                success = true,
                reason = "Opened camera."
            )
        } catch (e: Exception) {
            logFailure("OPEN_CAMERA", "CAMERA_APP_NOT_FOUND")
            PhoneActionResult(
                actionName = "OPEN_CAMERA",
                attempted = true,
                success = false,
                reason = "Could not open camera app.",
                errorCode = "CAMERA_APP_NOT_FOUND"
            )
        }
    }

    private fun openSettings(action: BrainAction): PhoneActionResult {
        val screen = action.params["screen"]?.lowercase(Locale.US) ?: ""
        val intent = when (screen) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }

        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            logSuccess("OPEN_SETTINGS", screen)
            PhoneActionResult(
                actionName = "OPEN_SETTINGS",
                attempted = true,
                success = true,
                reason = "Opened settings $screen."
            )
        } catch (e: Exception) {
            logFailure("OPEN_SETTINGS", "SETTINGS_FAILED", screen)
            PhoneActionResult(
                actionName = "OPEN_SETTINGS",
                attempted = true,
                success = false,
                reason = "Failed to open settings: ${e.message}",
                errorCode = "UNSUPPORTED_SETTINGS_SCREEN"
            )
        }
    }

    private fun searchWeb(action: BrainAction): PhoneActionResult {
        val query = action.params["query"] ?: action.params["text"] ?: ""
        if (query.isBlank()) {
            return PhoneActionResult(
                actionName = "SEARCH_WEB",
                attempted = false,
                success = false,
                reason = "Search query is empty.",
                errorCode = "EMPTY_QUERY"
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            logSuccess("SEARCH_WEB", query)
            PhoneActionResult(
                actionName = "SEARCH_WEB",
                attempted = true,
                success = true,
                reason = "Searching for '$query'."
            )
        } catch (e: ActivityNotFoundException) {
            // Fallback to browser
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                logSuccess("SEARCH_WEB_BROWSER", query)
                PhoneActionResult(
                    actionName = "SEARCH_WEB",
                    attempted = true,
                    success = true,
                    reason = "Opened browser to search for '$query'."
                )
            } catch (e2: Exception) {
                logFailure("SEARCH_WEB", "BROWSER_FAILED")
                PhoneActionResult(
                    actionName = "SEARCH_WEB",
                    attempted = true,
                    success = false,
                    reason = "No app available to perform search.",
                    errorCode = "SEARCH_FAILED"
                )
            }
        }
    }

    private fun toggleFlashlight(action: BrainAction): PhoneActionResult {
        val state = action.params["state"]?.lowercase(Locale.US) ?: "on"
        val on = state != "off"
        
        val status = flashlightController.setFlashlight(on)
        val success = status == FlashlightController.FlashlightStatus.FLASHLIGHT_ON || 
                      status == FlashlightController.FlashlightStatus.FLASHLIGHT_OFF

        if (success) {
            logSuccess("TOGGLE_FLASHLIGHT", state)
        } else {
            logFailure("TOGGLE_FLASHLIGHT", status.name)
        }

        return PhoneActionResult(
            actionName = "TOGGLE_FLASHLIGHT",
            attempted = true,
            success = success,
            reason = when (status) {
                FlashlightController.FlashlightStatus.FLASHLIGHT_ON -> "Flashlight turned on."
                FlashlightController.FlashlightStatus.FLASHLIGHT_OFF -> "Flashlight turned off."
                FlashlightController.FlashlightStatus.FLASHLIGHT_UNAVAILABLE -> "Flashlight is not available on this device."
                FlashlightController.FlashlightStatus.CAMERA_PERMISSION_MISSING -> "Flashlight cannot be used because camera permission is missing."
                FlashlightController.FlashlightStatus.FLASHLIGHT_FAILED -> "Failed to toggle flashlight."
            },
            errorCode = if (!success) status.name else null
        )
    }

    private fun goBack(action: BrainAction): PhoneActionResult = performNavigation("BACK", navigationController::goBack)
    private fun goHome(action: BrainAction): PhoneActionResult = performNavigation("HOME", navigationController::goHome)
    private fun openRecents(action: BrainAction): PhoneActionResult = performNavigation("RECENTS", navigationController::openRecents)
    private fun openNotifications(action: BrainAction): PhoneActionResult = performNavigation("NOTIFICATIONS", navigationController::openNotifications)

    private fun performNavigation(name: String, navCall: () -> NavigationController.NavigationStatus): PhoneActionResult {
        val status = navCall()
        val success = status == NavigationController.NavigationStatus.SUCCESS
        
        if (success) {
            logSuccess("NAVIGATION_$name")
        } else {
            logFailure("NAVIGATION_$name", status.name)
        }

        val reason = when (status) {
            NavigationController.NavigationStatus.SUCCESS -> {
                when (name) {
                    "HOME" -> "Going home."
                    "BACK" -> "Going back."
                    "RECENTS" -> "Opening recent apps."
                    "NOTIFICATIONS" -> "Opening notifications."
                    else -> "Navigation $name successful."
                }
            }
            NavigationController.NavigationStatus.ACCESSIBILITY_NOT_READY -> "Accessibility service is not ready, so I cannot press $name yet."
            NavigationController.NavigationStatus.FAILED -> "Failed to perform navigation $name."
            NavigationController.NavigationStatus.UNSUPPORTED -> "Navigation $name is not supported."
        }

        return PhoneActionResult(
            actionName = "NAVIGATION_$name",
            attempted = status != NavigationController.NavigationStatus.ACCESSIBILITY_NOT_READY,
            success = success,
            reason = reason,
            errorCode = if (!success) status.name else null
        )
    }

    private fun unsupportedAction(action: BrainAction): PhoneActionResult {
        logFailure("UNSUPPORTED", action.actionType.name)
        return PhoneActionResult(
            actionName = action.actionType.name,
            attempted = false,
            success = false,
            reason = "Action ${action.actionType} is not supported by the phone executor.",
            errorCode = "UNSUPPORTED_ACTION"
        )
    }

    private fun logSuccess(action: String, target: String? = null) {
        Log.i(TAG, "action=$action target=$target safety=ALLOW attempted=true success=true")
    }

    private fun logFailure(action: String, error: String, target: String? = null) {
        Log.i(TAG, "action=$action target=$target safety=ALLOW attempted=true success=false error=$error")
    }
}
