package com.skushagra.selfselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class ChatViewModel : ViewModel() {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.apiKey
    )

    // Start a new chat with the GenerativeModel
    private val conversation: Chat = generativeModel.startChat()

    // Initialize _uiState here
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        sendInitialMessage()
    }

    fun sendInitialMessage() {
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = conversation.sendMessage(prompt1)
                val reply = response.text ?: "Automator is ready."
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
        _chatHistory.value += ChatMessage(prompt, Sender.USER)
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = conversation.sendMessage(prompt)
                val reply = response.text ?: "No response from Automator."
                _chatHistory.value += ChatMessage(reply, Sender.AUTOMATOR)
                extractYamlAndPrompt(reply)
            } catch (e: Exception) {
                val error = e.localizedMessage ?: "Something went wrong."
                _chatHistory.value += ChatMessage(error, Sender.AUTOMATOR)
                _uiState.value = UiState.Error(error)
            }
        }
    }

    private fun extractYamlAndPrompt(text: String) {
        val pattern: Pattern = Pattern.compile("```yaml\\n(.*?)\\n```", Pattern.DOTALL)
        val matcher: Matcher = pattern.matcher(text)
        if (matcher.find()) {
            val yamlText = matcher.group(1)?.trim()
            if (yamlText != null) {
                _uiState.value = UiState.ShowYamlDialog(yamlText)
            } else {
                _uiState.value = UiState.Error("No YAML block found")
            }
        } else {
            _uiState.value = UiState.Error("No YAML block found")
        }
    }

    fun onDialogResult(copy: Boolean) {
        viewModelScope.launch { // Use viewModelScope
            if (copy) {
                _chatHistory.value += ChatMessage("YAML copied to clipboard.", Sender.AUTOMATOR)
            }
            _uiState.value = UiState.Success("")
        }
    }
}
