package com.skushagra.selfselect

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.InvalidApiKeyException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.regex.Matcher
import java.util.regex.Pattern

enum class Sender {
    USER, AUTOMATOR
}

data class ChatMessage(
    val text: String,
    val sender: Sender
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private var generativeModel: GenerativeModel? = null
    private var conversation: Chat? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    companion object {
        private const val PREFS_NAME = "selfselect_prefs"
        private const val API_KEY_NAME = "api_key"
        private const val TAG = "ChatViewModel" // Added TAG
    }

    init {
        checkForApiKey(application.applicationContext)
    }

    private fun storeApiKey(context: Context, apiKey: String) { // Corrected parameter order
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            PREFS_NAME, // Filename
            masterKeyAlias, // Master Key
            context, // Context
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // Key Encryption Scheme
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // Value Encryption Scheme
        )
        with(sharedPreferences.edit()) {
            putString(API_KEY_NAME, apiKey)
            apply()
        }
    }

    private fun retrieveApiKey(context: Context): String? {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            PREFS_NAME, // Filename
            masterKeyAlias, // Master Key
            context, // Context
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // Key Encryption Scheme
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // Value Encryption Scheme
        )
        return sharedPreferences.getString(API_KEY_NAME, null)
    }

    private fun initializeGenerativeModel(apiKey: String): Boolean {
        if (apiKey.isBlank()) { // Prevent trying to init with a blank key from storage/BuildConfig
            Log.w(TAG, "API Key is blank, initialization skipped.")
            return false
        }
        return try {
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash", // Or your desired model
                apiKey = apiKey
            )
            conversation = generativeModel?.startChat() // Initialize conversation
            Log.i(TAG, "GenerativeModel initialized successfully.")
            true
        } catch (e: Exception) { // Catch all exceptions for robustness
            Log.e(TAG, "Error initializing GenerativeModel: ${e.message}", e)
            // Clear potentially partially initialized model/conversation on failure
            generativeModel = null
            conversation = null
            false
        }
    }

    fun checkForApiKey(context: Context) { // Keep public if called from UI, or use in init
        val storedApiKey = retrieveApiKey(context)
        if (initializeGenerativeModel(storedApiKey ?: "")) { // Pass empty string if null
            Log.d(TAG, "Initialized with stored API key.")
            _uiState.value = UiState.Loading // Set loading before sending initial message
            sendInitialMessage()
            return
        }

        // No valid stored key, or initialization failed. Try BuildConfig.apiKey
        // BuildConfig.apiKey might be empty string if not in local.properties
        val buildConfigKey = BuildConfig.apiKey
        if (buildConfigKey.isNotEmpty() && initializeGenerativeModel(buildConfigKey)) {
            Log.d(TAG, "Initialized with BuildConfig API key. Storing it.")
            storeApiKey(context, buildConfigKey) // Store if it's valid and wasn't the stored one
            _uiState.value = UiState.Loading
            sendInitialMessage()
            return
        }

        // If we reach here, no key worked or all keys were blank/invalid.
        Log.i(TAG, "No valid API key found or initialization failed. Prompting for API key.")
        _uiState.value = UiState.ShowApiKeyDialog
    }


    fun sendInitialMessage() {
        if (conversation == null) { // Guard against null conversation
            Log.w(TAG, "sendInitialMessage: Conversation is null, cannot send initial message.")
            // This state should ideally be prevented by checkForApiKey or submitApiKey
            // ensuring the model (and thus conversation) is initialized.
            // If it happens, it means there was a logic error earlier.
            // Re-triggering API key check might be an option, or just show error.
            _uiState.value = UiState.Error("AI model not ready. Please check API key.")
            // Potentially show API key dialog again if this error state is reached.
            // viewModelScope.launch { delay(500); _uiState.value = UiState.ShowApiKeyDialog }
            return
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure prompt1 is defined or passed appropriately
                val response = conversation?.sendMessage(prompt1) // Use safe call
                val reply = response?.text ?: "Automator is ready but no specific greeting generated."
                _chatHistory.value += ChatMessage(reply, Sender.AUTOMATOR)
                _uiState.value = UiState.Success(reply)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending initial message: ${e.message}", e)
                val error = e.localizedMessage ?: "Error during initial communication."
                _chatHistory.value += ChatMessage(error, Sender.AUTOMATOR)
                _uiState.value = UiState.Error(error)
            }
        }
    }

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    fun sendMessage(prompt: String) {
        if (conversation == null) {
            Log.w(TAG, "sendMessage: Conversation is null.")
            _uiState.value = UiState.Error("AI model not ready. Please check API key.")
            _chatHistory.value += ChatMessage("Error: AI Model not initialized. Cannot send message.", Sender.AUTOMATOR)
            // Consider re-showing API key dialog
            // viewModelScope.launch { delay(500); _uiState.value = UiState.ShowApiKeyDialog }
            return
        }
        _chatHistory.value += ChatMessage(prompt, Sender.USER)
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = conversation?.sendMessage(prompt)
                val reply = response?.text ?: "No response from Automator."
                _chatHistory.value += ChatMessage(reply, Sender.AUTOMATOR)
                extractYamlAndPrompt(reply)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}", e)
                val error = e.localizedMessage ?: "Something went wrong while sending the message."
                _chatHistory.value += ChatMessage(error, Sender.AUTOMATOR)
                _uiState.value = UiState.Error(error)
            }
        }
    }

    fun submitApiKey(apiKey: String, context: Context) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Submitted API Key is blank.")
            _uiState.value = UiState.Error("API Key cannot be empty.")
            // To ensure dialog is re-shown after error, it's better if ShowApiKeyDialog can carry an error.
            // For now, a slight delay then forcing ShowApiKeyDialog.
            viewModelScope.launch {
                kotlinx.coroutines.delay(100) // Minimal delay, Error state might be missed by Snackbar
                _uiState.value = UiState.ShowApiKeyDialog
            }
            return
        }

        if (initializeGenerativeModel(apiKey)) {
            Log.i(TAG, "Submitted API Key is valid. Storing and initializing chat.")
            storeApiKey(context, apiKey) // Corrected parameter order
            _uiState.value = UiState.Loading
            sendInitialMessage()
        } else {
            Log.w(TAG, "Submitted API Key is invalid.")
            _uiState.value = UiState.Error("Invalid API Key provided. Please check it and try again.")
            viewModelScope.launch {
                kotlinx.coroutines.delay(100)
                _uiState.value = UiState.ShowApiKeyDialog // Re-show dialog
            }
        }
    }

    fun updateUiStateToError(message: String) { // This is kept if other parts of UI need to set generic errors
        _uiState.value = UiState.Error(message)
    }

    private fun extractYamlAndPrompt(text: String) {
        val pattern: Pattern = Pattern.compile("```yaml\\n(.*?)\\n```", Pattern.DOTALL)
        val matcher: Matcher = pattern.matcher(text)
        if (matcher.find()) {
            val yamlText = matcher.group(1)?.trim()
            if (yamlText != null) {
                _uiState.value = UiState.ShowYamlDialog(yamlText)
            } else {
                // If YAML is expected but not found, keep the current success state but indicate no YAML
                // Or, if YAML is critical, change to an error state or a specific "NoYamlFound" state
                _uiState.value = UiState.Success(text) // Or Error("No YAML block found in response")
            }
        } else {
            // If no YAML block is found, consider it a regular successful response without YAML
            _uiState.value = UiState.Success(text) // Or Error("No YAML block found in response")
        }
    }

    fun onDialogResult(copy: Boolean) {
        viewModelScope.launch { // Use viewModelScope
            if (copy) {
                _chatHistory.value += ChatMessage("YAML copied to clipboard.", Sender.AUTOMATOR)
                _uiState.value = UiState.Success("YAML copied.") // Provide some feedback
            } else {
                _uiState.value = UiState.Success(" YAML dialog dismissed.") // Provide feedback
            }
            // Consider what the general state should be after dialog dismissal
            // If the last message is important, you might want to keep it in UiState.Success
            // For now, let's assume we just signal the operation is done.
        }
    }
}
