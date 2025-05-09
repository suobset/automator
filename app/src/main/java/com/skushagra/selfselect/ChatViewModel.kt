package com.skushagra.selfselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.Chat // Import Chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun sendMessage(prompt: String) {
        _chatHistory.value += ChatMessage(prompt, Sender.USER)
        _uiState.value = UiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = conversation.sendMessage(prompt) // Call send on the Chat object
                val reply = response.text ?: "No response from Automator."
                _chatHistory.value += ChatMessage(reply.toString(), Sender.AUTOMATOR)
                _uiState.value = UiState.Success(reply.toString())
            } catch (e: Exception) {
                val error = e.localizedMessage ?: "Something went wrong."
                _chatHistory.value += ChatMessage(error, Sender.AUTOMATOR)
                _uiState.value = UiState.Error(error)
            }
        }
    }
}