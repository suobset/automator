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
    }

    init {
        checkForApiKey(application.applicationContext)
    }

    private fun storeApiKey(apiKey: String, context: Context) {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        with(sharedPreferences.edit()) {
            putString(API_KEY_NAME, apiKey)
            apply()
        }
    }

    private fun retrieveApiKey(context: Context): String? {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return sharedPreferences.getString(API_KEY_NAME, null)
    }

    private fun initializeGenerativeModel(apiKey: String): Boolean {
        return try {
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
            conversation = generativeModel?.startChat()
            true
        } catch (e: InvalidApiKeyException) {
            Log.e("ChatViewModel", "Invalid API Key: ${e.message}")
            updateUiStateToError("Invalid API Key. Please check your API key and try again.")
            false
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error initializing GenerativeModel: ${e.message}")
            updateUiStateToError("An unexpected error occurred while initializing the AI model.")
            false
        }
    }

    private fun checkForApiKey(context: Context) {
        val apiKey = retrieveApiKey(context)
        if (apiKey == null) {
            _uiState.value = UiState.ShowApiKeyDialog
        } else {
            if (initializeGenerativeModel(apiKey)) {
                sendInitialMessage()
            }
        }
    }

    fun sendInitialMessage() {
        if (conversation == null) {
            // This case should ideally be handled by checkForApiKey ensuring model is initialized
            // or by prompting for API key again if initialization failed.
            updateUiStateToError("AI Model not initialized. Please check your API Key.")
            return
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = conversation?.sendMessage(prompt1)
                val reply = "Automator is ready."
                _chatHistory.value += ChatMessage(reply, Sender.AUTOMATOR)
                _uiState.value = UiState.Success(reply)
            } catch (e: Exception) {
                val error = e.localizedMessage ?: "Something went wrong."
                _chatHistory.value += ChatMessage(error, Sender.AUTOMATOR)
                _uiState.value = UiState.Error(error)
            }
        }
    }

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    fun sendMessage(prompt: String) {
        if (conversation == null) {
            updateUiStateToError("AI Model not initialized. Please check your API Key.")
            _chatHistory.value += ChatMessage("Error: AI Model not initialized.", Sender.AUTOMATOR)
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
                val error = e.localizedMessage ?: "Something went wrong."
                _chatHistory.value += ChatMessage(error, Sender.AUTOMATOR)
                _uiState.value = UiState.Error(error)
            }
        }
    }

    fun submitApiKey(apiKey: String, context: Context) {
        if (apiKey.isBlank()) {
            _uiState.value = UiState.Error("API Key cannot be empty.")
            // Transition back to ShowApiKeyDialog after showing the error or let user retry
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000) // Show error for 2 seconds
                if (_uiState.value is UiState.Error) { // Check if current state is still the error
                    _uiState.value = UiState.ShowApiKeyDialog
                }
            }
            return
        }
        if (initializeGenerativeModel(apiKey)) {
            storeApiKey(apiKey, context)
            sendInitialMessage() // This will set state to Loading then Success/Error
        } else {
            // initializeGenerativeModel already updated UI state to Error and logged
            // Optionally, ensure dialog is reshown if needed
             _uiState.value = UiState.ShowApiKeyDialog
        }
    }

    fun updateUiStateToError(message: String) {
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
