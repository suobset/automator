package com.skushagra.selfselect

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log // For better logging
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

class ActorViewModel {

    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    private val TAG = "ActorViewModel" // For Logcat

    // MutableState to hold the YAML Scripts
    private val _yamlScripts = mutableStateOf("")
    val yamlScripts: State<String> get() = _yamlScripts

    // MutableState to hold the error message for the UI
    private val _errorState = mutableStateOf("")
    val errorState: State<String> get() = _errorState

    fun copyAction(context: Context, yamlInput: String){
        _yamlScripts.value = yamlInput
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("YAML Script", yamlInput)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Error copying YAML to clipboard: ${e.message}", e)
            Toast.makeText(context, "Error copying to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun showErrors(context: Context, yamlError: String) {
        // This function updates the errorTextState which is observed by the ActorScreen
        _errorState.value = yamlError
        Toast.makeText(context, yamlError, Toast.LENGTH_SHORT).show()
    }

    fun clearErrors() {
        _errorState.value = "No Errors"
    }

    fun executeAction(context: Context, yamlInput: String) {
        // Regardless of the action taken, clear the error box
        clearErrors()
        // Store the yamlInput
        _yamlScripts.value = yamlInput
        try {
            val service = ActorAccessibilityService.instance
            if (service == null) {
                Log.w(TAG, "Accessibility service not enabled. Redirecting to settings.")
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK // Required if context is not Activity
                context.startActivity(intent)
                return // Stop further execution if service is not available
            }

            Log.d(TAG, "Received YAML input: $yamlInput")
            val script = try {
                yamlMapper.readValue(yamlInput, ActorScript::class.java)
            } catch (e: Exception) {
                showErrors(context, "Error parsing YAML: ${e.message}")
                Log.e(TAG, "Error parsing YAML: ${e.message}", e)
                // Consider providing user feedback here if possible, e.g., via a callback or LiveData
                return
            }

            if (script.actions.isEmpty()) {
                Log.d(TAG, "No actions found in the script.")
                showErrors(context, "No actions found in the YAML script.")
                return
            }

            for (actorAction in script.actions) {
                Log.d(TAG, "Executing action: ${actorAction.actionType}, Description: ${actorAction.description ?: "N/A"}, Params: ${actorAction.parameters}")
                when (actorAction.actionType) {
                    ActionTypes.PULL_DOWN_NOTIFICATION_BAR -> {
                        service.pullDownNotificationBar()
                    }
                    ActionTypes.NAVIGATE_HOME -> {
                        service.navigateHome()
                    }
                    ActionTypes.NAVIGATE_BACK -> {
                        service.navigateBack()
                    }
                    ActionTypes.OPEN_APP -> {
                        val appName = actorAction.parameters?.get(ParameterKeys.APP_NAME)
                        val packageName = actorAction.parameters?.get(ParameterKeys.PACKAGE_NAME)
                        if (!appName.isNullOrBlank()) {
                            service.openApp(appName)
                        } else if (!packageName.isNullOrBlank()) {
                            service.openAppByPackageName(packageName)
                        } else {
                            showErrors(context, "OPEN_APP action requires '${ParameterKeys.APP_NAME}' or '${ParameterKeys.PACKAGE_NAME}' parameter.")
                            Log.e(TAG, "OPEN_APP action requires '${ParameterKeys.APP_NAME}' or '${ParameterKeys.PACKAGE_NAME}' parameter.")
                        }
                    }
                    ActionTypes.LAUNCH_URL -> {
                        val url = actorAction.parameters?.get(ParameterKeys.URL)
                        if (!url.isNullOrBlank()) {
                            service.launchUrl(url)
                        } else {
                            showErrors(context, "LAUNCH_URL action requires '${ParameterKeys.URL}' parameter.")
                            Log.e(TAG, "LAUNCH_URL action requires '${ParameterKeys.URL}' parameter.")
                        }
                    }
                    ActionTypes.TYPE_TEXT -> {
                        val textToType = actorAction.parameters?.get(ParameterKeys.TEXT_TO_TYPE)
                        val targetResId = actorAction.parameters?.get(ParameterKeys.ELEMENT_RESOURCE_ID)
                        val targetText = actorAction.parameters?.get(ParameterKeys.ELEMENT_TEXT_TO_CLICK)
                        if (textToType != null) { // textToType can be empty string
                            service.typeText(textToType, targetResId, targetText)
                        } else {
                            showErrors(context, "TYPE_TEXT action requires '${ParameterKeys.TEXT_TO_TYPE}' parameter.")
                            Log.e(TAG, "TYPE_TEXT action requires '${ParameterKeys.TEXT_TO_TYPE}' parameter.")
                        }
                    }
                    ActionTypes.CLICK_ELEMENT -> {
                        val elementText = actorAction.parameters?.get(ParameterKeys.ELEMENT_TEXT_TO_CLICK)
                        val resourceId = actorAction.parameters?.get(ParameterKeys.ELEMENT_RESOURCE_ID)
                        val contentDescription = actorAction.parameters?.get(ParameterKeys.ELEMENT_CONTENT_DESCRIPTION)
                        if (elementText != null || resourceId != null || contentDescription != null) {
                            service.clickElement(elementText, resourceId, contentDescription)
                        } else {
                            showErrors(context, "CLICK_ELEMENT action requires at least one of '${ParameterKeys.ELEMENT_TEXT_TO_CLICK}', '${ParameterKeys.ELEMENT_RESOURCE_ID}', or '${ParameterKeys.ELEMENT_CONTENT_DESCRIPTION}'.")
                            Log.e(TAG, "CLICK_ELEMENT action requires at least one of '${ParameterKeys.ELEMENT_TEXT_TO_CLICK}', '${ParameterKeys.ELEMENT_RESOURCE_ID}', or '${ParameterKeys.ELEMENT_CONTENT_DESCRIPTION}'.")
                        }
                    }
                    ActionTypes.SCROLL_VIEW -> {
                        val direction = actorAction.parameters?.get(ParameterKeys.SCROLL_DIRECTION)
                        val targetResId = actorAction.parameters?.get(ParameterKeys.SCROLL_TARGET_RESOURCE_ID)
                        val targetText = actorAction.parameters?.get(ParameterKeys.SCROLL_TARGET_TEXT)
                        if (!direction.isNullOrBlank()) {
                            service.scrollView(direction, targetResId, targetText)
                        } else {
                            showErrors(context, "SCROLL_VIEW action requires '${ParameterKeys.SCROLL_DIRECTION}' parameter.")
                            Log.e(TAG, "SCROLL_VIEW action requires '${ParameterKeys.SCROLL_DIRECTION}' parameter.")
                        }
                    }
                    ActionTypes.WAIT -> {
                        val durationString = actorAction.parameters?.get(ParameterKeys.WAIT_DURATION_MS)
                        try {
                            val durationMs = durationString?.toLong()
                            if (durationMs != null && durationMs > 0) {
                                Log.d(TAG, "Waiting for ${durationMs}ms")
                                Thread.sleep(durationMs)
                            } else {
                                showErrors(context, "WAIT action requires a valid positive '${ParameterKeys.WAIT_DURATION_MS}'. Found: $durationString")
                                Log.e(TAG, "WAIT action requires a valid positive '${ParameterKeys.WAIT_DURATION_MS}'. Found: $durationString")
                            }
                        } catch (e: NumberFormatException) {
                            showErrors(context, "Invalid format for '${ParameterKeys.WAIT_DURATION_MS}'. Value: '$durationString'. Error: ${e.message}")
                            Log.e(TAG, "Invalid format for '${ParameterKeys.WAIT_DURATION_MS}'. Value: '$durationString'. Error: ${e.message}", e)
                        } catch (e: InterruptedException) {
                            Log.w(TAG, "Wait action interrupted.", e)
                            Thread.currentThread().interrupt() // Restore interruption status
                        }
                    }
                    ActionTypes.SEND_TEXT_MESSAGE -> {
                        val recipientName = actorAction.parameters?.get(ParameterKeys.RECIPIENT_NAME)
                        val recipientNumber = actorAction.parameters?.get(ParameterKeys.RECIPIENT_NUMBER)
                        val messageBody = actorAction.parameters?.get(ParameterKeys.MESSAGE_BODY)
                        if ((!recipientName.isNullOrBlank() || !recipientNumber.isNullOrBlank()) && messageBody != null) {
                            service.sendTextMessage(recipientName, recipientNumber, messageBody)
                        } else {
                            showErrors(context, "SEND_TEXT_MESSAGE requires ('${ParameterKeys.RECIPIENT_NAME}' or '${ParameterKeys.RECIPIENT_NUMBER}') and '${ParameterKeys.MESSAGE_BODY}'.")
                            Log.e(TAG, "SEND_TEXT_MESSAGE requires ('${ParameterKeys.RECIPIENT_NAME}' or '${ParameterKeys.RECIPIENT_NUMBER}') and '${ParameterKeys.MESSAGE_BODY}'.")
                        }
                    }
                    ActionTypes.TAKE_SCREENSHOT -> {
                        // service.takeScreenshot() // Needs careful implementation for file path & permissions
                        Log.i(TAG, "TAKE_SCREENSHOT action called (not yet fully implemented in service).")
                    }
                    ActionTypes.TOGGLE_WIFI -> {
                        val state = actorAction.parameters?.get(ParameterKeys.WIFI_STATE)
                        if (!state.isNullOrBlank()) {
                            // service.toggleWifi(state.equals("ON", ignoreCase = true))
                            Log.i(TAG, "TOGGLE_WIFI action called with state $state (not yet fully implemented in service).")
                        } else {
                            showErrors(context, "TOGGLE_WIFI action requires '${ParameterKeys.WIFI_STATE}' parameter (ON/OFF).")
                            Log.e(TAG, "TOGGLE_WIFI action requires '${ParameterKeys.WIFI_STATE}' parameter (ON/OFF).")
                        }
                    }
                    ActionTypes.TOGGLE_BLUETOOTH -> {
                        val state = actorAction.parameters?.get(ParameterKeys.BLUETOOTH_STATE)
                        if (!state.isNullOrBlank()) {
                            // service.toggleBluetooth(state.equals("ON", ignoreCase = true))
                            Log.i(TAG, "TOGGLE_BLUETOOTH action called with state $state (not yet fully implemented in service).")
                        } else {
                            showErrors(context, "TOGGLE_BLUETOOTH action requires '${ParameterKeys.BLUETOOTH_STATE}' parameter (ON/OFF).")
                            Log.e(TAG, "TOGGLE_BLUETOOTH action requires '${ParameterKeys.BLUETOOTH_STATE}' parameter (ON/OFF).")
                        }
                    }
                    ActionTypes.SET_VOLUME -> {
                        val volumeLevel = actorAction.parameters?.get(ParameterKeys.VOLUME_LEVEL)
                        if (!volumeLevel.isNullOrBlank()) {
                            // service.setVolume(volumeLevel)
                            Log.i(TAG, "SET_VOLUME action called with level $volumeLevel (not yet fully implemented in service).")
                        } else {
                            showErrors(context, "SET_VOLUME action requires '${ParameterKeys.VOLUME_LEVEL}' parameter.")
                            Log.e(TAG, "SET_VOLUME action requires '${ParameterKeys.VOLUME_LEVEL}' parameter.")
                        }
                    }
                    // ... (add other cases as per ActionTypes, initially logging them)
                    else -> {
                        showErrors(context, "Unknown or not yet implemented action_type: ${actorAction.actionType}")
                        Log.w(TAG, "Unknown or not yet implemented action_type: ${actorAction.actionType}")
                    }
                }
            }
        } catch (e: Exception) {
            // Catch any other unexpected errors during action execution loop or setup
            showErrors(context, "Error executing action script: ${e.message}")
            Log.e(TAG, "Error executing action script: ${e.message}", e)
            // It might be useful to inform the user if an entire script fails catastrophically
        }
    }
}
