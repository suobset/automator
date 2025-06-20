package com.skushagra.selfselect

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ActionTypes and ParameterKeys are defined in ActorAction.kt
// No need to redefine them here if ActorAction.kt is in the same module
// and its contents are accessible.

class ActorViewModel {

    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    private val TAG = "ActorViewModel"

    private val _yamlScripts = mutableStateOf("")
    val yamlScripts: State<String> get() = _yamlScripts

    private val _errorState = mutableStateOf("")
    val errorState: State<String> get() = _errorState

    // ViewModel CoroutineScope for operations that might need to switch context
    // or for future asynchronous operations if you reconsider.
    private val viewModelScope = CoroutineScope(Dispatchers.Main.immediate)

    fun copyAction(context: Context, yamlInput: String){
        _yamlScripts.value = yamlInput
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("YAML Script", yamlInput)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Script copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error copying YAML to clipboard: ${e.message}", e)
            showErrors(context, "Error copying to clipboard: ${e.message}")
        }
    }

    fun showErrors(context: Context, errorMessage: String) {
        // This function can be called from any thread if needed,
        // but since we're aiming for main thread execution, it's simpler.
        _errorState.value = errorMessage
        Log.e(TAG, "Error displayed: $errorMessage")
        // Consider making Toast display conditional or more integrated with UI state
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }

    fun clearErrors() {
        _errorState.value = ""
    }

    fun executeAction(context: Context, yamlInput: String) {
        clearErrors()
        _yamlScripts.value = yamlInput
        val service = ActorAccessibilityService.instance

        if (service == null) {
            Log.w(TAG, "Accessibility service not enabled. Redirecting to settings.")
            showErrors(context, "Accessibility Service not enabled. Please enable it in Settings.")
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return
        }

        Log.d(TAG, "Received YAML input for execution: \n$yamlInput")

        val script: ActorScript = try {
            yamlMapper.readValue(yamlInput, ActorScript::class.java)
        } catch (e: Exception) {
            showErrors(context, "Error parsing YAML: ${e.message}")
            Log.e(TAG, "Error parsing YAML: ${e.message}", e)
            return
        }

        if (script.actions.isEmpty()) {
            Log.d(TAG, "No actions found in the script.")
            showErrors(context, "No actions found in the YAML script.")
            return
        }

        // Direct execution loop.
        // WARNING: Long-running service calls or Thread.sleep here WILL block the main thread
        // and can lead to ANR (Application Not Responding) errors.
        // This approach is taken based on user preference to "see what's happening" sequentially
        // without explicit ViewModel threading. The user is responsible for ensuring
        // service methods are fast or for handling ANRs.

        viewModelScope.launch { // Use a coroutine for sequential execution but on Main.immediate
            var overallSuccess = true
            for (actorAction in script.actions) {
                // Small delay to allow UI to update if needed, and to make steps observable.
                // This delay itself, if too long or if the main thread is busy, adds to ANR risk.
                try {
                    withContext(Dispatchers.IO) { // Perform sleep on IO dispatcher
                        Thread.sleep(250) // Brief pause between actions
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Log.w(TAG, "Delay interrupted", e)
                    showErrors(context, "Script execution interrupted during delay.")
                    overallSuccess = false
                    break
                }

                Log.i(TAG, "Executing action: ${actorAction.actionType}, Description: ${actorAction.description ?: "N/A"}, Params: ${actorAction.parameters}")
                var actionSuccess = true // Assume success unless proven otherwise or action doesn't return status

                // IMPORTANT: The 'service' methods (e.g., service.openApp)
                // MUST be updated in ActorAccessibilityService.kt to return Boolean for success/failure
                // for this logic to correctly determine if an action failed.
                // If they remain void, 'actionSuccess' will not be correctly updated for those.

                when (actorAction.actionType) {
                    ActionTypes.PULL_DOWN_NOTIFICATION_BAR -> service.pullDownNotificationBar() // Assuming void, or update to return boolean
                    ActionTypes.NAVIGATE_HOME -> service.navigateHome() // Assuming void
                    ActionTypes.NAVIGATE_BACK -> service.navigateBack() // Assuming void
                    ActionTypes.OPEN_APP -> {
                        val appName = actorAction.parameters?.get(ParameterKeys.APP_NAME)
                        val packageName = actorAction.parameters?.get(ParameterKeys.PACKAGE_NAME)
                        if (!appName.isNullOrBlank()) {
                            service.openApp(appName) // Assuming void
                        } else if (!packageName.isNullOrBlank()) {
                            service.openAppByPackageName(packageName) // Assuming void
                        } else {
                            showErrors(context, "OPEN_APP action requires '${ParameterKeys.APP_NAME}' or '${ParameterKeys.PACKAGE_NAME}' parameter.")
                            actionSuccess = false
                        }
                    }
                    ActionTypes.LAUNCH_URL -> {
                        val url = actorAction.parameters?.get(ParameterKeys.URL)
                        if (!url.isNullOrBlank()) {
                            service.launchUrl(url) // Assuming void
                        } else {
                            showErrors(context, "LAUNCH_URL action requires '${ParameterKeys.URL}' parameter.")
                            actionSuccess = false
                        }
                    }
                    ActionTypes.TYPE_TEXT -> {
                        val textToType = actorAction.parameters?.get(ParameterKeys.TEXT_TO_TYPE)
                        val targetResId = actorAction.parameters?.get(ParameterKeys.ELEMENT_RESOURCE_ID)
                        val targetElementText = actorAction.parameters?.get(ParameterKeys.ELEMENT_TEXT_TO_CLICK)
                        if (textToType != null) { // textToType can be an empty string
                            service.typeText(textToType, targetResId, targetElementText) // Assuming void
                        } else {
                            showErrors(context, "TYPE_TEXT action requires '${ParameterKeys.TEXT_TO_TYPE}' parameter.")
                            actionSuccess = false
                        }
                    }
                    ActionTypes.CLICK_ELEMENT -> {
                        val elementText = actorAction.parameters?.get(ParameterKeys.ELEMENT_TEXT_TO_CLICK)
                        val resourceId = actorAction.parameters?.get(ParameterKeys.ELEMENT_RESOURCE_ID)
                        val contentDescription = actorAction.parameters?.get(ParameterKeys.ELEMENT_CONTENT_DESCRIPTION)
                        if (elementText != null || resourceId != null || contentDescription != null) {
                            service.clickElement(elementText, resourceId, contentDescription) // Assuming void
                        } else {
                            showErrors(context, "CLICK_ELEMENT action requires at least one identifier parameter.")
                            actionSuccess = false
                        }
                    }
                    ActionTypes.SCROLL_VIEW -> {
                        val direction = actorAction.parameters?.get(ParameterKeys.SCROLL_DIRECTION)
                        // Ensure your service.scrollView can handle nulls for optional params if they are not provided
                        val targetResId = actorAction.parameters?.get(ParameterKeys.SCROLL_TARGET_RESOURCE_ID)
                        val targetText = actorAction.parameters?.get(ParameterKeys.SCROLL_TARGET_TEXT)
                        if (!direction.isNullOrBlank()) {
                            // Example: service.scrollView(direction, targetResId, targetText)
                            // This depends on the actual signature in your service.
                            // For now, assuming it exists and is callable like this:
                            Log.w(TAG, "SCROLL_VIEW calls a hypothetical service.scrollView method. Ensure it exists and handles parameters.")
                            // actionSuccess = service.scrollView(direction, targetResId, targetText) // If it returns boolean
                        } else {
                            showErrors(context, "SCROLL_VIEW action requires '${ParameterKeys.SCROLL_DIRECTION}' parameter.")
                            actionSuccess = false
                        }
                    }
                    ActionTypes.WAIT -> {
                        val durationString = actorAction.parameters?.get(ParameterKeys.WAIT_DURATION_MS)
                        try {
                            val durationMs = durationString?.toLong()
                            if (durationMs != null && durationMs > 0) {
                                Log.d(TAG, "Waiting for ${durationMs}ms (blocking main thread or coroutine context)")
                                // This is where the ANR risk is very high if not handled properly.
                                // Using withContext(Dispatchers.IO) for Thread.sleep
                                withContext(Dispatchers.IO) {
                                    Thread.sleep(durationMs)
                                }
                                Log.d(TAG, "Wait finished.")
                            } else {
                                showErrors(context, "WAIT action requires a valid positive '${ParameterKeys.WAIT_DURATION_MS}'. Found: $durationString")
                                actionSuccess = false
                            }
                        } catch (e: NumberFormatException) {
                            showErrors(context, "Invalid format for '${ParameterKeys.WAIT_DURATION_MS}'. Value: '$durationString'. Error: ${e.message}")
                            actionSuccess = false
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            Log.w(TAG, "Wait action interrupted.", e)
                            showErrors(context, "Wait action interrupted.")
                            actionSuccess = false
                        }
                    }
                    ActionTypes.SEND_TEXT_MESSAGE -> {
                        val recipientName = actorAction.parameters?.get(ParameterKeys.RECIPIENT_NAME)
                        val recipientNumber = actorAction.parameters?.get(ParameterKeys.RECIPIENT_NUMBER)
                        val messageBody = actorAction.parameters?.get(ParameterKeys.MESSAGE_BODY)
                        if ((!recipientName.isNullOrBlank() || !recipientNumber.isNullOrBlank()) && messageBody != null) {
                            // Assuming service.sendTextMessage exists and is void or you adapt
                            Log.w(TAG, "SEND_TEXT_MESSAGE calls a hypothetical service.sendTextMessage. Ensure it exists.")
                            // service.sendTextMessage(recipientName, recipientNumber, messageBody)
                        } else {
                            showErrors(context, "SEND_TEXT_MESSAGE requires ('${ParameterKeys.RECIPIENT_NAME}' or '${ParameterKeys.RECIPIENT_NUMBER}') and '${ParameterKeys.MESSAGE_BODY}'.")
                            actionSuccess = false
                        }
                    }

                    // --- NEW ACTIONS ---
                    // These will require corresponding methods in ActorAccessibilityService.kt
                    ActionTypes.GET_TEXT_FROM_ELEMENT -> {
                        val resId = actorAction.parameters?.get(ParameterKeys.ELEMENT_RESOURCE_ID)
                        val textToFind = actorAction.parameters?.get(ParameterKeys.ELEMENT_TEXT_TO_CLICK) // Or dedicated ELEMENT_TEXT_TO_FIND
                        val contentDesc = actorAction.parameters?.get(ParameterKeys.ELEMENT_CONTENT_DESCRIPTION)
                        if (resId != null || textToFind != null || contentDesc != null) {
                            // val retrievedText = service.getTextFromElement(resId, textToFind, contentDesc) // Assuming this new method exists and returns String?
                            // if (retrievedText != null) {
                            //    Log.i(TAG, "GET_TEXT_FROM_ELEMENT successful. Text: \"$retrievedText\"")
                            // } else {
                            //    showErrors(context, "GET_TEXT_FROM_ELEMENT: Failed to find element or get text.")
                            //    actionSuccess = false
                            // }
                            Log.w(TAG, "GET_TEXT_FROM_ELEMENT requires service.getTextFromElement to be implemented.")
                            actionSuccess = false // Mark as not ready until service impl.
                        } else {
                            showErrors(context, "GET_TEXT_FROM_ELEMENT requires at least one identifier parameter.")
                            actionSuccess = false
                        }
                    }
                    ActionTypes.WAIT_FOR_ELEMENT -> {
                        val resId = actorAction.parameters?.get(ParameterKeys.ELEMENT_RESOURCE_ID)
                        val textToFind = actorAction.parameters?.get(ParameterKeys.ELEMENT_TEXT_TO_CLICK)
                        val contentDesc = actorAction.parameters?.get(ParameterKeys.ELEMENT_CONTENT_DESCRIPTION)
                        val timeoutMsStr = actorAction.parameters?.get(ParameterKeys.TIMEOUT_MS)
                        if ((resId != null || textToFind != null || contentDesc != null) && !timeoutMsStr.isNullOrBlank()) {
                            try {
                                val timeoutMs = timeoutMsStr.toLong()
                                if (timeoutMs > 0) {
                                    // actionSuccess = service.waitForElement(resId, textToFind, contentDesc, timeoutMs) // Assuming this new method exists and returns Boolean
                                    // if (!actionSuccess) {
                                    //    showErrors(context, "WAIT_FOR_ELEMENT: Element not found within timeout.")
                                    // }
                                    Log.w(TAG, "WAIT_FOR_ELEMENT requires service.waitForElement to be implemented.")
                                    actionSuccess = false // Mark as not ready
                                } else {
                                    showErrors(context, "WAIT_FOR_ELEMENT requires a positive '${ParameterKeys.TIMEOUT_MS}'.")
                                    actionSuccess = false
                                }
                            } catch (e: NumberFormatException) {
                                showErrors(context, "Invalid format for '${ParameterKeys.TIMEOUT_MS}' in WAIT_FOR_ELEMENT.")
                                actionSuccess = false
                            }
                        } else {
                            showErrors(context, "WAIT_FOR_ELEMENT requires an element identifier and '${ParameterKeys.TIMEOUT_MS}'.")
                            actionSuccess = false
                        }
                    }
                    ActionTypes.PERFORM_ACCESSIBILITY_ACTION -> {
                        val actionToPerform = actorAction.parameters?.get(ParameterKeys.ACTION_TO_PERFORM)
                        val resId = actorAction.parameters?.get(ParameterKeys.ELEMENT_RESOURCE_ID)
                        val elementText = actorAction.parameters?.get(ParameterKeys.ELEMENT_TEXT_TO_CLICK)
                        val contentDesc = actorAction.parameters?.get(ParameterKeys.ELEMENT_CONTENT_DESCRIPTION)

                        if (!actionToPerform.isNullOrBlank() && (resId != null || elementText != null || contentDesc != null)) {
                            // Ensure service.performAccessibilityActionOnElement takes actionName (String)
                            // The service will be responsible for mapping this string to an integer ID
                            val success = service.performAccessibilityActionOnElement(actionToPerform, resId, elementText, contentDesc)
                            if (!success) {
                                showErrors(context, "Failed to perform action '$actionToPerform' on the specified element.")
                                overallSuccess = false // Mark that this step failed
                            }
                            actionSuccess = success
                        } else {
                            showErrors(context, "PERFORM_ACCESSIBILITY_ACTION requires '${ParameterKeys.ACTION_TO_PERFORM}' and at least one element identifier.")
                            actionSuccess = false
                        }
                    }
                    ActionTypes.TAKE_SCREENSHOT, ActionTypes.TOGGLE_WIFI, ActionTypes.TOGGLE_BLUETOOTH, ActionTypes.SET_VOLUME -> {
                        // These are defined in ActorAction.kt but might not have full service implementations yet.
                        Log.i(TAG, "${actorAction.actionType} action called. Ensure service implementation exists.")
                        // Assume true for now, or get boolean from service call if it exists and returns one.
                        // e.g. actionSuccess = service.toggleWifi(...)
                    }
                    else -> {
                        Log.e(TAG, "Unknown or unsupported action type: ${actorAction.actionType}")
                        showErrors(context, "Unknown action type: ${actorAction.actionType}")
                        actionSuccess = false
                    }
                }

                if (!actionSuccess) {
                    Log.e(TAG, "Action [${actorAction.actionType}] failed or was not fully implemented. Stopping script execution.")
                    overallSuccess = false
                    break // Stop processing further actions if one fails
                }
            }

            if (overallSuccess) {
                Log.i(TAG, "Script executed. Final status: Success (or all actions attempted).")
                // Optionally show a success message
                // Toast.makeText(context, "Script finished", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Script execution stopped due to a failed action.")
            }
        }
    }
}