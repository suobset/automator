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

class ChatViewModel(application: Application, isPreview: Boolean) : AndroidViewModel(application) {

    private var generativeModel: GenerativeModel? = null
    private var conversation: Chat? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    // Secondary constructor for existing code, defaults to not being a preview
    constructor(application: Application) : this(application, false)

    companion object {
        private const val PREFS_NAME = "selfselect_prefs"
        private const val API_KEY_NAME = "api_key"
        private const val TAG = "ChatViewModel"
    }

    init {
        if (isPreview) {
            _uiState.value = UiState.Success("Preview mode")
            _chatHistory.value = listOf(
                ChatMessage("This is a response from the Automator.", Sender.AUTOMATOR),
                ChatMessage("Hi, I'm a user.", Sender.USER)
            )
        } else {
            checkForApiKey(application.applicationContext)
        }
    }

    // **MISSING FUNCTION ADDED HERE**
    fun updateUiStateToError(message: String) {
        _uiState.value = UiState.Error(message)
        viewModelScope.launch {
            _chatHistory.value += ChatMessage(message, Sender.AUTOMATOR)
        }
    }

    private fun storeApiKey(context: Context, apiKey: String) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error storing API key: ${e.message}", e)
        }
    }

    private fun retrieveApiKey(context: Context): String? {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return sharedPreferences.getString(API_KEY_NAME, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving API key: ${e.message}", e)
            return null
        }
    }

    private fun initializeGenerativeModel(apiKey: String): Boolean {
        if (apiKey.isBlank()) {
            Log.w(TAG, "API Key is blank, initialization skipped.")
            return false
        }
        return try {
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
            conversation = generativeModel?.startChat()
            Log.i(TAG, "GenerativeModel initialized successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing GenerativeModel: ${e.message}", e)
            generativeModel = null
            conversation = null
            false
        }
    }

    fun checkForApiKey(context: Context) {
        val storedApiKey = retrieveApiKey(context)
        if (initializeGenerativeModel(storedApiKey ?: "")) {
            Log.d(TAG, "Successfully initialized GenerativeModel with stored API key.")
            _uiState.value = UiState.Loading
            sendInitialMessage()
            return
        }
        Log.i(TAG, "Stored API key not valid or not found. Prompting for new API key via dialog.")
        _uiState.value = UiState.ShowApiKeyDialog
    }

    fun sendInitialMessage() {
        if (conversation == null) {
            Log.w(TAG, "sendInitialMessage: Conversation is null, cannot send initial message.")
            _uiState.value = UiState.Error("AI model not ready. Please check API key.")
            return
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = conversation?.sendMessage("Hello")
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

    fun sendMessage(prompt: String) {
        if (conversation == null) {
            Log.w(TAG, "sendMessage: Conversation is null.")
            _uiState.value = UiState.Error("AI model not ready. Please check API key.")
            _chatHistory.value += ChatMessage("Error: AI Model not initialized. Cannot send message.", Sender.AUTOMATOR)
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
            viewModelScope.launch {
                kotlinx.coroutines.delay(100)
                _uiState.value = UiState.ShowApiKeyDialog
            }
            return
        }

        if (initializeGenerativeModel(apiKey)) {
            Log.i(TAG, "Submitted API Key is valid. Storing and initializing chat.")
            storeApiKey(context, apiKey)
            _uiState.value = UiState.Loading
            sendInitialMessage()
        } else {
            Log.w(TAG, "Submitted API Key is invalid.")
            _uiState.value = UiState.Error("Invalid API Key provided. Please check it and try again.")
            viewModelScope.launch {
                kotlinx.coroutines.delay(100)
                _uiState.value = UiState.ShowApiKeyDialog
            }
        }
    }

    private fun extractYamlAndPrompt(text: String) {
        val pattern: Pattern = Pattern.compile("```yaml\n(.*?)\n```", Pattern.DOTALL)
        val matcher: Matcher = pattern.matcher(text)
        if (matcher.find()) {
            val yamlText = matcher.group(1)?.trim()
            if (yamlText != null) {
                _uiState.value = UiState.ShowYamlDialog(yamlText)
            } else {
                _uiState.value = UiState.Success(text)
            }
        } else {
            _uiState.value = UiState.Success(text)
        }
    }

    fun onDialogResult(copy: Boolean) {
        viewModelScope.launch {
            if (copy) {
                _chatHistory.value += ChatMessage("YAML copied to clipboard for edits.", Sender.AUTOMATOR)
                _uiState.value = UiState.Success("YAML dialog dismissed & copied.")
            } else {
                _chatHistory.value += ChatMessage("YAML executing (and copied)", Sender.AUTOMATOR)
                _uiState.value = UiState.Success(" YAML executed.")
            }
        }
    }
}